package com.fras.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;

/**
 * One scheduled class session: a subject taught in a classroom on a
 * given weekday between two times.
 *
 * <p>The weekday column is deliberately named {@code day_of_week} and not
 * {@code day}: {@code DAY} is a reserved word in H2 2.x (and a keyword in
 * MySQL 8), so {@code create table timetables (... day varchar ...)} was
 * rejected by the database and this table was never created. The Java
 * field and its JSON property stay {@code day} so the desktop client's
 * payloads are unchanged.
 *
 * <p>The weekday is checked against the seven names rather than merely being
 * required. The client picks from a fixed list so it cannot send anything else,
 * but the endpoint is reachable without the client, and a row reading
 * "Funday" - or "monday", which the client's own day filter would then miss -
 * is not something to discover later. See {@link Department} for why the rest of
 * these constraints exist.
 *
 * <p>That the class ends after it starts is not expressed here. It is a rule
 * about two fields together, and stating it as a property constraint would name
 * the property in the message the user reads.
 * {@code TimetableRestController} checks it instead, in a sentence written for a
 * person, alongside the overlap check that no annotation could express.
 */
@Entity
@Table(
        name = "timetables",
        indexes = {
                @Index(name = "idx_timetable_classroom_day", columnList = "classroom_id, day_of_week"),
                @Index(name = "idx_timetable_subject_day", columnList = "subject_id, day_of_week")
        }
)
public class Timetable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A day of the week is required")
    @Pattern(
            regexp = "(?i)monday|tuesday|wednesday|thursday|friday|saturday|sunday",
            message = "must be a day of the week, spelled out in full")
    @Column(name = "day_of_week", nullable = false, length = 16)
    private String day;

    @NotNull(message = "A start time is required")
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @NotNull(message = "An end time is required")
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @NotNull(message = "A class must be booked in a classroom")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "classroom_id", nullable = false)
    private Classroom classroom;

    @NotNull(message = "A class must teach a subject")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    public Timetable() {
    }

    public Timetable(Long id, String day, LocalTime startTime, LocalTime endTime, Classroom classroom, Subject subject) {
        this.id = id;
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.classroom = classroom;
        this.subject = subject;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDay() {
        return day;
    }

    public void setDay(String day) {
        this.day = day;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Classroom getClassroom() {
        return classroom;
    }

    public void setClassroom(Classroom classroom) {
        this.classroom = classroom;
    }

    public Subject getSubject() {
        return subject;
    }

    public void setSubject(Subject subject) {
        this.subject = subject;
    }
}