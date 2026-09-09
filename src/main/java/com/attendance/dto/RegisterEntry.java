package com.attendance.dto;

import com.attendance.entity.Attendance;
import com.attendance.entity.MarkedBy;
import com.attendance.entity.Status;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * One line of a register, as a person reads it.
 *
 * <p>The attendance endpoints used to answer with {@code Attendance} itself,
 * and {@code Attendance} keeps {@code student_id}, {@code subject_id} and
 * {@code classroom_id} as plain numbers with no associations - so the register
 * on screen read "Student 1 | Room 2 | Subject 2" while the two dropdowns
 * directly above it read "404, Hall" and "cse212 - oop". Every number in that
 * row is a fact about where the data is stored rather than about the class that
 * was taught.
 *
 * <p>The ids are still here under their original names: the desktop client and
 * the tests both read {@code studentId}, and the student id is also the folder
 * a face is enrolled into, which makes it the one number worth printing. What
 * is added is the four things that were missing - who the student is, which
 * section they sit in, what the subject is called, and which room it was in.
 *
 * <p>Dates and times are rendered here rather than left as {@code LocalDate}
 * and {@code LocalTime}, because the client holds both as strings and this is
 * the last place that still knows they are not. The time loses its fractional
 * seconds on the way past: {@code LocalTime.toString()} prints "13:38:55.704",
 * and the milliseconds of a lecture are three digits of noise on every line of
 * a printed register.
 */
public class RegisterEntry {

    /** What a register says when nobody has recorded a section. */
    public static final String NO_SECTION = "-";

    /**
     * How a line reads when the roster expected somebody and no attendance was
     * ever recorded for them. It still counts as an absence - which is what
     * {@code AttendanceReports} assumes when it totals a session - but a
     * recorded absence and a student nobody marked are not the same thing, and
     * a register is where that difference has to be visible.
     */
    public static final String NOT_MARKED = "Not marked";

    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");

    private Long id;
    private Long studentId;
    private String studentName = "";
    private String section = NO_SECTION;
    private Long classroomId;
    private Long subjectId;
    private String subject = "";
    private String room = "";
    private String attendanceDate = "";
    private String attendanceTime = "";
    private String status = "";
    private String markedBy = "";
    private Double confidenceScore;

    public RegisterEntry() {
    }

    /**
     * A row that exists: somebody was marked, and this is what was written
     * down. The four labels are filled in afterwards, by the one service that
     * can look them up.
     *
     * <p>{@code status} stays the raw enum name. It is not for reading - the
     * desktop client turns it into a coloured badge by matching on
     * {@code PRESENT}, {@code ABSENT} and {@code LATE}, and the export writers
     * decide what to flag the same way, so humanising it here would quietly
     * blank every badge in the application.
     */
    public static RegisterEntry recorded(Attendance row) {
        RegisterEntry entry = new RegisterEntry();
        entry.id = row.getId();
        entry.studentId = row.getStudentId();
        entry.classroomId = row.getClassroomId();
        entry.subjectId = row.getSubjectId();
        entry.attendanceDate = date(row.getAttendanceDate());
        entry.attendanceTime = time(row.getAttendanceTime());
        entry.status = row.getStatus() == null ? "" : row.getStatus().name();
        entry.markedBy = label(row.getMarkedBy());
        entry.confidenceScore = row.getConfidenceScore();
        return entry;
    }

    /**
     * A student the roster expected, with no attendance row anywhere for that
     * session. Absent, and deliberately with no record id, no time and no
     * confidence: giving an unmarked student a time would be inventing a
     * moment nobody observed.
     */
    public static RegisterEntry unmarked(Long studentId, Long classroomId, Long subjectId,
                                         LocalDate date) {
        RegisterEntry entry = new RegisterEntry();
        entry.studentId = studentId;
        entry.classroomId = classroomId;
        entry.subjectId = subjectId;
        entry.attendanceDate = date(date);
        entry.status = Status.ABSENT.name();
        entry.markedBy = NOT_MARKED;
        return entry;
    }

    /**
     * How the row was recorded, as words. The enum name is the database's term
     * for it; "SYSTEM_AUTO_ABSENT" on a register handed to a head of year is a
     * constant that escaped.
     */
    private static String label(MarkedBy markedBy) {
        if (markedBy == null) {
            return "";
        }
        return switch (markedBy) {
            case FACE_RECOGNITION -> "Face recognition";
            case MANUAL_TEACHER -> "Teacher";
            case SYSTEM_AUTO_ABSENT -> "Session close";
        };
    }

    private static String date(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private static String time(LocalTime time) {
        return time == null ? "" : CLOCK.format(time);
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName == null ? "" : studentName;
    }

    public String getSection() {
        return section;
    }

    /**
     * Blank and null both become "-", so "no section" prints one way. A
     * register with an empty cell on one line and "-" on the next is reporting
     * a difference that does not exist.
     */
    public void setSection(String section) {
        this.section = section == null || section.isBlank() ? NO_SECTION : section.trim();
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject == null ? "" : subject;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room == null ? "" : room;
    }

    public String getAttendanceDate() {
        return attendanceDate;
    }

    public String getAttendanceTime() {
        return attendanceTime;
    }

    public String getStatus() {
        return status;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    /** True for a roster member nobody marked, which the exports print in bold. */
    public boolean isUnmarked() {
        return NOT_MARKED.equals(markedBy);
    }

    @Override
    public String toString() {
        return "RegisterEntry{studentId=" + studentId
                + ", studentName='" + studentName + "'"
                + ", section='" + section + "'"
                + ", subject='" + subject + "'"
                + ", room='" + room + "'"
                + ", date=" + attendanceDate
                + ", status=" + status + '}';
    }
}
