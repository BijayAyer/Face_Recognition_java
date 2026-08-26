package com.attendance.report;

import com.attendance.entity.Attendance;
import com.attendance.entity.Status;
import com.attendance.repository.AttendanceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AttendanceReports {

    private final AttendanceRepository attendanceRepository;

    public AttendanceReports(AttendanceRepository attendanceRepository) {
        this.attendanceRepository = attendanceRepository;
    }

    public static class StudentSubjectReport {
        private final Long studentId;
        private final Long subjectId;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final long totalSessions;
        private final long presentCount;
        private final long absentCount;
        private final long lateCount;
        private final long excusedCount;
        private final double attendancePercentage;

        public StudentSubjectReport(Long studentId, Long subjectId, LocalDate startDate, LocalDate endDate,
                                    long totalSessions, long presentCount, long absentCount,
                                    long lateCount, long excusedCount) {
            this.studentId = studentId;
            this.subjectId = subjectId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.totalSessions = totalSessions;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.excusedCount = excusedCount;

            long attended = presentCount + lateCount;
            this.attendancePercentage = totalSessions == 0
                    ? 0.0
                    : Math.round((attended * 10000.0) / totalSessions) / 100.0;
        }

        public Long getStudentId() {
            return studentId;
        }

        public Long getSubjectId() {
            return subjectId;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public long getTotalSessions() {
            return totalSessions;
        }

        public long getPresentCount() {
            return presentCount;
        }

        public long getAbsentCount() {
            return absentCount;
        }

        public long getLateCount() {
            return lateCount;
        }

        public long getExcusedCount() {
            return excusedCount;
        }

        public double getAttendancePercentage() {
            return attendancePercentage;
        }
    }

    public static class StudentOverallReport {
        private final Long studentId;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final List<StudentSubjectReport> perSubject;
        private final double overallPercentage;

        public StudentOverallReport(Long studentId, LocalDate startDate, LocalDate endDate,
                                    List<StudentSubjectReport> perSubject) {
            this.studentId = studentId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.perSubject = perSubject;

            long totalSessions = perSubject.stream().mapToLong(StudentSubjectReport::getTotalSessions).sum();
            long totalAttended = perSubject.stream()
                    .mapToLong(report -> report.getPresentCount() + report.getLateCount())
                    .sum();
            this.overallPercentage = totalSessions == 0
                    ? 0.0
                    : Math.round((totalAttended * 10000.0) / totalSessions) / 100.0;
        }

        public Long getStudentId() {
            return studentId;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public List<StudentSubjectReport> getPerSubject() {
            return perSubject;
        }

        public double getOverallPercentage() {
            return overallPercentage;
        }
    }

    public static class ClassDailyReport {
        private final Long classroomId;
        private final Long subjectId;
        private final LocalDate date;
        private final int totalStudents;
        private final int presentCount;
        private final int absentCount;
        private final int lateCount;
        private final List<Long> absentStudentIds;

        public ClassDailyReport(Long classroomId, Long subjectId, LocalDate date,
                                int totalStudents, int presentCount, int absentCount,
                                int lateCount, List<Long> absentStudentIds) {
            this.classroomId = classroomId;
            this.subjectId = subjectId;
            this.date = date;
            this.totalStudents = totalStudents;
            this.presentCount = presentCount;
            this.absentCount = absentCount;
            this.lateCount = lateCount;
            this.absentStudentIds = absentStudentIds;
        }

        public Long getClassroomId() {
            return classroomId;
        }

        public Long getSubjectId() {
            return subjectId;
        }

        public LocalDate getDate() {
            return date;
        }

        public int getTotalStudents() {
            return totalStudents;
        }

        public int getPresentCount() {
            return presentCount;
        }

        public int getAbsentCount() {
            return absentCount;
        }

        public int getLateCount() {
            return lateCount;
        }

        public List<Long> getAbsentStudentIds() {
            return absentStudentIds;
        }
    }

    public static class ClassRangeReport {
        private final Long classroomId;
        private final Long subjectId;
        private final LocalDate startDate;
        private final LocalDate endDate;
        private final Map<Long, Double> perStudentPercentage;
        private final double classAveragePercentage;

        public ClassRangeReport(Long classroomId, Long subjectId, LocalDate startDate, LocalDate endDate,
                                Map<Long, Double> perStudentPercentage) {
            this.classroomId = classroomId;
            this.subjectId = subjectId;
            this.startDate = startDate;
            this.endDate = endDate;
            this.perStudentPercentage = perStudentPercentage;

            double average = perStudentPercentage.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);
            this.classAveragePercentage = Math.round(average * 100.0) / 100.0;
        }

        public Long getClassroomId() {
            return classroomId;
        }

        public Long getSubjectId() {
            return subjectId;
        }

        public LocalDate getStartDate() {
            return startDate;
        }

        public LocalDate getEndDate() {
            return endDate;
        }

        public Map<Long, Double> getPerStudentPercentage() {
            return perStudentPercentage;
        }

        public double getClassAveragePercentage() {
            return classAveragePercentage;
        }
    }

    public StudentSubjectReport generateStudentSubjectReport(Long studentId, Long subjectId,
                                                             LocalDate start, LocalDate end) {
        List<Attendance> filtered = attendanceRepository
                .findByStudentIdAndSubjectIdAndAttendanceDateBetweenOrderByAttendanceDateDescAttendanceTimeDesc(
                        studentId, subjectId, start, end);
        return toStudentSubjectReport(studentId, subjectId, start, end, filtered);
    }

    public StudentOverallReport generateStudentOverallReport(Long studentId,
                                                             LocalDate start, LocalDate end) {
        List<Long> subjectIds = attendanceRepository
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateDescAttendanceTimeDesc(
                        studentId, start, end)
                .stream()
                .map(Attendance::getSubjectId)
                .distinct()
                .collect(Collectors.toList());

        List<StudentSubjectReport> perSubject = subjectIds.stream()
                .map(subjectId -> generateStudentSubjectReport(studentId, subjectId, start, end))
                .collect(Collectors.toList());

        return new StudentOverallReport(studentId, start, end, perSubject);
    }

    public ClassDailyReport generateClassDailyReport(Long classroomId, Long subjectId,
                                                     LocalDate date, List<Long> classRoster) {
        Map<Long, Status> statusByStudent = attendanceRepository
                .findByClassroomIdAndSubjectIdAndAttendanceDateOrderByStudentIdAsc(classroomId, subjectId, date)
                .stream()
                .collect(Collectors.toMap(
                        Attendance::getStudentId,
                        Attendance::getStatus,
                        (first, ignored) -> first));

        int present = 0;
        int absent = 0;
        int late = 0;
        List<Long> absentees = new ArrayList<>();

        for (Long studentId : classRoster) {
            Status status = statusByStudent.getOrDefault(studentId, Status.ABSENT);
            switch (status) {
                case PRESENT -> present++;
                case LATE -> late++;
                case ABSENT -> {
                    absent++;
                    absentees.add(studentId);
                }
                case EXCUSED -> {
                    // Excused is a recorded non-attendance status, not an absence.
                }
            }
        }

        return new ClassDailyReport(classroomId, subjectId, date, classRoster.size(),
                present, absent, late, absentees);
    }

    public ClassRangeReport generateClassRangeReport(Long classroomId, Long subjectId,
                                                     LocalDate start, LocalDate end,
                                                     List<Long> classRoster) {
        Map<Long, Double> perStudent = new LinkedHashMap<>();
        for (Long studentId : classRoster) {
            StudentSubjectReport report = generateStudentSubjectReport(
                    studentId, classroomId, subjectId, start, end);
            perStudent.put(studentId, report.getAttendancePercentage());
        }
        return new ClassRangeReport(classroomId, subjectId, start, end, perStudent);
    }

    public List<Long> getStudentsBelowThreshold(Long classroomId, Long subjectId,
                                                LocalDate start, LocalDate end,
                                                List<Long> classRoster, double thresholdPercent) {
        List<Long> flagged = new ArrayList<>();
        for (Long studentId : classRoster) {
            StudentSubjectReport report = generateStudentSubjectReport(
                    studentId, classroomId, subjectId, start, end);
            if (report.getAttendancePercentage() < thresholdPercent) {
                flagged.add(studentId);
            }
        }
        return flagged;
    }

    private StudentSubjectReport generateStudentSubjectReport(Long studentId, Long classroomId, Long subjectId,
                                                              LocalDate start, LocalDate end) {
        List<Attendance> filtered = attendanceRepository
                .findByStudentIdAndClassroomIdAndSubjectIdAndAttendanceDateBetween(
                        studentId, classroomId, subjectId, start, end);
        return toStudentSubjectReport(studentId, subjectId, start, end, filtered);
    }

    private StudentSubjectReport toStudentSubjectReport(Long studentId, Long subjectId,
                                                        LocalDate start, LocalDate end,
                                                        List<Attendance> filtered) {
        long total = filtered.size();
        long present = countStatus(filtered, Status.PRESENT);
        long absent = countStatus(filtered, Status.ABSENT);
        long late = countStatus(filtered, Status.LATE);
        long excused = countStatus(filtered, Status.EXCUSED);

        return new StudentSubjectReport(studentId, subjectId, start, end,
                total, present, absent, late, excused);
    }

    private long countStatus(List<Attendance> records, Status status) {
        return records.stream()
                .filter(attendance -> attendance.getStatus() == status)
                .count();
    }
}
