package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SFace recognition: whose face the camera is looking at.
 *
 * <p>Both halves preprocess identically, which is the only way the comparison
 * means anything. An enrolled sample was written by
 * {@link FaceRegistrationService} as a 112x112 crop that {@code alignCrop} had
 * already rotated and scaled from YuNet's five landmarks; a live face is put
 * through {@code alignCrop} here, from the landmarks of this frame. The two
 * embeddings are then compared by cosine similarity.
 *
 * <p><b>The threshold is the one OpenCV publishes for SFace</b>, and it is not a
 * percentage of anything - 0.363 cosine similarity is where the model's own
 * evaluation puts the balance between naming the wrong student and naming
 * nobody. Raising it makes the register miss people who are present; lowering it
 * marks people present who are not. Neither is a small thing, so it is left
 * where the model's authors put it.
 *
 * <p><b>Two threads use this.</b> The enrolment thread calls {@link #train()},
 * the frame thread calls {@link #recognize}, and both go through the same native
 * network - which is not safe to call from two threads at once - so both are
 * synchronized on this service. A retrain after a capture therefore holds up
 * recognition for as long as it takes to re-read the samples. That is a visible
 * pause of perhaps a second on a large class, and it is the right trade: the
 * alternative is a second copy of a 37 MB network that OpenCV's Java bindings
 * give no way to free.
 */
public class FaceRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(FaceRecognitionService.class);

    /** The same folder {@link FaceRegistrationService} writes to. */
    private static final String BASE_DIR = "data/faces";

    /** OpenCV's published cosine threshold for SFace. See the class comment. */
    private static final double MATCH_THRESHOLD = 0.363;

    /** What SFace expects, and the size every enrolled sample is stored at. */
    private static final int SFACE_INPUT = 112;

    /**
     * The score reported when no comparison was possible at all - no enrolled
     * faces, or a face that could not be aligned. Distinct from a real low
     * score, which is a comparison that happened and failed.
     */
    private static final double NO_COMPARISON = -1.0;

    private final FaceRecognizerSF recognizer;

    /** Student id to that student's sample embeddings. Guarded by this. */
    private final Map<String, List<Mat>> studentEmbeddings = new HashMap<>();

    /**
     * @param sfaceModelPath a readable copy of the SFace ONNX file on disk
     */
    public FaceRecognitionService(String sfaceModelPath) {
        recognizer = FaceRecognizerSF.create(sfaceModelPath, "");
        if (recognizer == null) {
            throw new IllegalStateException(
                    "Unable to load the SFace model from " + sfaceModelPath + ".");
        }
    }

    /**
     * Reloads every enrolled sample from disk.
     *
     * <p>Blocking, and proportional to the number of samples: each one is read,
     * decoded and embedded. Call it from a background thread.
     *
     * <p>The new set is built first and swapped in at the end. Emptying the map
     * up front - as this used to - meant that a failure halfway through left the
     * recogniser knowing fewer people than it had a moment earlier, while
     * recognition carried on regardless and quietly called those students
     * strangers.
     */
    public synchronized void train() {
        File baseDir = new File(BASE_DIR);
        if (!baseDir.isDirectory() && !baseDir.mkdirs()) {
            log.error("The face sample folder {} does not exist and could not be created.",
                    baseDir.getAbsolutePath());
            return;
        }
        File[] studentDirs = baseDir.listFiles(File::isDirectory);
        Map<String, List<Mat>> loaded = new HashMap<>();
        int sampleCount = 0;

        try {
            if (studentDirs != null) {
                Arrays.sort(studentDirs, Comparator.comparing(File::getName));
                for (File studentDir : studentDirs) {
                    List<Mat> embeddings = loadStudent(studentDir);
                    if (!embeddings.isEmpty()) {
                        loaded.put(studentDir.getName(), embeddings);
                        sampleCount += embeddings.size();
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            // Nothing has been swapped in yet, so the old set is still live and
            // correct. Only the half-built new one needs freeing.
            releaseAll(loaded.values());
            throw failure;
        }

        List<List<Mat>> previous = new ArrayList<>(studentEmbeddings.values());
        studentEmbeddings.clear();
        studentEmbeddings.putAll(loaded);
        releaseAll(previous);

        if (loaded.isEmpty()) {
            log.info("No enrolled faces under {}. Nobody can be recognised until at "
                    + "least one student has been enrolled.", baseDir.getAbsolutePath());
        } else {
            log.info("Loaded {} face samples for {} students.", sampleCount, loaded.size());
        }
    }

    /**
     * Every usable sample in one student's folder, as embeddings.
     *
     * <p>A sample that will not read or will not embed is skipped with a line in
     * the log rather than failing the whole load. One corrupt PNG should not
     * cost a class its register.
     */
    private List<Mat> loadStudent(File studentDir) {
        List<Mat> embeddings = new ArrayList<>();
        File[] samples = studentDir.listFiles((dir, name) -> isImage(name));
        if (samples == null || samples.length == 0) {
            return embeddings;
        }
        Arrays.sort(samples, Comparator.comparing(File::getName));
        for (File sample : samples) {
            Mat image = Imgcodecs.imread(sample.getAbsolutePath(), Imgcodecs.IMREAD_COLOR);
            if (image == null || image.empty()) {
                log.warn("Skipped a face sample that would not read: {}",
                        sample.getAbsolutePath());
                if (image != null) {
                    image.release();
                }
                continue;
            }

            Mat embedding = null;
            try {
                embedding = embedSample(image, sample);
                if (embedding != null) {
                    embeddings.add(embedding);
                    // Ownership has passed to the list, so the finally below
                    // must not free it.
                    embedding = null;
                }
            } catch (RuntimeException failure) {
                log.warn("Skipped the face sample {}: {}",
                        sample.getAbsolutePath(), failure.getMessage());
            } finally {
                image.release();
                if (embedding != null) {
                    embedding.release();
                }
            }
        }
        return embeddings;
    }

    private static boolean isImage(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
    }

    /**
     * One stored sample as an embedding, or null if SFace produced nothing.
     *
     * <p>A sample the current enrolment screen wrote is already 112x112 and
     * already aligned, so it goes straight to the network. Anything else is a
     * leftover from before enrolment saved aligned crops: it is stretched to fit
     * so the student is not simply dropped, but a stretched face is measurably
     * worse to match against, so it is worth saying so once per sample per load.
     */
    private Mat embedSample(Mat image, File sample) {
        if (image.width() == SFACE_INPUT && image.height() == SFACE_INPUT) {
            return embed(image);
        }
        log.warn("The sample {} is {}x{} rather than {}x{}, so it predates aligned "
                        + "enrolment. It is being stretched to fit, which matches less "
                        + "reliably - re-enrol this student to replace it.",
                sample.getAbsolutePath(), image.width(), image.height(),
                SFACE_INPUT, SFACE_INPUT);

        Mat resized = new Mat();
        try {
            Imgproc.resize(image, resized, new Size(SFACE_INPUT, SFACE_INPUT));
            return embed(resized);
        } finally {
            resized.release();
        }
    }

    /**
     * Names one detected face.
     *
     * <p>Neither argument is retained. The frame is the caller's, and the
     * landmark copy taken from {@code detectedFace} is released here.
     *
     * @param frame        the whole camera frame, undrawn
     * @param detectedFace one detection from that same frame - its landmarks must
     *                     belong to these pixels, or the crop is aligned to where
     *                     the face used to be
     */
    public synchronized RecognitionResult recognize(Mat frame, DetectedFace detectedFace) {
        if (frame == null || frame.empty() || detectedFace == null) {
            return RecognitionResult.unknown(NO_COMPARISON);
        }
        if (studentEmbeddings.isEmpty()) {
            return RecognitionResult.unknown(NO_COMPARISON);
        }

        Mat landmarks = detectedFace.getLandmarks();
        Mat alignedFace = new Mat();
        Mat target = null;
        try {
            recognizer.alignCrop(frame, landmarks, alignedFace);
            if (alignedFace.empty()) {
                return RecognitionResult.unknown(NO_COMPARISON);
            }

            target = embed(alignedFace);
            if (target == null) {
                return RecognitionResult.unknown(NO_COMPARISON);
            }

            return bestMatch(target);

        } finally {
            landmarks.release();
            alignedFace.release();
            if (target != null) {
                target.release();
            }
        }
    }

    /**
     * The closest enrolled sample to this embedding.
     *
     * <p>Every sample of every student, not the first over the line. The best
     * score across the whole set is what decides, because a face that is a
     * near-match for two students should be reported as whichever it resembles
     * more - and because the runner-up's score is what makes a wrong answer
     * visible in the log.
     */
    private RecognitionResult bestMatch(Mat target) {
        String bestStudent = null;
        double bestScore = NO_COMPARISON;

        for (Map.Entry<String, List<Mat>> entry : studentEmbeddings.entrySet()) {
            for (Mat stored : entry.getValue()) {
                double score = recognizer.match(target, stored, FaceRecognizerSF.FR_COSINE);
                if (score > bestScore) {
                    bestScore = score;
                    bestStudent = entry.getKey();
                }
            }
        }

        if (bestStudent != null && bestScore >= MATCH_THRESHOLD) {
            return RecognitionResult.recognized(bestStudent, bestScore);
        }
        return RecognitionResult.unknown(bestScore);
    }

    /**
     * One embedding from an already aligned 112x112 face, or null if SFace
     * declined to produce one.
     *
     * <p><b>The caller owns the returned Mat.</b> An empty result is released
     * here and reported as null, so a caller cannot accidentally store an empty
     * embedding - {@code match} against one throws, and it would do so once per
     * face per pass for as long as the sample stayed loaded.
     */
    private Mat embed(Mat alignedFace) {
        Mat embedding = new Mat();
        recognizer.feature(alignedFace, embedding);
        if (embedding.empty()) {
            embedding.release();
            return null;
        }
        return embedding;
    }

    private static void releaseAll(Collection<List<Mat>> embeddingLists) {
        for (List<Mat> embeddings : embeddingLists) {
            if (embeddings == null) {
                continue;
            }
            for (Mat embedding : embeddings) {
                if (embedding != null) {
                    embedding.release();
                }
            }
        }
    }

    /**
     * Frees the loaded embeddings. The network itself stays.
     *
     * <p>It has to: {@code FaceRecognizerSF} exposes no {@code close} in the Java
     * bindings, only a protected finalizer, so there is nothing to call. Keeping
     * the one instance is therefore not an oversight but the only version of this
     * that does not leak - and it means the next {@link #train()} reloads the
     * samples from disk, which is also how a face enrolled in the meantime comes
     * to be recognised.
     *
     * <p>Safe to call twice.
     */
    public synchronized void close() {
        releaseAll(studentEmbeddings.values());
        studentEmbeddings.clear();
    }
}
