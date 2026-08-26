package com.attendance;

import com.attendance.dto.MarkAttendanceRequest;
import com.attendance.entity.Attendance;
import com.attendance.entity.MarkedBy;
import com.attendance.export.AttendanceExcelExport;
import com.attendance.export.AttendancePdfExport;
import com.attendance.report.AttendanceReports;
import com.attendance.report.AttendanceReports.ClassDailyReport;
import com.attendance.report.AttendanceReports.ClassRangeReport;
import com.attendance.report.AttendanceReports.StudentOverallReport;
import com.attendance.report.AttendanceReports.StudentSubjectReport;
import com.attendance.service.AttendanceHistory;
import com.attendance.service.AttendanceService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;

@SpringBootApplication
public class AttendanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }

    @Bean
    CommandLineRunner demoRunner(AttendanceService attendanceService,
                                 AttendanceHistory attendanceHistory,
                                 AttendanceReports attendanceReports,
                                 AttendanceExcelExport excelExport,
                                 AttendancePdfExport pdfExport) {
        return args -> {
            LocalDate demoDate = LocalDate.of(2026, 8, 20);
            LocalTime sessionStart = LocalTime.of(9, 0);
            Long classroomId = 1L;
            Long subjectId = 501L;
            List<Long> roster = List.of(101L, 102L, 103L, 104L, 105L);

            System.out.println("Marking sample attendance...");
            print(attendanceService.markAttendance(new MarkAttendanceRequest(
                    101L, classroomId, subjectId, demoDate, LocalTime.of(9, 5),
                    MarkedBy.FACE_RECOGNITION, 0.92), sessionStart));
            print(attendanceService.markAttendance(new MarkAttendanceRequest(
                    102L, classroomId, subjectId, demoDate, LocalTime.of(9, 25),
                    MarkedBy.FACE_RECOGNITION, 0.88), sessionStart));
            print(attendanceService.markAttendance(new MarkAttendanceRequest(
                    103L, classroomId, subjectId, demoDate, LocalTime.of(9, 2),
                    MarkedBy.MANUAL_TEACHER, null), sessionStart));

            try {
                attendanceService.markAttendance(new MarkAttendanceRequest(
                        101L, classroomId, subjectId, demoDate, LocalTime.of(9, 10),
                        MarkedBy.MANUAL_TEACHER, null), sessionStart);
            } catch (RuntimeException ex) {
                System.out.println("Expected duplicate error: " + ex.getMessage());
            }

            try {
                attendanceService.markAttendance(new MarkAttendanceRequest(
                        104L, classroomId, subjectId, demoDate, LocalTime.of(9, 6),
                        MarkedBy.FACE_RECOGNITION, 0.40), sessionStart);
            } catch (RuntimeException ex) {
                System.out.println("Expected low-confidence error: " + ex.getMessage());
            }

            attendanceService.markAbsenteesForSession(roster, classroomId, subjectId, demoDate);

            System.out.println();
            System.out.println("Student 101 history:");
            attendanceHistory.getFullHistory(101L).forEach(AttendanceApplication::print);

            System.out.println();
            System.out.println("Monthly view for student 101:");
            attendanceHistory.getMonthlyView(101L, YearMonth.of(2026, 8))
                    .forEach((date, records) -> System.out.println(date + " -> " + records.size() + " record(s)"));

            System.out.println();
            System.out.println("Class register:");
            attendanceHistory.getClassRegister(classroomId, subjectId, demoDate)
                    .forEach(AttendanceApplication::print);

            StudentSubjectReport studentReport = attendanceReports.generateStudentSubjectReport(
                    102L, subjectId, demoDate, demoDate);
            StudentOverallReport overallReport = attendanceReports.generateStudentOverallReport(
                    102L, demoDate, demoDate);
            ClassDailyReport dailyReport = attendanceReports.generateClassDailyReport(
                    classroomId, subjectId, demoDate, roster);
            ClassRangeReport rangeReport = attendanceReports.generateClassRangeReport(
                    classroomId, subjectId, demoDate, demoDate, roster);

            System.out.println();
            System.out.printf("Student 102 subject attendance: %.2f%%%n",
                    studentReport.getAttendancePercentage());
            System.out.printf("Student 102 overall attendance: %.2f%%%n",
                    overallReport.getOverallPercentage());
            System.out.printf("Class average attendance: %.2f%%%n",
                    rangeReport.getClassAveragePercentage());
            System.out.println("Students below 75%: " + attendanceReports.getStudentsBelowThreshold(
                    classroomId, subjectId, demoDate, demoDate, roster, 75.0));

            Path reportDir = Path.of("target", "generated-reports");
            Files.createDirectories(reportDir);
            excelExport.exportStudentSummaryReport(
                    List.of(studentReport),
                    reportDir.resolve("attendance-summary.xlsx").toString());
            excelExport.exportClassDailyReport(
                    dailyReport,
                    reportDir.resolve("daily-register.xlsx").toString());
            excelExport.exportClassRangeReport(
                    rangeReport,
                    reportDir.resolve("class-trend.xlsx").toString());
            pdfExport.exportStudentSummaryReport(
                    List.of(studentReport),
                    reportDir.resolve("attendance-summary.pdf").toString());
            pdfExport.exportClassDailyReport(
                    dailyReport,
                    reportDir.resolve("daily-register.pdf").toString());
            pdfExport.exportClassRangeReport(
                    rangeReport,
                    reportDir.resolve("class-trend.pdf").toString());

            System.out.println();
            System.out.println("Reports written to: " + reportDir.toAbsolutePath());
        };
    }

    private static void print(Attendance attendance) {
        System.out.printf("Student %d | Subject %d | %s | %s | %s | by=%s%n",
                attendance.getStudentId(),
                attendance.getSubjectId(),
                attendance.getAttendanceDate(),
                attendance.getAttendanceTime() != null ? attendance.getAttendanceTime() : "--:--",
                attendance.getStatus(),
                attendance.getMarkedBy());
    }
}
