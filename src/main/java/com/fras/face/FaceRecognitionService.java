package com.fras.face;

import com.fras.config.OpenCVConfig;
import org.opencv.core.Mat;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceRecognizerSF;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SFace face-recognition service.
 *
 * Pipeline:
 *
 * Registered image
 *       ↓
 * Face alignment
 *       ↓
 * SFace embedding
 *
 * Live image
 *       ↓
 * Face alignment
 *       ↓
 * SFace embedding
 *
 * Both pipelines therefore use identical preprocessing.
 */
public class FaceRecognitionService {

    private static final String BASE_DIR =
            "data/faces";

    private static final double MATCH_THRESHOLD =
            0.363;

    private final FaceRecognizerSF recognizer;

    private final Map<
            String,
            List<Mat>
            > studentEmbeddings =
            new HashMap<>();

    public FaceRecognitionService() {

        String modelPath =
                OpenCVConfig.resolveResourceToFile(
                        "/models/face_recognition_sface_2021dec.onnx",
                        "sface-",
                        ".onnx"
                );

        recognizer =
                FaceRecognizerSF.create(
                        modelPath,
                        ""
                );

        if (recognizer == null) {

            throw new IllegalStateException(
                    "Unable to load SFace model."
            );
        }
    }

    /**
     * Load all registered samples.
     *
     * IMPORTANT:
     *
     * Existing samples from the old
     * non-aligned pipeline should be
     * recreated after this upgrade.
     */
    public synchronized void train() {

        releaseEmbeddings();

        studentEmbeddings.clear();

        File baseDir =
                new File(BASE_DIR);

        if (
                !baseDir.exists()
                        && !baseDir.mkdirs()
        ) {

            System.err.println(
                    "Unable to create face directory: "
                            + baseDir.getAbsolutePath()
            );

            return;
        }

        File[] studentDirs =
                baseDir.listFiles(
                        File::isDirectory
                );

        if (
                studentDirs == null
                        || studentDirs.length == 0
        ) {

            System.out.println(
                    "No face training data found."
            );

            return;
        }

        for (
                File studentDir
                : studentDirs
        ) {

            String studentId =
                    studentDir.getName();

            File[] samples =
                    studentDir.listFiles(
                            (dir, name) -> {

                                String lower =
                                        name.toLowerCase();

                                return lower.endsWith(
                                        ".png"
                                )
                                        || lower.endsWith(
                                        ".jpg"
                                )
                                        || lower.endsWith(
                                        ".jpeg"
                                );
                            }
                    );

            if (
                    samples == null
                            || samples.length == 0
            ) {

                continue;
            }

            Arrays.sort(
                    samples,
                    Comparator.comparing(
                            File::getName
                    )
            );

            List<Mat> embeddings =
                    new ArrayList<>();

            /*
             * Because the saved registration
             * images are already face crops,
             * we don't have their landmarks.
             *
             * Therefore these samples are
             * resized consistently here.
             *
             * For maximum alignment quality,
             * registration should capture
             * the aligned face itself.
             */
            for (
                    File sample
                    : samples
            ) {

                Mat image =
                        Imgcodecs.imread(
                                sample.getAbsolutePath(),
                                Imgcodecs.IMREAD_COLOR
                        );

                if (
                        image == null
                                || image.empty()
                ) {

                    if (image != null) {
                        image.release();
                    }

                    continue;
                }

                Mat embedding =
                        null;

                try {

                    embedding =
                            computeEmbeddingFromCrop(
                                    image
                            );

                    if (
                            embedding != null
                                    && !embedding.empty()
                    ) {

                        embeddings.add(
                                embedding
                        );

                        embedding = null;
                    }

                } catch (
                        RuntimeException e
                ) {

                    System.err.println(
                            "Failed to process face sample: "
                                    + sample.getName()
                    );

                } finally {

                    image.release();

                    if (
                            embedding != null
                    ) {

                        embedding.release();
                    }
                }
            }

            if (
                    !embeddings.isEmpty()
            ) {

                studentEmbeddings.put(
                        studentId,
                        embeddings
                );
            }
        }

        System.out.println(
                "Face data loaded: "
                        + studentEmbeddings.size()
                        + " students."
        );
    }

    /**
     * Recognize a detected face.
     *
     * @param frame complete camera frame
     * @param detectedFace YuNet detection
     */
    public synchronized RecognitionResult recognize(
            Mat frame,
            DetectedFace detectedFace
    ) {

        if (
                frame == null
                        || frame.empty()
                        || detectedFace == null
        ) {

            return RecognitionResult.unknown(
                    -1.0
            );
        }

        if (
                studentEmbeddings.isEmpty()
        ) {

            return RecognitionResult.unknown(
                    -1.0
            );
        }

        Mat landmarks =
                detectedFace.getLandmarks();

        Mat alignedFace =
                new Mat();

        Mat targetEmbedding =
                null;

        try {

            /*
             * THIS IS THE IMPORTANT FIX.
             *
             * SFace's alignCrop() uses
             * YuNet's five facial landmarks.
             */
            recognizer.alignCrop(
                    frame,
                    landmarks,
                    alignedFace
            );

            if (
                    alignedFace.empty()
            ) {

                return RecognitionResult.unknown(
                        -1.0
                );
            }

            targetEmbedding =
                    computeEmbedding(
                            alignedFace
                    );

            String bestStudent =
                    "UNKNOWN";

            double bestScore =
                    -1.0;

            for (
                    Map.Entry<
                            String,
                            List<Mat>
                            > entry
                    : studentEmbeddings.entrySet()
            ) {

                for (
                        Mat storedEmbedding
                        : entry.getValue()
                ) {

                    double score =
                            recognizer.match(
                                    targetEmbedding,
                                    storedEmbedding,
                                    FaceRecognizerSF.FR_COSINE
                            );

                    if (
                            score > bestScore
                    ) {

                        bestScore =
                                score;

                        bestStudent =
                                entry.getKey();
                    }
                }
            }

            if (
                    bestScore
                            >= MATCH_THRESHOLD
            ) {

                return RecognitionResult.recognized(
                        bestStudent,
                        bestScore
                );
            }

            return RecognitionResult.unknown(
                    bestScore
            );

        } finally {

            landmarks.release();

            alignedFace.release();

            if (
                    targetEmbedding != null
            ) {

                targetEmbedding.release();
            }
        }
    }

    /**
     * Create SFace embedding from an
     * already aligned face.
     */
    private Mat computeEmbedding(
            Mat alignedFace
    ) {

        Mat embedding =
                new Mat();

        recognizer.feature(
                alignedFace,
                embedding
        );

        return embedding;
    }

    /**
     * Used for existing registration
     * images that are already face crops.
     *
     * New registrations will save aligned
     * faces through FaceRegistrationService.
     */
    private Mat computeEmbeddingFromCrop(
            Mat faceCrop
    ) {

        Mat resized =
                new Mat();

        try {

            org.opencv.imgproc.Imgproc.resize(
                    faceCrop,
                    resized,
                    new org.opencv.core.Size(
                            112,
                            112
                    )
            );

            return computeEmbedding(
                    resized
            );

        } finally {

            resized.release();
        }
    }

    private void releaseEmbeddings() {

        for (
                List<Mat> embeddings
                : studentEmbeddings.values()
        ) {

            for (
                    Mat embedding
                    : embeddings
            ) {

                if (
                        embedding != null
                ) {

                    embedding.release();
                }
            }
        }
    }

    public synchronized void close() {

        releaseEmbeddings();

        studentEmbeddings.clear();
    }

    public double getMatchThreshold() {

        return MATCH_THRESHOLD;
    }
}