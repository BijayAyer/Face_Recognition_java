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
}
