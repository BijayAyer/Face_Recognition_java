package com.fras.config;

import com.fras.face.CaptureResult;
import com.fras.face.DetectedFace;
import com.fras.face.FaceDetector;
import com.fras.face.FaceRecognitionService;
import com.fras.face.FaceRegistrationService;
import com.fras.face.RecognitionResult;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import nu.pattern.OpenCV;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfByte;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * The camera, the detector and the recogniser, driven by one background loop.
 *
 * <p><b>Three threads touch this class.</b> The JavaFX thread calls
 * {@link #start} and {@link #stop}. A single scheduled thread reads frames,
 * detects, recognises and draws. A second single thread does enrolment work,
 * which is disk I/O and a full retrain, so it cannot be allowed to sit in
 * front of the frame loop. Anything shared is either volatile, guarded by
 * {@link #frameLock}, or owned outright by the frame thread and touched by
 * others only after it has been confirmed dead.
 *
 * <p><b>Opening a screen no longer freezes the window.</b> {@code start} used
 * to extract two ONNX models to disk, construct both native networks and run
 * a complete retrain - every enrolled sample re-read and re-embedded - on the
 * JavaFX thread, before it so much as looked for a camera. On a class with
 * fifty enrolled students that is several seconds of a frozen, unpainted
 * window every single time the live screen is opened. All of it now happens on
 * the work thread, and the screen is told what is going on through
 * {@link Status} instead of being left blank.
 *
 * <p><b>The native models are built once and kept.</b> They used to be
 * rebuilt by every {@code start} and never released, so each visit to the live
 * or enrolment screen loaded another copy of a 37 MB recogniser and left the
 * previous one to a finalizer that may never run. OpenCV's Java bindings
 * expose no {@code close} on {@code FaceDetectorYN} or {@code FaceRecognizerSF},
 * so the only way to not leak them is to stop making duplicates: see
 * {@link #pipeline}.
 *
 * <p><b>Failure is reported rather than printed.</b> No camera, a camera
 * unplugged mid-session, a model that will not load - each of these used to
 * write a line to {@code System.err} that nobody sees and leave the viewfinder
 * black for ever. They are now logged and handed to the screen as a sentence.
 */
public class CameraService {

    private static final Logger log = LoggerFactory.getLogger(CameraService.class);

    static {
        OpenCV.loadLocally();
    }

    private static final String YUNET_RESOURCE = "/models/face_detection_yunet_2023mar.onnx";
    private static final String SFACE_RESOURCE = "/models/face_recognition_sface_2021dec.onnx";

    private static final int PREFERRED_CAMERA_INDEX = 0;
    private static final int FALLBACK_CAMERA_INDEX = 1;

    /** Roughly 30 frames a second, which is as fast as the webcam supplies them. */
    private static final long FRAME_INTERVAL_MS = 33;

    /**
     * Detection every third frame and recognition every ninth. Detection is
     * cheap enough to look continuous at 10 Hz, and recognition - one aligned
     * crop and one embedding per face, compared against every stored sample -
     * is not. Both intervals are about spending the frame budget where it
     * shows.
     */
    private static final long DETECTION_INTERVAL_MS = 100;
    private static final long RECOGNITION_INTERVAL_MS = 300;

    /**
     * How many reads in a row may fail before the device is reopened. A webcam
     * that has been unplugged does not throw: {@code read} simply returns
     * false, for ever. Counting only thrown errors - which is what this used
     * to do - meant the single most common failure never triggered a restart
     * and the screen just froze on the last good frame.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    /**
     * The overlay has two inks and no colours: see {@link #drawResults} for how
     * three states are told apart without any.
     *
     * <p>BGR, not RGB - OpenCV's channel order. Both of these are neutral, so
     * the distinction does not matter here, which is precisely the point: the
     * previous yellow/green/red trio was three BGR triples that had to be read
     * backwards to make sense of.
     */
    private static final Scalar OVERLAY_INK = new Scalar(0, 0, 0);
    private static final Scalar OVERLAY_LIGHT = new Scalar(255, 255, 255);

    /** Font of every overlay label, hoisted so the plate and the text agree. */
    private static final int OVERLAY_FONT = Imgproc.FONT_HERSHEY_SIMPLEX;
    private static final double OVERLAY_SCALE = 0.6;
    private static final int OVERLAY_WEIGHT = 1;

    /**
     * A negative stroke weight means "fill" to every OpenCV drawing call. Named
     * here rather than written as a bare -1 at the call site, and spelled out
     * rather than taken from {@code Imgproc.FILLED}, whose presence in the
     * generated Java bindings has moved between releases.
     */
    private static final int FILLED = -1;

    /**
     * Guards the handover of the capture device and the two executors between
     * {@link #start}'s background initialisation and {@link #stop}. Without it
     * an initialisation still in flight can publish a freshly opened camera
     * just after stop has already looked for one to release, and that device
     * is then held until the process ends.
     */
    private final Object lifecycleLock = new Object();

    /**
     * Bumped by every start and every stop. An initialisation that finds the
     * number changed underneath it belongs to a screen that has since been
     * left, so it releases what it opened instead of publishing it. This is
     * what makes leaving the live screen while the models are still loading
     * safe.
     */
    private final AtomicInteger generation = new AtomicInteger();

    /** True while a scheduled camera pass is inside native OpenCV work. */
    private final AtomicBoolean framePassActive = new AtomicBoolean();

    /**
     * The three native services, built once on first use and then kept for the
     * life of the process. Not rebuilt per start, and never released: OpenCV's
     * Java wrappers for these have no {@code close}, only a protected
     * finalizer, so a second copy is a leak with no remedy. One copy held
     * deliberately is the honest version of the same memory.
     */
    private volatile Pipeline pipeline;

    /*
     * Written on the JavaFX thread (start/stop), read on the camera thread
     * (processFrame) and rewritten there by restartCamera(). Volatile so the
     * camera thread cannot go on reading through a stale, already-released
     * handle after stop() has nulled it - dereferencing a released
     * VideoCapture is a native use-after-free, which takes the whole JVM down
     * rather than throwing something catchable.
     */
    private volatile VideoCapture capture;

    private volatile ScheduledExecutorService executor;

    /**
     * Enrolment and retraining. Separate from the frame loop so that saving a
     * sample - which writes a file and then re-embeds every sample on disk -
     * neither waits for a frame nor makes the next one late.
     */
    private volatile ExecutorService workExecutor;

    /*
     * Set on the JavaFX thread by start(), cleared there by stop(), read on the
     * camera thread. Cleared rather than left set so a page that has been
     * navigated away from can actually be collected instead of being pinned by
     * this long-lived service.
     */
    private volatile ImageView imageView;

    private final Object frameLock = new Object();

    /**
     * The most recent frame with nothing drawn on it, kept so that pressing
     * Capture enrols the moment the button was pressed.
     *
     * <p>It is taken before the overlay is drawn. Taken after - as it was -
     * every enrolled template had a coloured rectangle and a label baked into
     * it, and since a template is what every later match is measured against,
     * that is a permanent handicap on recognition rather than a cosmetic
     * blemish.
     */
    private Mat currentFrame;

    /** A copy of the detections belonging to {@link #currentFrame}. */
    private List<DetectedFace> currentFaces = Collections.emptyList();

    /** The frame thread's own detections, drawn and recognised from. */
    private List<DetectedFace> detectedFaces = Collections.emptyList();

    private final Map<Integer, RecognitionResult> recognitionResults = new HashMap<>();

    private long lastDetectionTime;
    private long lastRecognitionTime;
    private int consecutiveReadFailures;

    private volatile boolean running;

    /**
     * Optional callback invoked after every recognition pass with the results
     * that were actually recognised in that pass. Invoked on the camera
     * thread, NOT the JavaFX thread - a caller that touches the UI from inside
     * it must wrap that part in {@code Platform.runLater}. That is deliberate:
     * it lets the live screen make its blocking attendance call without
     * delaying the next frame, since it is already off the JavaFX thread here.
     */
    private volatile Consumer<List<RecognitionResult>> onRecognized;

    /** Where "starting", "running" and "it did not work" go. Always on the FX thread. */
    private volatile Consumer<Status> onStatus;

    /**
     * What the camera is doing, in a form a screen can put on a label.
     *
     * @param live    true once frames are arriving; false while starting, and
     *                after a failure or a stop
     * @param message one sentence, already fit to show a person
     */
    public record Status(boolean live, String message) {
    }

    public void setOnRecognized(Consumer<List<RecognitionResult>> callback) {
        this.onRecognized = callback;
    }

    // =========================================================
    // STARTING
    // =========================================================

    /**
     * Starts the camera and begins delivering frames to {@code imageView}.
     *
     * <p>Returns at once. The models and the device are opened on the work
     * thread, and {@code onStatus} is called on the JavaFX thread as that
     * proceeds - first that it is starting, then either that it is running or
     * why it is not.
     *
     * @param onStatus told what is happening, on the JavaFX thread. Not optional
     *                 in practice: a screen that passes null gets a viewfinder
     *                 that stays black with no explanation when there is no
     *                 camera, which is the behaviour this replaced.
     */
    public void start(ImageView imageView, Consumer<Status> onStatus) {
        if (framePassActive.get()) {
            this.onStatus = onStatus;
            report("The previous camera session is still shutting down. Please wait a moment and try again.", false);
            return;
        }
        if (running) {
            this.onStatus = onStatus;
            report("The camera is already running.", true);
            return;
        }

        // Every fresh start begins with no recognition callback attached.
        // Screens that need one must call setOnRecognized() themselves after
        // start(), so that a callback left behind by a previous screen - with
        // its own classroom and subject - cannot quietly go on marking here.
        this.onRecognized = null;
        this.onStatus = onStatus;
        this.imageView = imageView;
        this.consecutiveReadFailures = 0;

        int mine;
        synchronized (lifecycleLock) {
            mine = generation.incrementAndGet();
            executor = Executors.newSingleThreadScheduledExecutor(
                    named("fras-camera-thread"));
            workExecutor = Executors.newSingleThreadExecutor(named("fras-work-thread"));
        }

        report("Starting the camera...", false);
        workExecutor.submit(() -> beginCapture(mine));
    }

    /**
     * The slow half of starting, on the work thread: load the models, load the
     * enrolled faces, open the device, then schedule the frame loop.
     *
     * <p>Each step checks whether it is still wanted. Loading fifty students'
     * templates takes long enough that a teacher can open the live screen and
     * change their mind before it finishes, and an initialisation that
     * published a camera after that would leave a device open with nothing
     * reading it.
     */
    private void beginCapture(int mine) {
        try {
            Pipeline ready = ensurePipeline();
            if (stale(mine)) {
                return;
            }

            // Every enrolled sample is re-read and re-embedded here. This is
            // the several seconds that used to happen on the JavaFX thread.
            ready.recognition().train();
            if (stale(mine)) {
                return;
            }

            VideoCapture device = openCamera();
            if (device == null) {
                report("No camera could be opened. Check that one is connected "
                        + "and that nothing else is using it.", false);
                return;
            }

            ScheduledExecutorService frames;
            synchronized (lifecycleLock) {
                frames = executor;
                if (generation.get() != mine || frames == null) {
                    // Stopped while the device was opening. Release it here:
                    // stop() has already looked for one and found none.
                    device.release();
                    return;
                }
                capture = device;
                running = true;
            }

            frames.scheduleAtFixedRate(this::processFrame, 0,
                    FRAME_INTERVAL_MS, TimeUnit.MILLISECONDS);
            log.info("Camera started.");
            report("The camera is running.", true);

        } catch (Throwable failure) {
            // Includes the errors that a missing or unreadable ONNX model
            // throws. Without this the work thread would die in silence and
            // the viewfinder would stay black with nothing said.
            log.error("The camera could not be started.", failure);
            report(startupProblem(failure), false);
        }
    }

    /** True once this start has been superseded by another start or a stop. */
    private boolean stale(int mine) {
        if (generation.get() == mine) {
            return false;
        }
        log.debug("Camera start {} was abandoned before it finished.", mine);
        return true;
    }

    /**
     * The native services, built at most once.
     *
     * <p>Synchronized on the class rather than on {@code lifecycleLock}: this
     * can take seconds, and it must not hold the lock that {@link #stop} needs
     * to hand the device back. Two starts in quick succession therefore queue
     * here, and the second finds the pipeline already built.
     *
     * <p>The SFace model file is extracted once and its path given to both
     * services. Each of them used to resolve the resource itself, which copied
     * 37 MB out of the jar into a temporary file twice per start.
     */
    private synchronized Pipeline ensurePipeline() {
        Pipeline existing = pipeline;
        if (existing != null) {
            return existing;
        }

        String yunetPath = OpenCVConfig.resolveResourceToFile(
                YUNET_RESOURCE, "yunet-", ".onnx");
        String sfacePath = OpenCVConfig.resolveResourceToFile(
                SFACE_RESOURCE, "sface-", ".onnx");

        Pipeline built = new Pipeline(
                new FaceDetector(yunetPath),
                new FaceRegistrationService(sfacePath),
                new FaceRecognitionService(sfacePath));

        pipeline = built;
        log.info("Face detection and recognition models loaded.");
        return built;
    }

    /**
     * The three native services, together because they share a lifetime.
     *
     * <p>A record so that the frame thread can take all three in one volatile
     * read and know they belong to each other, rather than reading three fields
     * that a concurrent start could be halfway through replacing.
     */
    private record Pipeline(FaceDetector detector,
                            FaceRegistrationService registration,
                            FaceRecognitionService recognition) {
    }

    private static ThreadFactory named(String name) {
        return runnable -> {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Turns a startup failure into a sentence for the screen. */
    private static String startupProblem(Throwable failure) {
        String detail = failure.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = failure.getClass().getSimpleName();
        }
        return "The camera could not be started: " + detail;
    }

    /**
     * Hands a status to the screen on the JavaFX thread.
     *
     * <p>Snapshotted before the hop, because stop() clears the field to let a
     * page that has gone away be collected, and reading it inside the lambda
     * would read it after that.
     */
    private void report(String message, boolean live) {
        Consumer<Status> listener = onStatus;
        if (listener == null) {
            return;
        }
        Status status = new Status(live, message);
        if (Platform.isFxApplicationThread()) {
            listener.accept(status);
        } else {
            Platform.runLater(() -> listener.accept(status));
        }
    }

    private VideoCapture openCamera() {
        VideoCapture camera = new VideoCapture(PREFERRED_CAMERA_INDEX);
        if (camera.isOpened()) {
            log.info("Camera opened on index {}.", PREFERRED_CAMERA_INDEX);
            return camera;
        }
        camera.release();

        if (FALLBACK_CAMERA_INDEX != PREFERRED_CAMERA_INDEX) {
            camera = new VideoCapture(FALLBACK_CAMERA_INDEX);
            if (camera.isOpened()) {
                log.info("Camera opened on index {}.", FALLBACK_CAMERA_INDEX);
                return camera;
            }
            camera.release();
        }

        log.warn("No camera on index {} or {}.", PREFERRED_CAMERA_INDEX, FALLBACK_CAMERA_INDEX);
        return null;
    }

    /**
     * Reopens the device after a run of failed reads, on the camera thread.
     *
     * <p>The usual cause is a webcam that has been unplugged and put back, or a
     * driver that has lost the handle. Reopening recovers from both; the
     * alternative is a screen frozen on its last good frame with no
     * explanation.
     */
    private void restartCamera() {
        log.warn("Reopening the camera after {} failed reads.", consecutiveReadFailures);
        report("Lost the camera. Trying to reopen it...", false);
        try {
            VideoCapture old = capture;
            capture = null;
            if (old != null) {
                old.release();
            }

            // Give the OS a moment to let go of the device before asking for
            // it again; reopening immediately usually just fails again.
            Thread.sleep(500);

            if (!running) {
                // Stopped while we were waiting. Opening a camera now would
                // leave one open with nothing to read it.
                return;
            }

            VideoCapture reopened = openCamera();
            if (reopened == null) {
                report("The camera could not be reopened. Reconnect it and open "
                        + "this screen again.", false);
                return;
            }

            synchronized (lifecycleLock) {
                if (!running) {
                    reopened.release();
                    return;
                }
                capture = reopened;
            }
            log.info("Camera reopened.");
            report("The camera is running.", true);

        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            log.error("The camera could not be reopened.", failure);
            report("The camera could not be reopened.", false);
        }
    }

    // =========================================================
    // THE FRAME LOOP
    // =========================================================

    /**
     * One pass: read, unmirror, detect, recognise, draw, publish.
     *
     * <p>Everything is caught, including Errors. A scheduled task that throws
     * is cancelled and never runs again, so an exception escaping here would
     * not produce an error message - it would produce a camera that has
     * quietly stopped while the window still says it is live.
     */
    private void processFrame() {
        // Never allow a second scheduled invocation to overlap the native pass.
        // This is normally guaranteed by the single-thread executor, but the
        // flag also gives stop/start a precise indication that it is safe to
        // release native state and prevents a new session from starting while a
        // previous VideoCapture.read() is still unwinding.
        if (!framePassActive.compareAndSet(false, true)) {
            return;
        }
        try {
            processFrameInternal();
        } finally {
            framePassActive.set(false);
        }
    }

    private void processFrameInternal() {
        // One read of each volatile field, used for the whole pass. Re-reading
        // them would let the guard pass and the use below still find null,
        // which is the shape the old code had.
        //
        // stop() waits for this method to return before releasing the device,
        // so a handle that is non-null here stays valid until the pass ends.
        VideoCapture device = capture;
        Pipeline services = pipeline;
        if (!running || device == null || services == null) {
            return;
        }

        Mat frame = new Mat();
        try {
            if (!readFrame(device, frame)) {
                return;
            }

            // The webcam image is mirrored. Unmirrored, it is what somebody
            // expects to see when they look at a picture of themselves.
            Core.flip(frame, frame, 1);

            long now = System.currentTimeMillis();
            if (now - lastDetectionTime >= DETECTION_INTERVAL_MS) {
                detect(services, frame);
                lastDetectionTime = now;
            }
            if (now - lastRecognitionTime >= RECOGNITION_INTERVAL_MS) {
                recognise(services, frame);
                lastRecognitionTime = now;
            }

            drawResults(frame, detectedFaces);
            publish(frame);

        } catch (Throwable failure) {
            log.error("A camera pass failed; the loop continues.", failure);
        } finally {
            frame.release();
        }
    }

    /**
     * One frame, or false if there was not one to be had.
     *
     * <p>A false return counts as a failure exactly as a thrown one does. It
     * used not to, and that was the omission that mattered: an unplugged webcam
     * does not throw, {@code read} simply returns false for ever. So the
     * counter never moved, the restart never fired, and the screen sat frozen
     * on its last good frame with the lamp still lit.
     */
    private boolean readFrame(VideoCapture device, Mat frame) {
        boolean read;
        try {
            read = device.read(frame);
        } catch (RuntimeException nativeFailure) {
            log.warn("Reading a frame failed: {}", nativeFailure.getMessage());
            read = false;
        }

        if (read && !frame.empty()) {
            consecutiveReadFailures = 0;
            return true;
        }

        consecutiveReadFailures++;
        if (consecutiveReadFailures > MAX_CONSECUTIVE_FAILURES) {
            consecutiveReadFailures = 0;
            restartCamera();
        }
        return false;
    }

    /**
     * Refreshes the detections, and with them the frame that enrolment uses.
     *
     * <p>The two are taken together so that landmarks always belong to the
     * pixels they will be applied to. Kept apart - a frame every pass, faces
     * every third - a capture could align this frame's face with landmarks
     * measured a tenth of a second ago, which for anybody moving is a
     * noticeably worse crop.
     */
    private void detect(Pipeline services, Mat frame) {
        List<DetectedFace> found = services.detector().detect(frame);
        List<DetectedFace> forEnrolment = copyDetectedFaces(found);

        List<DetectedFace> previousDrawn;
        List<DetectedFace> previousKept;

        synchronized (frameLock) {
            // Only the count matters. recognitionResults is keyed by position,
            // so position i can mean a different person only when the number of
            // faces changes; while the count holds, keeping each position's
            // last known result is what stops the box flickering between "no
            // result yet" and "recognised" two passes out of every three. The
            // previous version released and emptied detectedFaces before this
            // comparison, so it compared against an empty list, cleared on
            // every pass, and flickered exactly as it had before the fix.
            if (found.size() != detectedFaces.size()) {
                recognitionResults.clear();
            }

            previousDrawn = detectedFaces;
            previousKept = currentFaces;
            detectedFaces = found;
            currentFaces = forEnrolment;
            keepFrame(frame);
        }

        releaseDetectedFaces(previousDrawn);
        releaseDetectedFaces(previousKept);
    }

    /**
     * Keeps a clean copy of the frame for enrolment. Called with
     * {@link #frameLock} held, and before anything is drawn on the frame.
     *
     * <p>{@code copyTo} into the buffer already there, rather than
     * {@code clone} into a new one. Clone allocated and freed a two-megabyte
     * native buffer ten times a second for as long as the screen was open, all
     * so that a button press would have a frame ready.
     */
    private void keepFrame(Mat frame) {
        if (currentFrame == null) {
            currentFrame = frame.clone();
        } else {
            frame.copyTo(currentFrame);
        }
    }

    /**
     * Names every detected face, and tells whoever is listening.
     *
     * <p>Runs on the camera thread, and so does the callback. That is the point
     * of it: the live screen marks attendance from there, and an HTTP call
     * belongs anywhere except the JavaFX thread.
     */
    private void recognise(Pipeline services, Mat frame) {
        recognitionResults.clear();
        for (int i = 0; i < detectedFaces.size(); i++) {
            recognitionResults.put(i,
                    services.recognition().recognize(frame, detectedFaces.get(i)));
        }

        Consumer<List<RecognitionResult>> callback = onRecognized;
        if (callback == null) {
            return;
        }

        List<RecognitionResult> recognised = new ArrayList<>();
        for (RecognitionResult result : recognitionResults.values()) {
            if (result != null && result.isRecognized()) {
                recognised.add(result);
            }
        }
        if (!recognised.isEmpty()) {
            callback.accept(recognised);
        }
    }

    /**
     * Puts the drawn frame in the viewfinder.
     *
     * <p>The target is checked before the frame is encoded, not after: JPEG
     * encoding every frame for an ImageView that has been cleared is the
     * most expensive way to do nothing.
     */
    private void publish(Mat frame) {
        ImageView target = imageView;
        if (target == null) {
            return;
        }
        Image image = matToImage(frame);
        if (image == null) {
            // An encode that failed. Leaving the last good frame up is better
            // than blanking the viewfinder for one bad one.
            return;
        }
        Platform.runLater(() -> {
            if (running) {
                target.setImage(image);
            }
        });
    }

    /**
     * Draws a box and a label per face.
     *
     * <p>Three states, three shapes. A face that has been detected but not yet
     * recognised used to be drawn as a red "Unknown", identically to a
     * confirmed stranger - so for the two passes out of three before
     * recognition catches up, everybody in the room looked like an intruder.
     * "Still checking" and "checked, and not a match" are different facts and
     * are shown differently. The score is drawn either way, so the number
     * behind the verdict is always visible.
     *
     * <p><b>Why there are no colours left.</b> The three states were yellow,
     * green and red, which is unreadable to a red-green colour-blind teacher
     * and invisible on a washed-out projector - and a camera frame is the one
     * surface in the application whose background cannot be predicted, so a
     * mid-tone stroke of any hue can land on a mid-tone shirt and disappear.
     * Each state is now a shape:
     *
     * <ul>
     *   <li><b>Detecting</b> - four corner brackets, no full box. Nothing has
     *       been decided yet, and the brackets are the same "acquiring" motif
     *       as the ticks around the viewfinder itself.</li>
     *   <li><b>Recognised</b> - a plain closed box. The expected outcome, drawn
     *       as quietly as a box can be drawn.</li>
     *   <li><b>Unknown</b> - a closed box with a second box inset inside it, and
     *       a filled label plate. The only double-ruled thing on the frame, and
     *       the heaviest, because it is the one a person has to look at.</li>
     * </ul>
     *
     * <p>Every stroke is drawn twice, dark underneath and light on top, which is
     * what makes the overlay legible against a bright window or a dark doorway
     * without knowing which it is.
     */
    private void drawResults(Mat frame, List<DetectedFace> faces) {
        for (int i = 0; i < faces.size(); i++) {
            Rect box = faces.get(i).getBoundingBox();
            RecognitionResult result = recognitionResults.get(i);

            if (result == null) {
                corners(frame, box);
                label(frame, box, "Detecting...", false);
            } else if (result.isRecognized()) {
                outline(frame, box, 2);
                label(frame, box, result.getStudentId() + percent(result.getScore()), false);
            } else {
                outline(frame, box, 2);
                // Inset by 4px, so the two rules read as a double rule rather
                // than as one thick smudge.
                outline(frame, new Rect(box.x + 4, box.y + 4,
                        Math.max(1, box.width - 8), Math.max(1, box.height - 8)), 1);
                label(frame, box, "Unknown" + percent(Math.max(0, result.getScore())), true);
            }
        }
    }

    /**
     * A rectangle drawn dark-then-light, so it survives any background.
     *
     * <p>The dark pass is one pixel wider on each side rather than the same
     * width, or the light pass would cover it completely and the outline would
     * vanish on a light shirt - which is the whole failure this guards against.
     */
    private static void outline(Mat frame, Rect box, int weight) {
        Imgproc.rectangle(frame, box, OVERLAY_INK, weight + 2);
        Imgproc.rectangle(frame, box, OVERLAY_LIGHT, weight);
    }

    /** Four corner brackets, a fifth of the shorter side each. */
    private static void corners(Mat frame, Rect box) {
        int arm = Math.max(6, Math.min(box.width, box.height) / 5);
        int right = box.x + box.width;
        int bottom = box.y + box.height;

        bracket(frame, box.x, box.y, arm, arm);
        bracket(frame, right, box.y, -arm, arm);
        bracket(frame, box.x, bottom, arm, -arm);
        bracket(frame, right, bottom, -arm, -arm);
    }

    /**
     * One corner: a horizontal arm and a vertical arm meeting at
     * {@code (x, y)}. Negative lengths point back towards the opposite corner,
     * which is how the same call serves all four.
     */
    private static void bracket(Mat frame, int x, int y, int across, int down) {
        Point corner = new Point(x, y);
        Point horizontal = new Point(x + across, y);
        Point vertical = new Point(x, y + down);

        Imgproc.line(frame, corner, horizontal, OVERLAY_INK, 4);
        Imgproc.line(frame, corner, vertical, OVERLAY_INK, 4);
        Imgproc.line(frame, corner, horizontal, OVERLAY_LIGHT, 2);
        Imgproc.line(frame, corner, vertical, OVERLAY_LIGHT, 2);
    }

    /**
     * The text above a box. {@code plate} fills a solid light rectangle behind
     * it and draws the text dark - reserved for the stranger, so the loudest
     * state is the one piece of inverted type on the frame.
     *
     * <p>Sits above the box unless the face is at the very top of the frame, in
     * which case the text would be drawn off-screen.
     */
    private static void label(Mat frame, Rect box, String text, boolean plate) {
        int[] baseline = new int[1];
        Size size = Imgproc.getTextSize(text, OVERLAY_FONT, OVERLAY_SCALE, OVERLAY_WEIGHT, baseline);
        int baselineY = Math.max((int) size.height + 8, box.y - 8);
        Point origin = new Point(box.x, baselineY);

        if (plate) {
            Imgproc.rectangle(frame,
                    new Point(box.x - 3, baselineY - size.height - 5),
                    new Point(box.x + size.width + 5, baselineY + baseline[0] + 3),
                    OVERLAY_LIGHT, FILLED);
            Imgproc.putText(frame, text, origin,
                    OVERLAY_FONT, OVERLAY_SCALE, OVERLAY_INK, OVERLAY_WEIGHT);
            return;
        }

        // No plate: the same dark-under-light trick as the outlines, so the
        // text stays readable without covering the face it names.
        Imgproc.putText(frame, text, origin,
                OVERLAY_FONT, OVERLAY_SCALE, OVERLAY_INK, OVERLAY_WEIGHT + 2);
        Imgproc.putText(frame, text, origin,
                OVERLAY_FONT, OVERLAY_SCALE, OVERLAY_LIGHT, OVERLAY_WEIGHT);
    }

    private static String percent(double score) {
        return String.format(" (%.0f%%)", score * 100);
    }

    // =========================================================
    // ENROLMENT
    // =========================================================

    /**
     * Captures one sample on the work thread and answers on the JavaFX thread.
     *
     * <p>The answer carries a reason now. It used to be a bare boolean while
     * the reason - no face, two faces, too far away, the file could not be
     * written - went to standard output, so the screen could only say "nothing
     * was saved" and the person in front of the camera had to guess which of
     * five things to change.
     */
    public void captureSampleAsync(String studentId, Consumer<CaptureResult> onResult) {
        ExecutorService work = workExecutor;
        if (work == null) {
            onResult.accept(CaptureResult.refused("The camera is not running."));
            return;
        }
        try {
            work.submit(() -> {
                CaptureResult result = captureSample(studentId);
                Platform.runLater(() -> onResult.accept(result));
            });
        } catch (RejectedExecutionException stopping) {
            onResult.accept(CaptureResult.refused("The camera has just been stopped."));
        }
    }

    /**
     * Captures one aligned sample from the most recent clean frame.
     *
     * <p>Blocking: native calls, a file written, and a full retrain when it
     * succeeds. Runs on the work thread, from {@link #captureSampleAsync}.
     */
    private CaptureResult captureSample(String studentId) {
        Pipeline services = pipeline;
        if (services == null) {
            return CaptureResult.refused("The camera is not running.");
        }

        Mat frameCopy;
        List<DetectedFace> facesCopy;
        synchronized (frameLock) {
            if (currentFrame == null || currentFrame.empty()) {
                return CaptureResult.refused(
                        "No camera frame yet. Give the camera a moment and try again.");
            }
            frameCopy = currentFrame.clone();
            facesCopy = copyDetectedFaces(currentFaces);
        }

        try {
            CaptureResult result =
                    services.registration().captureSample(frameCopy, facesCopy, studentId);
            if (result.saved()) {
                // Reloaded at once, so the new sample counts on the very next
                // recognition pass rather than after the next restart.
                services.recognition().train();
            }
            return result;
        } finally {
            frameCopy.release();
            releaseDetectedFaces(facesCopy);
        }
    }

    // =========================================================
    // NATIVE HOUSEKEEPING
    // =========================================================

    /**
     * Independent copies, for handing detections to another thread.
     *
     * <p>One clone each, through {@link DetectedFace#copy()}. Building them
     * from {@code getBoundingBox()} and {@code getLandmarks()} cloned twice and
     * left the intermediate to be released by hand - once per face, ten times a
     * second.
     */
    private List<DetectedFace> copyDetectedFaces(List<DetectedFace> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        List<DetectedFace> copies = new ArrayList<>(source.size());
        for (DetectedFace face : source) {
            copies.add(face.copy());
        }
        return copies;
    }

    private void releaseDetectedFaces() {
        releaseDetectedFaces(detectedFaces);
        detectedFaces = Collections.emptyList();
    }

    private void releaseDetectedFaces(List<DetectedFace> faces) {
        if (faces == null) {
            return;
        }
        for (DetectedFace face : faces) {
            if (face != null) {
                face.release();
            }
        }
    }

    /** A frame as something JavaFX can draw, or null if it would not encode. */
    private Image matToImage(Mat mat) {
        MatOfByte buffer = new MatOfByte();
        try {
            if (!Imgcodecs.imencode(".jpg", mat, buffer)) {
                return null;
            }
            byte[] bytes = new byte[(int) buffer.total()];
            buffer.get(0, 0, bytes);
            return new Image(new ByteArrayInputStream(bytes));
        } finally {
            buffer.release();
        }
    }

    // =========================================================
    // STOPPING
    // =========================================================

    /**
     * Stops the camera and gives back everything it holds.
     *
     * <p>The order here is the whole point. The previous version set
     * {@code running = false}, called {@code shutdownNow()}, and released the
     * device on the very next line - but {@code shutdownNow()} only interrupts,
     * and {@code VideoCapture.read} is a blocking native call that ignores
     * interrupts. So the camera thread could still be inside {@code read} on a
     * handle this thread had just freed: a use-after-free in native code, which
     * ends the JVM outright instead of throwing something catchable. It showed
     * up as an occasional hard crash on leaving the live screen or closing the
     * window, which is exactly the kind of thing that looks random.
     *
     * <p>So: stop scheduling, wait for the pass in flight to finish, and only
     * then release. If the wait times out the device is deliberately left
     * un-released - a leaked handle until the process ends is recoverable, and a
     * crash is not.
     *
     * <p>Waiting is also what makes the rest of this method safe.
     * {@code detectedFaces} and {@code recognitionResults} belong to the camera
     * thread, so they are given back only when the wait confirmed that thread
     * had finished; if it did not, they are left alone for the same reason the
     * device is. The frame buffers need no such condition, because they are
     * guarded by {@link #frameLock} and the camera thread takes it too.
     *
     * <p>The models are not released, because they cannot be: see
     * {@link #pipeline}. What is released is everything that is per-session -
     * the device, the threads, the frames, the detections and the loaded
     * templates.
     *
     * <p>Safe to call twice: every field it clears is checked first.
     */
    public void stop() {
        ScheduledExecutorService frames;
        ExecutorService work;
        VideoCapture device;

        synchronized (lifecycleLock) {
            // Bumped inside the lock, and in the same breath as taking the
            // device, so a start still loading models cannot publish a camera
            // into a service that has just been stopped.
            generation.incrementAndGet();
            running = false;

            frames = executor;
            executor = null;
            work = workExecutor;
            workExecutor = null;
            device = capture;
            capture = null;
        }

        if (frames != null) {
            // shutdown(), not shutdownNow(): the interrupt buys nothing against
            // a native read, and cancelling the pass in flight is what we are
            // trying to avoid.
            frames.shutdown();
        }
        if (work != null) {
            work.shutdownNow();
        }

        boolean cameraThreadFinished = awaitQuietly(frames);

        if (device != null) {
            if (cameraThreadFinished) {
                device.release();
            } else {
                log.warn("The camera thread did not stop in time; leaving the capture "
                        + "device open rather than releasing it from under it.");
            }
        }

        // Safe either way: the camera thread takes frameLock before it touches
        // currentFrame or currentFaces, so it cannot be inside them here.
        synchronized (frameLock) {
            if (currentFrame != null) {
                currentFrame.release();
                currentFrame = null;
            }
            releaseDetectedFaces(currentFaces);
            currentFaces = Collections.emptyList();
        }

        // These three are not guarded, because the camera thread is the only
        // thread that reads them - which is precisely why they can only be
        // freed once it is gone. On the timeout path drawResults may still be
        // walking detectedFaces, and releasing those Mats from here would free
        // native memory out from under a native call: the same use-after-free,
        // by a different route, that the ordering above exists to prevent.
        //
        // So on that path they are left alone. detectedFaces is not even
        // cleared: holding the reference means the next start's first detect
        // pass releases them as its "previous" list, by which time the old
        // thread is long gone. Nulling it would turn a delayed free into a
        // permanent leak.
        if (cameraThreadFinished) {
            releaseDetectedFaces();
            recognitionResults.clear();
            consecutiveReadFailures = 0;
        }

        onRecognized = null;
        imageView = null;

        // Unloads the embeddings, not the recogniser. The next start reloads
        // them from disk, which is also how a sample enrolled meanwhile gets
        // picked up.
        Pipeline services = pipeline;
        if (services != null) {
            services.recognition().close();
        }

        log.info("Camera stopped.");
        report("The camera is stopped.", false);
        onStatus = null;
    }

    /**
     * Waits a bounded time for the frame loop to finish, and reports whether it
     * did. Called on the JavaFX thread, so the bound matters: a pass is one
     * frame's work, and a hitch of up to two seconds while leaving a screen is
     * a fair price for not crashing.
     *
     * @return true if the executor terminated, or there was nothing to wait for
     */
    private boolean awaitQuietly(ExecutorService service) {
        if (service == null) {
            return true;
        }
        try {
            if (service.awaitTermination(2, TimeUnit.SECONDS)) {
                return true;
            }
            // Out of patience. Interrupt in case the thread is somewhere that
            // responds to it, and give it a short second chance.
            service.shutdownNow();
            return service.awaitTermination(1, TimeUnit.SECONDS);

        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
