package com.attendance.repository;

import com.attendance.entity.Attendance;
import com.attendance.entity.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

    Optional<Attendance> findByStudentIdAndSubjectIdAndAttendanceDate(
            Long studentId, Long subjectId, LocalDate attendanceDate);

    /**
     * The rows that already exist for any of {@code studentIds} in one session.
     *
     * <p>Replaces one query per student in
     * {@code AttendanceService.markAbsenteesForSession}: a class of forty used
     * to cost forty selects before it wrote anything. Deliberately keyed on
     * (student, subject, date) and not on classroom, so it asks the same
     * question as the unique constraint and as the duplicate check on insert -
     * a student already marked for this subject today is already marked,
     * whichever room it happened in.
     */
    List<Attendance> findByStudentIdInAndSubjectIdAndAttendanceDate(
            Collection<Long> studentIds, Long subjectId, LocalDate attendanceDate);

    List<Attendance> findByStudentIdOrderByAttendanceDateDescAttendanceTimeDesc(Long studentId);

    List<Attendance> findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDescAttendanceTimeDesc(
            Long studentId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByStudentIdAndSubjectIdOrderByAttendanceDateDescAttendanceTimeDesc(
            Long studentId, Long subjectId);

    List<Attendance> findByStudentIdAndSubjectIdAndAttendanceDateBetweenOrderByAttendanceDateDescAttendanceTimeDesc(
            Long studentId, Long subjectId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateAscAttendanceTimeAsc(
            Long studentId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByClassroomIdAndSubjectIdAndAttendanceDateOrderByStudentIdAsc(
            Long classroomId, Long subjectId, LocalDate attendanceDate);

    List<Attendance> findByClassroomIdAndSubjectIdAndAttendanceDateBetween(
            Long classroomId, Long subjectId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByStudentIdAndClassroomIdAndSubjectIdAndAttendanceDateBetween(
            Long studentId, Long classroomId, Long subjectId, LocalDate startDate, LocalDate endDate);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.studentId = :studentId " +
            "AND a.subjectId = :subjectId AND a.status IN :attendedStatuses " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    long countAttendedSessions(@Param("studentId") Long studentId,
                               @Param("subjectId") Long subjectId,
                               @Param("attendedStatuses") Collection<Status> attendedStatuses,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate);

    @Query("SELECT COUNT(a) FROM Attendance a WHERE a.studentId = :studentId " +
            "AND a.subjectId = :subjectId " +
            "AND a.attendanceDate BETWEEN :startDate AND :endDate")
    long countTotalSessions(@Param("studentId") Long studentId,
                            @Param("subjectId") Long subjectId,
                            @Param("startDate") LocalDate startDate,
                            @Param("endDate") LocalDate endDate);

    /**
     * How much history would be orphaned by deleting a student, and the same
     * question for a subject and for a classroom below.
     *
     * <p>These three exist because {@code Attendance} keeps {@code studentId},
     * {@code subjectId} and {@code classroomId} as plain {@code Long} columns -
     * no {@code @ManyToOne}, no {@code @JoinColumn}, and therefore no foreign
     * key on any of them. So deleting a subject that has been taught for a term
     * does not fail: it succeeds, and every attendance row for it survives
     * pointing at an id that no longer names anything. Nothing in this package
     * resolves those ids back to names, so the corruption is silent - the export
     * simply prints {@code 7} where the subject used to be, for as long as the
     * database lives.
     *
     * <p>The delete endpoints ask these questions first and refuse rather than
     * cascade. Attendance is the record this system exists to keep; it should
     * not be destroyed as a side effect of tidying up a dropdown.
     *
     * <p>Adding the missing foreign keys would be the deeper fix and would let
     * the database refuse it outright. That is a schema migration against live
     * data that may already contain orphans, so it is not something to do
     * quietly inside a bug fix.
     */
    long countByStudentId(Long studentId);

    long countBySubjectId(Long subjectId);

    long countByClassroomId(Long classroomId);
}
