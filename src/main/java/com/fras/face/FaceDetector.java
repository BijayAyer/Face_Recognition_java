package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;

import java.util.ArrayList;
import java.util.List;

/**
 * YuNet face detection: where the faces are, and where the eyes, nose and
 * mouth corners are within each one.
 *
 * <p>The landmarks matter as much as the boxes. SFace does not embed a
 * rectangle cut out of the frame; it embeds a 112x112 image that
 * {@code alignCrop} has rotated and scaled so that the eyes land on fixed
 * pixels. Feeding it a plain crop instead costs real accuracy, so every
 * detection carries its whole YuNet row forward rather than just a box.
 *
 * <p><b>The leak this class used to have.</b> Each row was handed on as
 * {@code detectionOutput.row(i)}, which is a header sharing the parent's
 * pixels rather than a copy of them, and it was never released. A live
 * header keeps a reference on that buffer, so the
 * {@code detectionOutput.release()} in the finally block freed nothing at
 * all while any row survived - and every row survived. At ten detection
 * passes a second with two or three faces in shot that is thirty leaked
 * headers a second, each pinning an entire detection buffer, reclaimed
 * only if and when a finalizer happened to run. The rows are now released
 * as soon as {@link DetectedFace} has copied what it needs out of them.
 */
public class FaceDetector {
    /**
     * The network's own working resolution. The detector is told the real
     * frame size before every {@code detect}, so this does not decide what
     * the boxes are measured against - it only sets how much the image is
     * scaled on its way into YuNet.
     */
    private static final int INPUT_WIDTH = 320;
    private static final int INPUT_HEIGHT = 320;

    private static final float SCORE_THRESHOLD = 0.6f;
    private static final float NMS_THRESHOLD = 0.3f;
    private static final int TOP_K = 5000;

    /**
     * Faces smaller than this are dropped. Forty pixels of face carries too
     * little detail for SFace to place anybody reliably, and admitting one
     * does not produce a cautious answer - it produces a confident wrong
     * one, which is worse than no answer at all.
     */
    private static final int MIN_FACE_PIXELS = 40;

    /**
     * Column offsets within one YuNet detection row. Columns 5 to 14 are the
     * five landmark pairs - right eye, left eye, nose, right mouth corner,
     * left mouth corner - and are deliberately not named here: nothing in
     * this class reads them. {@code alignCrop} reads them straight out of
     * the row, which is exactly why the whole row is kept rather than five
     * points pulled out of it.
     */
    private static final int COL_X = 0;
    private static final int COL_Y = 1;
    private static final int COL_WIDTH = 2;
    private static final int COL_HEIGHT = 3;
    private static final int COL_CONFIDENCE = 4;

    private final FaceDetectorYN detector;

    public FaceDetector(String modelPath) {
        detector = FaceDetectorYN.create(
                modelPath, "",
                new Size(INPUT_WIDTH, INPUT_HEIGHT),
                SCORE_THRESHOLD, NMS_THRESHOLD, TOP_K);

        if (detector == null) {
            throw new IllegalStateException(
                    "Unable to create the YuNet face detector from " + modelPath + ".");
        }
    }

    /**
     * Every usable face in the frame, each carrying its own landmarks.
     *
     * <p>The faces returned own their data; the caller owns the faces and
     * must {@link DetectedFace#release()} them. Nothing in this class holds
     * a reference to them afterwards.
     */
    public List<DetectedFace> detect(Mat frame) {
        List<DetectedFace> faces = new ArrayList<>();
        if (frame == null || frame.empty()) {
            return faces;
        }

        // YuNet has to be told the actual image size, or the boxes come back
        // measured against the 320x320 input instead of the frame.
        detector.setInputSize(new Size(frame.cols(), frame.rows()));

        Mat detectionOutput = new Mat();
        try {
            detector.detect(frame, detectionOutput);
            if (detectionOutput.empty()) {
                return faces;
            }

            for (int row = 0; row < detectionOutput.rows(); row++) {
                DetectedFace face = readRow(detectionOutput, row, frame.cols(), frame.rows());
                if (face != null) {
                    faces.add(face);
                }
            }
            return faces;

        } catch (RuntimeException detectionFailed) {
            // Half a list is worse than none: the caller would release these
            // and carry on as though it had seen the room.
            releaseAll(faces);
            throw detectionFailed;
        } finally {
            detectionOutput.release();
        }
    }

    /**
     * One detection row as a face, or null if the row does not describe a
     * usable one.
     *
     * <p>The {@code row()} view is released here on every path. It is a
     * header over the parent's buffer rather than a copy of it, so keeping
     * one alive keeps the whole detection output alive with it, and
     * {@link DetectedFace} has taken its own copy before this returns.
     */
    private DetectedFace readRow(Mat detectionOutput, int row,
                                 int frameWidth, int frameHeight) {

        double confidence = detectionOutput.get(row, COL_CONFIDENCE)[0];
        if (confidence < SCORE_THRESHOLD) {
            return null;
        }

        Rect box = clampRect(
                new Rect(
                        (int) Math.round(detectionOutput.get(row, COL_X)[0]),
                        (int) Math.round(detectionOutput.get(row, COL_Y)[0]),
                        (int) Math.round(detectionOutput.get(row, COL_WIDTH)[0]),
                        (int) Math.round(detectionOutput.get(row, COL_HEIGHT)[0])),
                frameWidth, frameHeight);

        if (box.width < MIN_FACE_PIXELS || box.height < MIN_FACE_PIXELS) {
            return null;
        }

        Mat landmarks = detectionOutput.row(row);
        try {
            return new DetectedFace(box, landmarks);
        } finally {
            landmarks.release();
        }
    }

    /** Releases the faces already built when a later row throws. */
    private static void releaseAll(List<DetectedFace> faces) {
        for (DetectedFace face : faces) {
            face.release();
        }
        faces.clear();
    }

    /**
     * Trims a detection box to the frame.
     *
     * <p>YuNet will report a box running off the edge whenever somebody is
     * half out of shot, and a Rect whose x + width exceeds cols() is a
     * native crash the first time anything takes a submat of it. An empty
     * Rect is returned for a box that lies entirely outside, and the
     * size check in {@link #readRow} then drops it.
     */
    private Rect clampRect(Rect rect, int frameWidth, int frameHeight) {
        int x = Math.max(0, rect.x);
        int y = Math.max(0, rect.y);
        int right = Math.min(frameWidth, rect.x + rect.width);
        int bottom = Math.min(frameHeight, rect.y + rect.height);

        if (right <= x || bottom <= y) {
            return new Rect();
        }
        return new Rect(x, y, right - x, bottom - y);
    }
}
