package com.attendance.export;

import com.attendance.dto.RegisterEntry;
import com.attendance.entity.Status;
import com.attendance.report.AttendanceReports.ClassDailyReport;
import com.attendance.report.AttendanceReports.ClassRangeReport;
import com.attendance.report.AttendanceReports.StudentSubjectReport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Excel exports of the attendance reports.
 *
 * <p>Styles are now created once per workbook by {@link Styles} instead of per
 * row. The old code called {@code createWarningPercentStyle(workbook, ..)}
 * inside the row loop, so every student below the threshold added a fresh
 * {@code CellStyle} and a fresh {@code Font} to the workbook. XSSF has a hard
 * ceiling of 64,000 cell styles and 32,767 fonts, and it does not degrade
 * gracefully: past the limit {@code createCellStyle} throws
 * {@code IllegalStateException} and the export fails outright. A single term's
 * summary for a large school is well inside that range, which is why this was
 * a slow leak rather than an obvious break - the workbook simply grew, then one
 * day stopped being writable.
 *
 * <p>The styles are also identical row to row, so allocating one per row was
 * buying nothing at any size.
 */
@Service
public class AttendanceExcelExport {

    /** A percentage below this is flagged. Matches the PDF export. */
    private static final double WARN_BELOW = 75.0;

    /**
     * The one status that needs no emphasis on a register. Taken from the enum
     * rather than typed as "PRESENT", because {@link RegisterEntry} carries the
     * status as the enum's own name and the two must not drift apart.
     */
    private static final String PRESENT = Status.PRESENT.name();

