package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * Represents one detected face.
 *
 * YuNet returns:
 *
 * x, y, width, height,
 * right eye,
 * left eye,
 * nose,
 * right mouth corner,
 * left mouth corner
 */
public final class DetectedFace {

    private final Rect boundingBox;
    private final Mat landmarks;

    public DetectedFace(
            Rect boundingBox,
            Mat landmarks
    ) {

        this.boundingBox =
                new Rect(
                        boundingBox.x,
                        boundingBox.y,
                        boundingBox.width,
                        boundingBox.height
                );

        this.landmarks =
                landmarks.clone();
    }

    public Rect getBoundingBox() {
        return new Rect(
                boundingBox.x,
                boundingBox.y,
                boundingBox.width,
                boundingBox.height
        );
    }

    /**
     * Returns a copy of the YuNet detection row.
     *
     * IMPORTANT:
     * Caller owns the returned Mat and must release it.
     */
    public Mat getLandmarks() {
        return landmarks.clone();
    }

    public void release() {
        landmarks.release();
    }
}