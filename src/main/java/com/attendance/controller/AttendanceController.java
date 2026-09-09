package com.attendance.controller;

import com.attendance.dto.MarkAttendanceRequest;
import com.attendance.dto.RegisterEntry;
import com.attendance.entity.Attendance;
import com.attendance.export.AttendanceExcelExport;
import com.attendance.export.AttendancePdfExport;
import com.attendance.report.AttendanceReports;
import com.attendance.report.AttendanceReports.ClassDailyReport;
import com.attendance.security.AttendanceAccessPolicy;
import com.attendance.service.AttendanceHistory;
import com.attendance.service.AttendanceService;
import com.attendance.service.RegisterView;
import com.school.school_management_system.security.CustomUserDetails;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * REST endpoints for the attendance module.
 *
 * <p>This module previously had entity/repository/service/report/export
 * classes but no controller exposing them over HTTP - it was only reachable
 * from a standalone demo {@code CommandLineRunner}. These endpoints are what
 * the JavaFX desktop client ({@code ApiService}) calls to record
 * face-recognition attendance and to pull reports and exports.
 *
 * <p><b>Every method that names a student or a class asks
 * {@link AttendanceAccessPolicy} first.</b> URL-level rules in
 * {@code SecurityConfig} can only say "a signed-in account may GET
 * /attendance/**", which was the whole problem: {@code studentId} is a
 * sequential id chosen by the caller, so that rule let any account with a
 * token read every student's history by counting upwards. Authorisation that
 * depends on the value of a path variable has to happen where the value is
 * known, which is here.
 */
