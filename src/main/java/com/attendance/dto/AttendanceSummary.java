package com.attendance.dto;

public class AttendanceSummary {

    private final Long studentId;
    private final Long subjectId;
    private final long totalSessions;
    private final long attendedCount;
    private final double attendancePercentage;

    public AttendanceSummary(Long studentId, Long subjectId, long totalSessions, long attendedCount) {
        this.studentId = studentId;
        this.subjectId = subjectId;
        this.totalSessions = totalSessions;
        this.attendedCount = attendedCount;
        this.attendancePercentage = totalSessions == 0
                ? 0.0
                : Math.round((attendedCount * 10000.0) / totalSessions) / 100.0;
    }

    public Long getStudentId() {
        return studentId;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public long getTotalSessions() {
        return totalSessions;
    }

    public long getAttendedCount() {
        return attendedCount;
    }

    public double getAttendancePercentage() {
        return attendancePercentage;
    }
}
