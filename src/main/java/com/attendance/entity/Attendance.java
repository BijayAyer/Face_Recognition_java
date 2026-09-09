package com.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * One attendance record: a student, in a classroom, for a subject, on a date.
 *
 * <p>Student/classroom/subject are stored as plain ids rather than JPA
 * associations because they point at three different modules (and, for
 * students, a table owned by another package). The indexes below cover the
 * three access patterns the app actually uses: a student's own history, a
 * class register for one session, and the duplicate check on insert.
 */
@Entity
@Table(
        name = "attendance",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_attendance_student_subject_date",
                columnNames = {"student_id", "subject_id", "attendance_date"}
        ),
        indexes = {
                @Index(name = "idx_attendance_student_date", columnList = "student_id, attendance_date"),
                @Index(name = "idx_attendance_session", columnList = "classroom_id, subject_id, attendance_date"),
                @Index(name = "idx_attendance_date", columnList = "attendance_date")
        }
)
public class Attendance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Column(name = "classroom_id", nullable = false)
    private Long classroomId;

    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "attendance_time")
    private LocalTime attendanceTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Enumerated(EnumType.STRING)
    @Column(name = "marked_by", nullable = false, length = 24)
    private MarkedBy markedBy;

    @Column(name = "confidence_score")
    private Double confidenceScore;

    /** Wall-clock instant the row was written; useful for auditing edits. */
    @Column(name = "recorded_at")
    private Instant recordedAt;

    public Attendance() {
    }

    public Attendance(Long studentId, Long classroomId, Long subjectId,
                      LocalDate attendanceDate, LocalTime attendanceTime,
                      Status status, MarkedBy markedBy, Double confidenceScore) {
        this.studentId = studentId;
        this.classroomId = classroomId;
        this.subjectId = subjectId;
        this.attendanceDate = attendanceDate;
        this.attendanceTime = attendanceTime;
        this.status = status;
        this.markedBy = markedBy;
        this.confidenceScore = confidenceScore;
    }

    @PrePersist
    void onCreate() {
        if (recordedAt == null) {
            recordedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
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

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
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
