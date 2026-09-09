package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;

/**
 * One detected face: where it is, and the five landmarks inside it.
 *
 * <p>The landmark Mat is one YuNet detection row - x, y, width, height,
 * confidence, then the right eye, left eye, nose and both mouth corners as
 * x/y pairs. {@code FaceRecognizerSF.alignCrop} reads the row itself, which
 * is why it is carried whole instead of being unpacked into five points.
 *
 * <p><b>This class owns a native buffer, so it has an owner and a lifetime.</b>
 * It copies the row it is given, so the caller may release its own copy
 * immediately, and whoever holds the DetectedFace must call {@link #release()}
 * exactly once when finished. Every method here either copies or hands over
 * ownership explicitly - there is no accessor that returns a view into this
 * object's memory, because a view that outlives its parent is the one bug in
 * OpenCV's Java bindings that presents as a random crash minutes later
 * somewhere else entirely.
 */
public final class DetectedFace {

    private final Rect boundingBox;
    private final Mat landmarks;

    /**
     * @param boundingBox copied, so the caller may keep or mutate its own
     * @param landmarks   copied, so the caller may release its own at once
     */
    public DetectedFace(Rect boundingBox, Mat landmarks) {
        this.boundingBox = new Rect(
                boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
        this.landmarks = landmarks.clone();
    }

    /** A copy, so a caller cannot move this face by editing what it is given. */
    public Rect getBoundingBox() {
        return new Rect(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height);
    }

    /**
     * A copy of the detection row.
     *
     * <p><b>The caller owns the returned Mat and must release it.</b> Handing
     * out the field itself would let a caller release memory this object is
     * still using, or use memory this object has already released.
     */
    public Mat getLandmarks() {
        return landmarks.clone();
    }

    /**
     * An independent copy of this face.
     *
     * <p>For handing a detection to another thread. Doing it through
     * {@code new DetectedFace(f.getBoundingBox(), f.getLandmarks())} works
     * but copies the row twice and leaves the intermediate to be released by
     * hand - once per face per frame, at ten frames a second, which is a lot
     * of native allocation to do by accident.
     */
    public DetectedFace copy() {
        return new DetectedFace(boundingBox, landmarks);
    }

    /**
     * Frees the native row. Safe to call twice - OpenCV's {@code release} is
     * idempotent - but the face must not be used afterwards.
     */
    public void release() {
        landmarks.release();
    }
}
