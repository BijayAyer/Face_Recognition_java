package com.attendance.service;

import com.attendance.entity.Attendance;
import com.attendance.entity.Status;
import com.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttendanceHistory {

    private final AttendanceRepository attendanceRepository;

    public AttendanceHistory(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public List<Attendance> getFullHistory(Long studentId) {
        return attendanceRepository.findByStudentIdOrderByAttendanceDateDescAttendanceTimeDesc(studentId);
    }

    public List<Attendance> getHistoryBetween(Long studentId, LocalDate start, LocalDate end) {
        return attendanceRepository
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDescAttendanceTimeDesc(
                        studentId, start, end);
    }

    public List<Attendance> getHistoryBySubject(Long studentId, Long subjectId) {
        return attendanceRepository
                .findByStudentIdAndSubjectIdOrderByAttendanceDateDescAttendanceTimeDesc(studentId, subjectId);
    }

    public Map<LocalDate, List<Attendance>> getMonthlyView(Long studentId, YearMonth month) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        return attendanceRepository
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateAscAttendanceTimeAsc(
                        studentId, start, end)
                .stream()
                .collect(Collectors.groupingBy(
                        Attendance::getAttendanceDate,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }

    public List<Attendance> getClassRegister(Long classroomId, Long subjectId, LocalDate date) {
        return attendanceRepository
                .findByClassroomIdAndSubjectIdAndAttendanceDateOrderByStudentIdAsc(
                        classroomId, subjectId, date);
    }

    public int getCurrentAbsenceStreak(Long studentId, Long subjectId) {
        List<Attendance> history = getHistoryBySubject(studentId, subjectId);
        int streak = 0;

        for (Attendance attendance : history) {
            if (attendance.getStatus() == Status.ABSENT) {
                streak++;
            } else {
                break;
            }
        }

        return streak;
    }

    public Map<Status, Long> getStatusBreakdown(Long studentId, LocalDate start, LocalDate end) {
        Map<Status, Long> breakdown = new EnumMap<>(Status.class);
        for (Status status : Status.values()) {
            breakdown.put(status, 0L);
        }

        for (Attendance attendance : getHistoryBetween(studentId, start, end)) {
            breakdown.merge(attendance.getStatus(), 1L, Long::sum);
        }

        return breakdown;
    }
}