@RestController
@RequestMapping("/attendance")
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final AttendanceHistory attendanceHistory;
    private final AttendanceReports attendanceReports;
    private final AttendanceExcelExport excelExport;
    private final AttendancePdfExport pdfExport;
    private final AttendanceAccessPolicy access;
    private final RegisterView register;

    public AttendanceController(AttendanceService attendanceService,
                                AttendanceHistory attendanceHistory,
                                AttendanceReports attendanceReports,
                                AttendanceExcelExport excelExport,
                                AttendancePdfExport pdfExport,
                                AttendanceAccessPolicy access,
                                RegisterView register) {
        this.attendanceService = attendanceService;
        this.attendanceHistory = attendanceHistory;
        this.attendanceReports = attendanceReports;
        this.excelExport = excelExport;
        this.pdfExport = pdfExport;
        this.access = access;
        this.register = register;
    }

    /**
     * Mark attendance for one student. Used both for face-recognition
     * auto-marking ({@code markedBy=FACE_RECOGNITION}, {@code confidenceScore}
     * set) and manual teacher marking ({@code markedBy=MANUAL_TEACHER}).
     *
     * <p>Session start time defaults to none - everything counts as PRESENT -
     * unless {@code sessionStartTime=HH:mm} is supplied, in which case anyone
     * marked more than 15 minutes after it is recorded as LATE.
     */
    @PostMapping("/mark")
    public ResponseEntity<Attendance> markAttendance(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestBody MarkAttendanceRequest request,
            @RequestParam(required = false) String sessionStartTime) {

        access.requireCanRecord(principal);

        LocalTime sessionStart = (sessionStartTime != null && !sessionStartTime.isBlank())
                ? LocalTime.parse(sessionStartTime)
                : null;

        Attendance saved = attendanceService.markAttendance(request, sessionStart);
        return ResponseEntity.ok(saved);
    }

    /**
     * Mark everyone in the roster who was not already marked as ABSENT, and
     * answer with how many that was.
     *
     * <p>The body is new. This used to return an empty 200, so "the register
     * closed and marked 31 absent" and "the register closed and marked nobody,
     * because the roster never arrived" looked identical to the caller. The
     * whole session is one transaction, so the count is what was committed.
     */
    @PostMapping("/mark-absentees")
    public ResponseEntity<Map<String, Object>> markAbsentees(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam Long classroomId,
            @RequestParam Long subjectId,
            @RequestParam(required = false) String date,
            @RequestParam List<Long> roster) {

        access.requireCanRecord(principal);

        LocalDate attendanceDate = parseDate(date);
        int marked = attendanceService.markAbsenteesForSession(
                roster, classroomId, subjectId, attendanceDate);

        return ResponseEntity.ok(Map.of(
                "classroomId", classroomId,
                "subjectId", subjectId,
                "date", attendanceDate.toString(),
                "marked", marked));
    }

    /**
     * Full attendance history for a student, most recent first.
     *
     * <p>Staff may ask for anyone. A student account may ask only for the
     * roster row that carries its own email address; every other id is a 403,
     * whether or not that id exists.
     *
     * <p>Answers with {@link RegisterEntry} rather than the stored rows: the
     * ids are all still there under the same names, with the student, subject
     * and room they refer to named alongside them.
     */
    @GetMapping("/student/{studentId}")
    public List<RegisterEntry> getStudentHistory(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long studentId) {

        access.requireCanReadStudent(principal, studentId);
        return register.describe(attendanceHistory.getFullHistory(studentId));
    }

    /**
     * The signed-in student's own history, without them needing to know their
     * roster id. Staff have no "own attendance", so this is a 403 for them -
     * they have {@code /attendance/student/{id}}.
     */
    @GetMapping("/me")
    public List<RegisterEntry> getOwnHistory(@AuthenticationPrincipal CustomUserDetails principal) {
        Long own = access.requireOwnStudentId(principal);
        return register.describe(attendanceHistory.getFullHistory(own));
    }

    /**
     * The class register (who was present, absent or late) for one session.
     * Staff only - a register names every student in the room, so there is no
     * subset of it that belongs to one student.
     */
    @GetMapping("/classroom/{classroomId}/subject/{subjectId}/date/{date}")
    public List<RegisterEntry> getClassRegister(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long classroomId,
            @PathVariable Long subjectId,
            @PathVariable String date) {

        access.requireCanReadRegister(principal);
        return register.describe(
                attendanceHistory.getClassRegister(classroomId, subjectId, LocalDate.parse(date)));
    }

    /** Attendance percentage summary for a student in one subject. */
    @GetMapping("/summary/student/{studentId}/subject/{subjectId}")
    public Object getSummary(
            @AuthenticationPrincipal CustomUserDetails principal,
            @PathVariable Long studentId,
            @PathVariable Long subjectId,
            @RequestParam String startDate,
            @RequestParam String endDate) {

        access.requireCanReadStudent(principal, studentId);
        return attendanceService.getAttendanceSummary(
                studentId, subjectId, LocalDate.parse(startDate), LocalDate.parse(endDate));
    }

    /** Excel export of one class session's daily register. Staff only. */
    @GetMapping("/export/excel/daily")
    public ResponseEntity<ByteArrayResource> exportDailyExcel(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam Long classroomId,
            @RequestParam Long subjectId,
            @RequestParam(required = false) String date,
            @RequestParam List<Long> roster) throws IOException {

        access.requireCanReadRegister(principal);

        DailyRegister daily = dailyRegister(classroomId, subjectId, parseDate(date), roster);

        Path tempFile = Files.createTempFile("attendance-daily-", ".xlsx");
        try {
            excelExport.exportClassDailyReport(
                    daily.report(), daily.session(), daily.entries(), tempFile.toString());
            return fileResponse(tempFile, "attendance-daily.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /** PDF export of one class session's daily register. Staff only. */
    @GetMapping("/export/pdf/daily")
    public ResponseEntity<ByteArrayResource> exportDailyPdf(
            @AuthenticationPrincipal CustomUserDetails principal,
            @RequestParam Long classroomId,
            @RequestParam Long subjectId,
            @RequestParam(required = false) String date,
            @RequestParam List<Long> roster) throws IOException {

        access.requireCanReadRegister(principal);

        DailyRegister daily = dailyRegister(classroomId, subjectId, parseDate(date), roster);

        Path tempFile = Files.createTempFile("attendance-daily-", ".pdf");
        try {
            pdfExport.exportClassDailyReport(
                    daily.report(), daily.session(), daily.entries(), tempFile.toString());
            return fileResponse(tempFile, "attendance-daily.pdf", MediaType.APPLICATION_PDF_VALUE);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    /** The three things both daily exports write, built once for either format. */
    private record DailyRegister(ClassDailyReport report, String session, List<RegisterEntry> entries) {
    }

    /**
     * Builds the counted summary, the session title and one line per student.
     *
     * <p>The session's rows are read twice here - once by
     * {@code AttendanceReports} to count them and once by {@code RegisterView}
     * to describe them. Deliberate: the report is id-only because the dashboard
     * and the percentage counters consume it and neither wants names, and
     * widening it to carry rows out would push presentation into the reporting
     * layer. One more indexed read per exported file is the cheaper trade.
     */
    private DailyRegister dailyRegister(Long classroomId, Long subjectId,
                                        LocalDate day, List<Long> roster) {
        ClassDailyReport report = attendanceReports.generateClassDailyReport(
                classroomId, subjectId, day, roster);
        List<Attendance> rows = attendanceHistory.getClassRegister(classroomId, subjectId, day);
        List<RegisterEntry> entries = register.describeRoster(
                rows, roster, classroomId, subjectId, day);
        return new DailyRegister(report, register.describeSession(classroomId, subjectId), entries);
    }

    private ResponseEntity<ByteArrayResource> fileResponse(Path file,
                                                           String downloadName,
                                                           String contentType) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + downloadName + "\"")
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }

    private LocalDate parseDate(String date) {
        return (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
    }
}
