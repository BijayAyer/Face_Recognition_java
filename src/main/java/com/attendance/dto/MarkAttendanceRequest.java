package com.attendance.dto;

import com.attendance.entity.MarkedBy;

import java.time.LocalDate;
import java.time.LocalTime;

public class MarkAttendanceRequest {

    private Long studentId;
    private Long classroomId;
    private Long subjectId;
    private LocalDate attendanceDate;
    private LocalTime attendanceTime;
    private MarkedBy markedBy;
    private Double confidenceScore;

    public MarkAttendanceRequest() {
    }

    public MarkAttendanceRequest(Long studentId, Long classroomId, Long subjectId,
                                 LocalDate attendanceDate, LocalTime attendanceTime,
                                 MarkedBy markedBy, Double confidenceScore) {
        this.studentId = studentId;
        this.classroomId = classroomId;
        this.subjectId = subjectId;
        this.attendanceDate = attendanceDate;
        this.attendanceTime = attendanceTime;
        this.markedBy = markedBy;
        this.confidenceScore = confidenceScore;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
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

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public LocalTime getAttendanceTime() {
        return attendanceTime;
    }

    public void setAttendanceTime(LocalTime attendanceTime) {
        this.attendanceTime = attendanceTime;
    }

    public MarkedBy getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(MarkedBy markedBy) {
        this.markedBy = markedBy;
    }

    public Double getConfidenceScore() {
        return confidenceScore;
    }

    public void setConfidenceScore(Double confidenceScore) {
        this.confidenceScore = confidenceScore;
    }
}
