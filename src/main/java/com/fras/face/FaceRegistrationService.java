package com.fras.face;

import org.opencv.core.Mat;
import org.opencv.core.Rect;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.objdetect.FaceRecognizerSF;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Enrolment: turns one camera frame into one stored face template.
 *
 * <p>What is written is not a crop of the frame. YuNet's five landmarks are
 * handed to {@code alignCrop}, which rotates and scales the face so that the
 * eyes land on fixed pixels of a 112x112 image, and that is what goes to disk.
 * It matters that this is the same transform SFace applies when recognising: a
 * template stored as a plain rectangle would be compared against aligned crops
 * for the rest of its life, and every comparison would pay for the mismatch.
 *
 * <p>Samples live at {@code data/faces/<studentId>/1.png}, numbered upwards. The
 * folder name is the student id, and it is also how {@link FaceRecognitionService}
 * learns whose face it is looking at.
 *
 * <p><b>Every outcome is now returned rather than printed.</b> This class used to
 * answer {@code boolean} and send the reason to standard output, which in a
 * windowed application means nowhere. The screen could only say "nothing was
 * saved", so a person who needed to step closer, or to ask a classmate to move
 * out of shot, was told the same thing as one whose disk was full. The reason
 * travels with the answer as a {@link CaptureResult}.
 */
public class FaceRegistrationService {

    private static final Logger log = LoggerFactory.getLogger(FaceRegistrationService.class);

    private static final String BASE_DIR = "data/faces";

    /**
     * Also what keeps a student id from escaping the samples directory. The id
     * becomes a folder name, so {@code ../..} or an absolute path would
     * otherwise write wherever it liked; letters, digits, hyphen and underscore
     * cannot.
     */
    private static final Pattern STUDENT_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,50}");

    /**
     * The smallest face worth enrolling. Detection accepts smaller ones,
     * because naming somebody across the room is useful even from a rough box,
     * but a template is what every later match is measured against - a blurry
     * one is a permanent handicap rather than one bad frame.
     */
    private static final int MIN_SAMPLE_PIXELS = 80;

    /**
     * A ceiling on the search for an unused file name, so that a folder left in
     * a strange state cannot turn enrolment into a hang. Nobody needs five
     * hundred samples of one face; twenty is generous.
     */
    private static final int MAX_SAMPLES_PER_STUDENT = 500;

    private final FaceRecognizerSF recognizer;

    public FaceRegistrationService(String sfaceModelPath) {
        recognizer = FaceRecognizerSF.create(sfaceModelPath, "");
        if (recognizer == null) {
            throw new IllegalStateException(
                    "Unable to load the SFace model from " + sfaceModelPath + ".");
        }
    }

    /**
     * Aligns the single visible face and writes it as this student's next
     * sample.
     *
     * <p>Exactly one face, deliberately. Two faces in shot and there is no way
     * to know which one the teacher meant, and enrolling the wrong one puts a
     * classmate's face under this student's name - after which the recogniser
     * marks the wrong person present and does it confidently. Refusing is the
     * only safe answer.
     *
     * <p>Neither {@code frame} nor {@code faces} is retained or released here;
     * the caller owns both.
     */
    public CaptureResult captureSample(Mat frame, List<DetectedFace> faces, String studentId) {
        if (frame == null || frame.empty()) {
            return CaptureResult.refused(
                    "There is no camera picture to capture from yet.");
        }

        if (faces == null || faces.isEmpty()) {
            return CaptureResult.refused(
                    "No face in the picture. Look at the camera and try again.");
        }
        if (faces.size() > 1) {
            return CaptureResult.refused(
                    faces.size() + " faces are in shot. Enrolment needs the one student "
                            + "alone in front of the camera.");
        }

        if (studentId == null || !STUDENT_ID_PATTERN.matcher(studentId.trim()).matches()) {
            return CaptureResult.refused(
                    "That student cannot be enrolled: the ID \"" + studentId
                            + "\" is not one this system can store.");
        }

        String id = studentId.trim();
        Rect box = faces.get(0).getBoundingBox();
        if (box.width < MIN_SAMPLE_PIXELS || box.height < MIN_SAMPLE_PIXELS) {
            return CaptureResult.refused(
                    "The face is too small to enrol (" + box.width + " by " + box.height
                            + " pixels). Move closer to the camera.");
        }

        return writeSample(frame, faces.get(0), id);
    }

    /**
     * The part that touches native memory and the disk.
     *
     * <p>Split out so that the checks above read as checks. The landmarks are a
     * copy that {@link DetectedFace#getLandmarks()} handed over, so they are
     * this method's to release, and the aligned crop is allocated here - both
     * are freed on every path out.
     */
    private CaptureResult writeSample(Mat frame, DetectedFace face, String id) {
        Mat landmarks = face.getLandmarks();
        Mat alignedFace = new Mat();
        try {
            recognizer.alignCrop(frame, landmarks, alignedFace);
            if (alignedFace.empty()) {
                log.warn("alignCrop produced nothing for student {}.", id);
                return CaptureResult.refused(
                        "The face could not be squared up. Face the camera straight on "
                                + "and try again.");
            }

            File folder = new File(BASE_DIR, id);
            if (!folder.isDirectory() && !folder.mkdirs()) {
                log.error("Could not create the sample folder {}.", folder.getAbsolutePath());
                return CaptureResult.refused(
                        "The folder for this student's samples could not be created. "
                                + "Check that the application can write to " + BASE_DIR + ".");
            }
            File output = nextSampleFile(folder);
            if (output == null) {
                return CaptureResult.refused(
                        "This student already has " + MAX_SAMPLES_PER_STUDENT
                                + " samples, which is far more than recognition needs.");
            }

            if (!Imgcodecs.imwrite(output.getAbsolutePath(), alignedFace)) {
                log.error("imwrite refused to write {}.", output.getAbsolutePath());
                return CaptureResult.refused(
                        "The sample could not be written to disk. Check the free space "
                                + "and the permissions on " + BASE_DIR + ".");
            }

            log.info("Enrolled an aligned sample for student {} at {}.",
                    id, output.getAbsolutePath());
            return CaptureResult.ok("Sample " + output.getName().replace(".png", "")
                    + " saved for this student.");

        } catch (RuntimeException nativeFailure) {
            // alignCrop throws on a malformed landmark row. Letting it out would
            // reach the work thread as an uncaught error and the screen would be
            // told nothing at all.
            log.error("Aligning the face for student {} failed.", id, nativeFailure);
            return CaptureResult.refused(
                    "The face could not be processed. Try again, and if it keeps "
                            + "happening restart the application.");
        } finally {
            landmarks.release();
            alignedFace.release();
        }
    }

    /**
     * The lowest unused {@code n.png} in the folder, or null if the ceiling has
     * been reached.
     *
     * <p>Lowest unused rather than one-past-the-highest, so that deleting a bad
     * sample frees its number again. Only ever called from the single enrolment
     * thread, so nothing else is racing to claim the name in between.
     */
    private File nextSampleFile(File folder) {
        for (int number = 1; number <= MAX_SAMPLES_PER_STUDENT; number++) {
            File candidate = new File(folder, number + ".png");
            if (!candidate.exists()) {
                return candidate;
            }
        }
        log.warn("Student folder {} already holds {} samples.",
                folder.getAbsolutePath(), MAX_SAMPLES_PER_STUDENT);
        return null;
    }
}
