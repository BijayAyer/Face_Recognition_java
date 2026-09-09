package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;

import java.util.ArrayList;
import java.util.List;

/**
 * YuNet face detector.
 *
 * YuNet provides:
 * - face bounding box
 * - confidence
 * - 5 facial landmarks
 *
 * Those landmarks are later used by SFace alignCrop().
 */
public class FaceDetector {

    private static final int INPUT_WIDTH = 320;
    private static final int INPUT_HEIGHT = 320;

    private static final float SCORE_THRESHOLD = 0.6f;
    private static final float NMS_THRESHOLD = 0.3f;

    private static final int TOP_K = 5000;

    private final FaceDetectorYN detector;

    public FaceDetector(
            String modelPath
    ) {

        detector =
                FaceDetectorYN.create(
                        modelPath,
                        "",
                        new Size(
                                INPUT_WIDTH,
                                INPUT_HEIGHT
                        ),
                        SCORE_THRESHOLD,
                        NMS_THRESHOLD,
                        TOP_K
                );

        if (detector == null) {

            throw new IllegalStateException(
                    "Unable to create YuNet face detector."
            );
        }
    }

    /**
     * Detect faces and return bounding boxes
     * together with facial landmarks.
     */
    public List<DetectedFace> detect(
            Mat frame
    ) {

        List<DetectedFace> faces =
                new ArrayList<>();

        if (
                frame == null
                        || frame.empty()
        ) {

            return faces;
        }

        /*
         * YuNet must know the actual image size.
         */
        detector.setInputSize(
                new Size(
                        frame.cols(),
                        frame.rows()
                )
        );

        Mat detectionOutput =
                new Mat();

        try {

            detector.detect(
                    frame,
                    detectionOutput
            );

            if (
                    detectionOutput.empty()
            ) {

                return faces;
            }

            /*
             * Each YuNet detection contains:
             *
             * 0  = x
             * 1  = y
             * 2  = width
             * 3  = height
             * 4  = confidence
             * 5  = right eye x
             * 6  = right eye y
             * 7  = left eye x
             * 8  = left eye y
             * 9  = nose x
             * 10 = nose y
             * 11 = right mouth x
             * 12 = right mouth y
             * 13 = left mouth x
             * 14 = left mouth y
             */

            for (
                    int row = 0;
                    row < detectionOutput.rows();
                    row++
            ) {

                double confidence =
                        detectionOutput.get(
                                row,
                                4
                        )[0];

                if (
                        confidence
                                < SCORE_THRESHOLD
                ) {

                    continue;
                }

                int x =
                        (int) Math.round(
                                detectionOutput
                                        .get(row, 0)[0]
                        );

                int y =
                        (int) Math.round(
                                detectionOutput
                                        .get(row, 1)[0]
                        );

                int width =
                        (int) Math.round(
                                detectionOutput
                                        .get(row, 2)[0]
                        );

                int height =
                        (int) Math.round(
                                detectionOutput
                                        .get(row, 3)[0]
                        );

                Rect rect =
                        clampRect(
                                new Rect(
                                        x,
                                        y,
                                        width,
                                        height
                                ),
                                frame.cols(),
                                frame.rows()
                        );

                if (
                        rect.width < 40
                                || rect.height < 40
                ) {

                    continue;
                }

                /*
                 * Keep the complete YuNet row.
                 *
                 * SFace alignCrop() needs
                 * the facial landmark coordinates.
                 */
                Mat landmarks =
                        detectionOutput.row(row);

                faces.add(
                        new DetectedFace(
                                rect,
                                landmarks
                        )
                );
            }

            return faces;

        } finally {

            detectionOutput.release();
        }
    }

    private Rect clampRect(
            Rect rect,
            int frameWidth,
            int frameHeight
    ) {

        int x =
                Math.max(
                        0,
                        rect.x
                );

        int y =
                Math.max(
                        0,
                        rect.y
                );

        int right =
                Math.min(
                        frameWidth,
                        rect.x + rect.width
                );

        int bottom =
                Math.min(
                        frameHeight,
                        rect.y + rect.height
                );

        if (
                right <= x
                        || bottom <= y
        ) {

            return new Rect();
        }

        return new Rect(
                x,
                y,
                right - x,
                bottom - y
        );
    }
}