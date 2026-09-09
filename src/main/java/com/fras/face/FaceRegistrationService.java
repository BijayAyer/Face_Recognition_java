package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceRecognizerSF;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Face registration service.
 *
 * New samples are:
 *
 * Camera frame
 *      ↓
 * YuNet landmarks
 *      ↓
 * SFace alignCrop()
 *      ↓
 * Aligned face
 *      ↓
 * PNG
 */
public class FaceRegistrationService {

    private static final String BASE_DIR =
            "data/faces";

    private static final Pattern STUDENT_ID_PATTERN =
            Pattern.compile(
                    "[A-Za-z0-9_-]{1,50}"
            );

    private final FaceRecognizerSF recognizer;

    public FaceRegistrationService(
            String sfaceModelPath
    ) {

        recognizer =
                FaceRecognizerSF.create(
                        sfaceModelPath,
                        ""
                );

        if (recognizer == null) {

            throw new IllegalStateException(
                    "Unable to load SFace model."
            );
        }
    }

    /**
     * Capture exactly one face.
     */
    public boolean captureSample(
            Mat frame,
            List<DetectedFace> faces,
            String studentId
    ) {

        if (
                frame == null
                        || frame.empty()
        ) {

            System.out.println(
                    "No camera frame available."
            );

            return false;
        }

        /*
         * Exactly one face must be visible.
         */
        if (
                faces == null
                        || faces.size() != 1
        ) {

            System.out.println(
                    "Registration requires exactly one face."
            );

            return false;
        }

        if (
                studentId == null
                        || !STUDENT_ID_PATTERN
                        .matcher(
                                studentId.trim()
                        )
                        .matches()
        ) {

            System.out.println(
                    "Invalid Student ID."
            );

            return false;
        }

        DetectedFace detectedFace =
                faces.get(0);

        Rect rect =
                detectedFace.getBoundingBox();

        /*
         * Reject tiny faces.
         */
        if (
                rect.width < 80
                        || rect.height < 80
        ) {

            System.out.println(
                    "Face is too small. Move closer."
            );

            return false;
        }

        Mat landmarks =
                detectedFace.getLandmarks();

        Mat alignedFace =
                new Mat();

        try {

            /*
             * Proper SFace alignment.
             */
            recognizer.alignCrop(
                    frame,
                    landmarks,
                    alignedFace
            );

            if (
                    alignedFace.empty()
            ) {

                System.out.println(
                        "Unable to align face."
                );

                return false;
            }

            File studentDirectory =
                    new File(
                            BASE_DIR,
                            studentId.trim()
                    );

            if (
                    !studentDirectory.exists()
                            && !studentDirectory.mkdirs()
            ) {

                System.out.println(
                        "Unable to create student directory."
                );

                return false;
            }

            File output =
                    nextSampleFile(
                            studentDirectory
                    );

            /*
             * Save the aligned face.
             *
             * This means registration images
             * are already normalized for SFace.
             */
            boolean saved =
                    Imgcodecs.imwrite(
                            output.getAbsolutePath(),
                            alignedFace
                    );

            if (saved) {

                System.out.println(
                        "Aligned face sample saved: "
                                + output.getAbsolutePath()
                );

            } else {

                System.out.println(
                        "Failed to save face sample."
                );
            }

            return saved;

        } finally {

            landmarks.release();

            alignedFace.release();
        }
    }

    private File nextSampleFile(
            File directory
    ) {

        int number = 1;

        File file;

        do {

            file =
                    new File(
                            directory,
                            number + ".png"
                    );

            number++;

        } while (
                file.exists()
        );

        return file;
    }
}