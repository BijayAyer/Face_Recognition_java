package com.fras.face;

/**
 * Immutable result returned by the face-recognition module.
 *
 * This class is intentionally independent of the database
 * and attendance modules.
 */
public final class RecognitionResult {

    private final String studentId;
    private final double score;
    private final boolean recognized;

    private RecognitionResult(
            String studentId,
            double score,
            boolean recognized
    ) {

        this.studentId = studentId;
        this.score = score;
        this.recognized = recognized;
    }

    public static RecognitionResult recognized(
            String studentId,
            double score
    ) {

        return new RecognitionResult(
                studentId,
                score,
                true
        );
    }

    public static RecognitionResult unknown(
            double score
    ) {

        return new RecognitionResult(
                "UNKNOWN",
                score,
                false
        );
    }

    public String getStudentId() {
        return studentId;
    }

    public double getScore() {
        return score;
    }

    public boolean isRecognized() {
        return recognized;
    }
}