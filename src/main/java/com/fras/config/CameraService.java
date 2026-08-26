package com.fras.config;

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
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.videoio.VideoCapture;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Camera + face recognition controller.
 *
 * Heavy OpenCV processing runs on a
 * background thread.
 */
public class CameraService {

    static {
        OpenCV.loadLocally();
    }

    private static final int PREFERRED_CAMERA_INDEX = 0;

    private static final int FALLBACK_CAMERA_INDEX = 1;

    private static final long FRAME_INTERVAL_MS = 33;

    private static final long DETECTION_INTERVAL_MS = 100;

    private static final long RECOGNITION_INTERVAL_MS = 300;

    private final FaceRecognitionService recognitionService =
            new FaceRecognitionService();

    private FaceRegistrationService registrationService;

    private FaceDetector faceDetector;

    private VideoCapture capture;

    private ScheduledExecutorService executor;

    /*
     * FIX: dedicated background executor for
     * captureSample()/retrain() work, kept separate
     * from the camera frame-processing executor so
     * registration never gets blocked behind (or
     * blocks) frame capture, and never runs on the
     * JavaFX Application Thread.
     */
    private ExecutorService workExecutor;

    private ImageView imageView;

    private final Object frameLock =
            new Object();

    private Mat currentFrame;

    private List<DetectedFace> currentFaces =
            Collections.emptyList();

    private List<DetectedFace> detectedFaces =
            Collections.emptyList();

    private final Map<Integer, RecognitionResult>
            recognitionResults =
            new HashMap<>();

    private long lastDetectionTime;

    private long lastRecognitionTime;

    private volatile boolean running;

    public void start(
            ImageView imageView
    ) {

        if (running) {
            return;
        }

        this.imageView =
                imageView;

        /*
         * YuNet model.
         */
        String yunetModelPath =
                OpenCVConfig.resolveResourceToFile(
                        "/models/face_detection_yunet_2023mar.onnx",
                        "yunet-",
                        ".onnx"
                );

        /*
         * SFace model.
         */
        String sfaceModelPath =
                OpenCVConfig.resolveResourceToFile(
                        "/models/face_recognition_sface_2021dec.onnx",
                        "sface-",
                        ".onnx"
                );

        faceDetector =
                new FaceDetector(
                        yunetModelPath
                );

        registrationService =
                new FaceRegistrationService(
                        sfaceModelPath
                );

        /*
         * Load existing registered faces.
         */
        recognitionService.train();

        capture =
                openCamera();

        if (
                capture == null
                        || !capture.isOpened()
        ) {

            System.err.println(
                    "Unable to open camera."
            );

            return;
        }

        running = true;

        workExecutor =
                Executors.newSingleThreadExecutor(
                        runnable -> {

                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "fras-work-thread"
                                    );

                            thread.setDaemon(true);

                            return thread;
                        }
                );

        executor =
                Executors.newSingleThreadScheduledExecutor(
                        runnable -> {

                            Thread thread =
                                    new Thread(
                                            runnable,
                                            "fras-camera-thread"
                                    );

                            thread.setDaemon(true);

                            return thread;
                        }
                );

        executor.scheduleAtFixedRate(
                this::processFrame,
                0,
                FRAME_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

        System.out.println(
                "Camera started."
        );
    }

    private VideoCapture openCamera() {

        VideoCapture camera =
                new VideoCapture(
                        PREFERRED_CAMERA_INDEX
                );

        if (
                camera.isOpened()
        ) {

            System.out.println(
                    "Camera opened: "
                            + PREFERRED_CAMERA_INDEX
            );

            return camera;
        }

        camera.release();

        if (
                FALLBACK_CAMERA_INDEX
                        != PREFERRED_CAMERA_INDEX
        ) {

            camera =
                    new VideoCapture(
                            FALLBACK_CAMERA_INDEX
                    );

            if (
                    camera.isOpened()
            ) {

                System.out.println(
                        "Camera opened: "
                                + FALLBACK_CAMERA_INDEX
                );

                return camera;
            }

            camera.release();
        }

        return null;
    }