    public void exportStudentSummaryReport(List<StudentSubjectReport> reports,
                                           String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Summary");
            Styles styles = new Styles(workbook);

            String[] headers = {
                    "Student ID", "Subject ID", "Start Date", "End Date",
                    "Total Sessions", "Present", "Absent", "Late", "Excused", "Attendance %"
            };
            writeHeader(sheet.createRow(0), headers, styles.header);

            int rowIndex = 1;
            for (StudentSubjectReport report : reports) {
                Row row = sheet.createRow(rowIndex++);
                number(row, 0, report.getStudentId());
                number(row, 1, report.getSubjectId());
                row.createCell(2).setCellValue(text(report.getStartDate()));
                row.createCell(3).setCellValue(text(report.getEndDate()));
                row.createCell(4).setCellValue(report.getTotalSessions());
                row.createCell(5).setCellValue(report.getPresentCount());
                row.createCell(6).setCellValue(report.getAbsentCount());
                row.createCell(7).setCellValue(report.getLateCount());
                row.createCell(8).setCellValue(report.getExcusedCount());

                double percentage = report.getAttendancePercentage();
                Cell percentCell = row.createCell(9);
                percentCell.setCellValue(percentage / 100.0);
                percentCell.setCellStyle(styles.forPercentage(percentage));
            }

            autoSizeColumns(sheet, headers.length);
            writeToFile(workbook, filePath);
        }
    }

    /**
     * The daily register: the counted summary, then one row per student.
     *
     * <p>The per-student rows are new. This sheet used to be five cells - four
     * counts and a comma-separated list of absent ids - so the answer to "who
     * missed the lecture" was a row of numbers that had to be looked up one at
     * a time somewhere else, and the students who attended were not on the sheet
     * at all. A register that cannot be read as a register is a summary.
     */
    public void exportClassDailyReport(ClassDailyReport report,
                                       String sessionTitle,
                                       List<RegisterEntry> entries,
                                       String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Daily Register");
            Styles styles = new Styles(workbook);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(sessionTitle + " | Date: " + report.getDate());
            titleCell.setCellStyle(styles.header);

            String[] totals = {"Total Students", "Present", "Absent", "Late"};
            writeHeader(sheet.createRow(1), totals, styles.header);

            Row summary = sheet.createRow(2);
            summary.createCell(0).setCellValue(report.getTotalStudents());
            summary.createCell(1).setCellValue(report.getPresentCount());
            summary.createCell(2).setCellValue(report.getAbsentCount());
            summary.createCell(3).setCellValue(report.getLateCount());

            // Row 3 left empty, so the register below does not read as a fifth
            // and sixth column of the totals above it.
            String[] headers = {
                    "Student ID", "Student", "Section", "Status", "Time", "Marked by", "Confidence"
            };
            writeHeader(sheet.createRow(4), headers, styles.header);

            int rowIndex = 5;
            for (RegisterEntry entry : entries) {
                Row row = sheet.createRow(rowIndex++);
                number(row, 0, entry.getStudentId());
                row.createCell(1).setCellValue(entry.getStudentName());
                row.createCell(2).setCellValue(entry.getSection());

                Cell status = row.createCell(3);
                status.setCellValue(entry.getStatus());
                // Anything other than present is the exception a register is
                // read for, so it carries the weight. Bold, not colour: the
                // sheet gets printed.
                if (!PRESENT.equals(entry.getStatus())) {
                    status.setCellStyle(styles.strong);
                }

                row.createCell(4).setCellValue(entry.getAttendanceTime());
                row.createCell(5).setCellValue(entry.getMarkedBy());

                // Plain percent, never the flagged style: this is how sure the
                // camera was, and borrowing the low-attendance flag here would
                // make a hesitant match look like a truancy problem.
                Double confidence = entry.getConfidenceScore();
                if (confidence != null) {
                    Cell cell = row.createCell(6);
                    cell.setCellValue(confidence);
                    cell.setCellStyle(styles.percent);
                }
            }

            autoSizeColumns(sheet, headers.length);
            writeToFile(workbook, filePath);
        }
    }

    public void exportClassRangeReport(ClassRangeReport report, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Class Attendance Trend");
            Styles styles = new Styles(workbook);

            Row titleRow = sheet.createRow(0);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Classroom " + report.getClassroomId()
                    + " | Subject " + report.getSubjectId()
                    + " | " + report.getStartDate() + " to " + report.getEndDate());
            titleCell.setCellStyle(styles.header);

            String[] headers = {"Student ID", "Attendance %"};
            writeHeader(sheet.createRow(1), headers, styles.header);

            int rowIndex = 2;
            for (Map.Entry<Long, Double> entry : report.getPerStudentPercentage().entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                number(row, 0, entry.getKey());
                double percentage = entry.getValue() == null ? 0.0 : entry.getValue();
                Cell percentCell = row.createCell(1);
                percentCell.setCellValue(percentage / 100.0);
                // Was plain percentStyle here while the summary sheet
                // highlighted the same figures. Same rule, same colour.
                percentCell.setCellStyle(styles.forPercentage(percentage));
            }

            // One blank row before the total, so it does not read as another
            // student.
            Row averageRow = sheet.createRow(rowIndex + 1);
            Cell label = averageRow.createCell(0);
            label.setCellValue("Class Average");
            label.setCellStyle(styles.header);
            Cell averageCell = averageRow.createCell(1);
            averageCell.setCellValue(report.getClassAveragePercentage() / 100.0);
            averageCell.setCellStyle(styles.percent);

            autoSizeColumns(sheet, headers.length);
            writeToFile(workbook, filePath);
        }
    }

    /**
     * Every style a sheet needs, created once.
     *
     * <p>A {@code CellStyle} belongs to the workbook, not to the cell, so the
     * same instance can be handed to every cell that looks the same - which is
     * the whole point: POI counts styles, not cells.
     */
    private static final class Styles {

        private final CellStyle header;
        private final CellStyle percent;
        private final CellStyle warningPercent;
        private final CellStyle strong;

        Styles(Workbook workbook) {
            DataFormat formats = workbook.createDataFormat();
            short percentFormat = formats.getFormat("0.00%");

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            // Bold and underlined rather than red. A register is printed, and
            // on a mono printer a red percentage is a grey percentage - which
            // is to say, no flag at all. Weight and a rule survive the
            // photocopier, and match the desktop client, which has no hue in
            // it either.
            Font warnFont = workbook.createFont();
            warnFont.setBold(true);
            warnFont.setUnderline(Font.U_SINGLE);

            header = workbook.createCellStyle();
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            percent = workbook.createCellStyle();
            percent.setDataFormat(percentFormat);

            // Bold text with no fill and no format, for the one cell in a
            // register row that should stop the eye.
            strong = workbook.createCellStyle();
            strong.setFont(boldFont);

            warningPercent = workbook.createCellStyle();
            warningPercent.setDataFormat(percentFormat);
            warningPercent.setFont(warnFont);
            // A second signal, so the flag survives a reader who is skimming
            // the column rather than reading each figure.
            warningPercent.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            warningPercent.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }

        /** The percentage style for this value: flagged below the threshold. */
        CellStyle forPercentage(double percentage) {
            return percentage < WARN_BELOW ? warningPercent : percent;
        }
    }

    private void writeHeader(Row row, String[] headers, CellStyle headerStyle) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    /**
     * A numeric cell that stays blank for a null id, rather than the {@code 0}
     * that {@code setCellValue(Long)} would have to invent. Autoboxing hid this
     * before: {@code setCellValue(report.getStudentId())} unboxes a
     * {@code Long}, so a null id was an NPE in the middle of a write.
     */
    private static void number(Row row, int column, Long value) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
    }

    private static String text(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    /**
     * Writes the workbook, creating the parent directory if it is missing.
     * {@code FileOutputStream} on a path whose directory does not exist fails
     * with {@code FileNotFoundException}, which reads as "no such file" and
     * sends the reader looking for the wrong problem.
     */
    private void writeToFile(Workbook workbook, String filePath) throws IOException {
        Path target = Paths.get(filePath).toAbsolutePath();
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        try (OutputStream out = Files.newOutputStream(target)) {
            workbook.write(out);
        }
    }
}
