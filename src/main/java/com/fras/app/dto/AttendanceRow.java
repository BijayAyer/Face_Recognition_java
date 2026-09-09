package com.fras.app.dto;

/**
 * Lightweight client-side row model used to render the JSON array
 * returned by the attendance history ("/attendance/student/{id}")
 * and class register ("/attendance/classroom/.../date/...")
 * endpoints in a JavaFX TableView, instead of dumping the raw JSON
 * text.
 * <p>
 * Date/time fields are kept as plain Strings on purpose: Jackson
 * happily maps a JSON date/time string onto a String field, so no
 * extra jackson-datatype-jsr310 wiring is needed just for the
 * desktop client's read-only display.
 * <p>
 * {@code studentName}, {@code section}, {@code subject} and {@code room}
 * are what the server now sends alongside the three ids. The ids stayed:
 * the student id is the folder a face is enrolled into, so it is worth
 * showing, but "Room 2" and "Subject 2" were the register naming a room
 * and a subject by their table position while the two dropdowns directly
 * above it named them properly.
 */
public class AttendanceRow {

    private Long id;
    private Long studentId;
    private String studentName;
    private String section;
    private Long classroomId;
    private Long subjectId;
    private String subject;
    private String room;
    private String attendanceDate;
    private String attendanceTime;
    private String status;
    private String markedBy;
    private Double confidenceScore;

    public AttendanceRow() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public Long getClassroomId() {
        return classroomId;
    }

    public void setClassroomId(Long classroomId) {
        this.classroomId = classroomId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public void setSubjectId(Long subjectId) {
        this.subjectId = subjectId;
    }

    public String getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(String attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public String getAttendanceTime() {
        return attendanceTime;
    }

    public void setAttendanceTime(String attendanceTime) {
        this.attendanceTime = attendanceTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
}