    private void processFrame() {

        if (
                !running
                        || capture == null
        ) {

            return;
        }

        Mat frame =
                new Mat();

        try {

            if (
                    !capture.read(frame)
                            || frame.empty()
            ) {

                return;
            }

            /*
             * Fix mirrored camera image.
             */
            Core.flip(
                    frame,
                    frame,
                    1
            );

            long now =
                    System.currentTimeMillis();

            /*
             * FACE DETECTION
             */
            if (
                    now - lastDetectionTime
                            >= DETECTION_INTERVAL_MS
            ) {

                releaseDetectedFaces();

                List<DetectedFace> newlyDetectedFaces =
                        faceDetector.detect(
                                frame
                        );

                /*
                 * FIX (flicker): the previous code cleared
                 * recognitionResults on EVERY detection pass
                 * (every 100ms), while recognition itself
                 * only runs every 300ms. That meant the map
                 * was empty for ~2 out of every 3 detection
                 * cycles, so drawResults() kept alternating
                 * between "no result -> red" and
                 * "cached result -> green" -> the box
                 * blinked red/green continuously even for a
                 * single still face.
                 *
                 * We only need to invalidate the
                 * index-based cache when the NUMBER of
                 * detected faces changes, since that's the
                 * only case where index i could now point
                 * at a different face than before. If the
                 * count is unchanged, keep showing the last
                 * known recognition result for each index
                 * until the next recognition pass updates
                 * it - this removes the flicker without
                 * reintroducing the stale-name-on-wrong-face
                 * risk the original clear() was guarding
                 * against.
                 */
                if (
                        newlyDetectedFaces.size()
                                != detectedFaces.size()
                ) {

                    recognitionResults.clear();
                }

                detectedFaces =
                        newlyDetectedFaces;

                /*
                 * FIX: release the PREVIOUS currentFaces
                 * list before overwriting the reference,
                 * otherwise its native Mat landmarks are
                 * never freed (native memory leak).
                 */
                synchronized (frameLock) {

                    releaseDetectedFaces(
                            currentFaces
                    );

                    currentFaces =
                            copyDetectedFaces(
                                    detectedFaces
                            );
                }

                lastDetectionTime =
                        now;
            }

            /*
             * FACE RECOGNITION
             */
            if (
                    now - lastRecognitionTime
                            >= RECOGNITION_INTERVAL_MS
            ) {

                recognitionResults.clear();

                for (
                        int i = 0;
                        i < detectedFaces.size();
                        i++
                ) {

                    RecognitionResult result =
                            recognitionService.recognize(
                                    frame,
                                    detectedFaces.get(i)
                            );

                    recognitionResults.put(
                            i,
                            result
                    );
                }

                lastRecognitionTime =
                        now;
            }

            drawResults(
                    frame,
                    detectedFaces
            );

            /*
             * Keep current frame for registration.
             */
            synchronized (frameLock) {

                if (
                        currentFrame != null
                ) {

                    currentFrame.release();
                }

                currentFrame =
                        frame.clone();
            }

            Image image =
                    matToImage(
                            frame
                    );

            if (
                    imageView != null
            ) {

                Platform.runLater(
                        () -> {

                            if (
                                    running
                            ) {

                                imageView.setImage(
                                        image
                                );
                            }
                        }
                );
            }

        } catch (
                Exception e
        ) {

            System.err.println(
                    "Camera processing error: "
                            + e.getMessage()
            );

        } finally {

            frame.release();
        }
    }

    private void drawResults(
            Mat frame,
            List<DetectedFace> faces
    ) {

        for (
                int i = 0;
                i < faces.size();
                i++
        ) {

            DetectedFace face =
                    faces.get(i);

            RecognitionResult result =
                    recognitionResults.get(i);

            Scalar color;

            String label;

            /*
             * FIX (accuracy status): previously a face with
             * no cached result yet (i.e. recognition simply
             * hasn't run for it) was drawn identically to a
             * confirmed stranger - both fell into the "else"
             * branch as a red "Unknown" box. That's
             * misleading: "still processing" and "checked,
             * and it's not a match" are different states and
             * should look different. We now show a distinct
             * neutral "Detecting..." state, and we always
             * surface the match score (not just on a
             * successful match) so the confidence behind
             * every status is visible.
             */
            if (
                    result == null
            ) {

                color =
                        new Scalar(
                                0,
                                255,
                                255
                        );

                label =
                        "Detecting...";

            } else if (
                    result.isRecognized()
            ) {

                color =
                        new Scalar(
                                0,
                                255,
                                0
                        );

                label =
                        result.getStudentId()
                                + String.format(
                                " (%.0f%%)",
                                result.getScore() * 100
                        );

            } else {

                color =
                        new Scalar(
                                0,
                                0,
                                255
                        );

                label =
                        "Unknown"
                                + String.format(
                                " (%.0f%%)",
                                Math.max(
                                        0,
                                        result.getScore()
                                ) * 100
                        );
            }

            Imgproc.rectangle(
                    frame,
                    face.getBoundingBox(),
                    color,
                    2
            );

            var rect =
                    face.getBoundingBox();

            Imgproc.putText(
                    frame,
                    label,
                    new org.opencv.core.Point(
                            rect.x,
                            Math.max(
                                    25,
                                    rect.y - 10
                            )
                    ),
                    Imgproc.FONT_HERSHEY_SIMPLEX,
                    0.65,
                    color,
                    2
            );
        }
    }

