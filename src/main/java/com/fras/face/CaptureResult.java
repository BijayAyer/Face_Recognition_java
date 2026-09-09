package com.fras.face;

/**
 * The outcome of trying to capture one enrolment sample: whether a template was
 * written, and in plain words why not.
 *
 * <p><b>Why this exists instead of a boolean.</b> Capture can be refused for at
 * least five different reasons - no face in frame, more than one face, the face
 * too small, an unusable student id, or the file could not be written - and each
 * one needs a different thing done about it. The old signature returned
 * {@code boolean} and printed the reason to standard output, so the screen could
 * only ever say "nothing was saved, try again", and the person standing in front
 * of the camera had to guess whether to step closer, ask a classmate to move out
 * of shot, or fetch somebody about a full disk. The reason travels with the
 * answer now.
 *
 * @param saved   true if a template file was written
 * @param message one sentence, already fit to put in front of a person: what
 *                happened, and where relevant what to do about it
 */
public record CaptureResult(boolean saved, String message) {

    /** A sample was written. */
    public static CaptureResult ok(String message) {
        return new CaptureResult(true, message);
    }

    /**
     * Nothing was written, for the stated reason.
     *
     * <p>Covers both "the room is not right for this" and "the disk said no".
     * The screen treats them the same way - it shows the sentence - so they are
     * not distinguished here.
     */
    public static CaptureResult refused(String reason) {
        return new CaptureResult(false, reason);
    }
}