    /**
     * FIX: async version of captureSample().
     *
     * Runs the capture + retrain work (disk I/O and
     * native OpenCV calls) on a background thread and
     * delivers the result back on the JavaFX
     * Application Thread, so the UI never freezes.
     */
    public void captureSampleAsync(
            String studentId,
            Consumer<Boolean> onResult
    ) {

        if (
                workExecutor == null
        ) {

            onResult.accept(false);

            return;
        }

        workExecutor.submit(
                () -> {

                    boolean saved =
                            captureSample(
                                    studentId
                            );

                    Platform.runLater(
                            () -> onResult.accept(
                                    saved
                            )
                    );
                }
        );
    }

    /**
     * Capture an aligned face sample.
     *
     * NOTE: performs blocking disk I/O and native
     * OpenCV calls. Prefer captureSampleAsync() when
     * calling from the JavaFX Application Thread.
     */
    public boolean captureSample(
            String studentId
    ) {

        Mat frameCopy;

        List<DetectedFace> facesCopy;

        synchronized (frameLock) {

            if (
                    currentFrame == null
                            || currentFrame.empty()
            ) {

                System.out.println(
                        "No camera frame available."
                );

                return false;
            }

            frameCopy =
                    currentFrame.clone();

            facesCopy =
                    copyDetectedFaces(
                            currentFaces
                    );
        }

        try {

            if (
                    registrationService == null
            ) {

                return false;
            }

            boolean saved =
                    registrationService.captureSample(
                            frameCopy,
                            facesCopy,
                            studentId
                    );

            if (saved) {

                /*
                 * Reload embeddings immediately.
                 */
                recognitionService.train();
            }

            return saved;

        } finally {

            frameCopy.release();

            releaseDetectedFaces(
                    facesCopy
            );
        }
    }

    private List<DetectedFace> copyDetectedFaces(
            List<DetectedFace> source
    ) {

        if (
                source == null
                        || source.isEmpty()
        ) {

            return Collections.emptyList();
        }

        List<DetectedFace> result =
                new ArrayList<>();

        for (
                DetectedFace face
                : source
        ) {

            Mat landmarks =
                    face.getLandmarks();

            try {

                result.add(
                        new DetectedFace(
                                face.getBoundingBox(),
                                landmarks
                        )
                );

            } finally {

                landmarks.release();
            }
        }

        return result;
    }

    private void releaseDetectedFaces() {

        releaseDetectedFaces(
                detectedFaces
        );

        detectedFaces =
                Collections.emptyList();
    }

    private void releaseDetectedFaces(
            List<DetectedFace> faces
    ) {

        if (
                faces == null
        ) {

            return;
        }

        for (
                DetectedFace face
                : faces
        ) {

            if (
                    face != null
            ) {

                face.release();
            }
        }
    }

    private Image matToImage(
            Mat mat
    ) {

        MatOfByte buffer =
                new MatOfByte();

        try {

            if (
                    !Imgcodecs.imencode(
                            ".jpg",
                            mat,
                            buffer
                    )
            ) {

                return null;
            }

            byte[] bytes =
                    new byte[
                            (int) buffer.total()
                            ];

            buffer.get(
                    0,
                    0,
                    bytes
            );

            return new Image(
                    new ByteArrayInputStream(
                            bytes
                    )
            );

        } finally {

            buffer.release();
        }
    }

    public void retrain() {

        recognitionService.train();
    }

    public void stop() {

        running = false;

        if (
                executor != null
        ) {

            executor.shutdownNow();

            executor = null;
        }

        if (
                workExecutor != null
        ) {

            workExecutor.shutdownNow();

            workExecutor = null;
        }

        if (
                capture != null
        ) {

            capture.release();

            capture = null;
        }

        synchronized (frameLock) {

            if (
                    currentFrame != null
            ) {

                currentFrame.release();

                currentFrame = null;
            }

            releaseDetectedFaces(
                    currentFaces
            );

            currentFaces =
                    Collections.emptyList();
        }

        releaseDetectedFaces();

        recognitionResults.clear();

        recognitionService.close();

        System.out.println(
                "Camera stopped."
        );
    }
}