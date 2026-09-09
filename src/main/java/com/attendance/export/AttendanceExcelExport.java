package com.attendance.export;

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

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class AttendanceExcelExport {

    public void exportStudentSummaryReport(List<StudentSubjectReport> reports,
                                           String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Attendance Summary");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            String[] headers = {
                    "Student ID", "Subject ID", "Start Date", "End Date",
                    "Total Sessions", "Present", "Absent", "Late", "Excused", "Attendance %"
            };
            writeHeader(sheet.createRow(0), headers, headerStyle);

            int rowIndex = 1;
            for (StudentSubjectReport report : reports) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(report.getStudentId());
                row.createCell(1).setCellValue(report.getSubjectId());
                row.createCell(2).setCellValue(report.getStartDate().toString());
                row.createCell(3).setCellValue(report.getEndDate().toString());
                row.createCell(4).setCellValue(report.getTotalSessions());
                row.createCell(5).setCellValue(report.getPresentCount());
                row.createCell(6).setCellValue(report.getAbsentCount());
                row.createCell(7).setCellValue(report.getLateCount());
                row.createCell(8).setCellValue(report.getExcusedCount());

                Cell percentCell = row.createCell(9);
                percentCell.setCellValue(report.getAttendancePercentage() / 100.0);
                percentCell.setCellStyle(report.getAttendancePercentage() < 75.0
                        ? createWarningPercentStyle(workbook, percentStyle)
                        : percentStyle);
            }

            autoSizeColumns(sheet, headers.length);
            writeToFile(workbook, filePath);
        }
    }

    public void exportClassDailyReport(ClassDailyReport report, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Daily Register");
            CellStyle headerStyle = createHeaderStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(
                    "Class " + report.getClassroomId()
                            + " | Subject " + report.getSubjectId()
                            + " | Date: " + report.getDate());
            titleRow.getCell(0).setCellStyle(headerStyle);

            String[] headers = {"Total Students", "Present", "Absent", "Late", "Absent Student IDs"};
            writeHeader(sheet.createRow(1), headers, headerStyle);

            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue(report.getTotalStudents());
            row.createCell(1).setCellValue(report.getPresentCount());
            row.createCell(2).setCellValue(report.getAbsentCount());
            row.createCell(3).setCellValue(report.getLateCount());
            row.createCell(4).setCellValue(report.getAbsentStudentIds().toString());

            autoSizeColumns(sheet, headers.length);
            writeToFile(workbook, filePath);
        }
    }

    public void exportClassRangeReport(ClassRangeReport report, String filePath) throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Class Attendance Trend");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle percentStyle = createPercentStyle(workbook);

            Row titleRow = sheet.createRow(0);
            titleRow.createCell(0).setCellValue(
                    "Classroom " + report.getClassroomId()
                            + " | Subject " + report.getSubjectId()
                            + " | " + report.getStartDate() + " to " + report.getEndDate());
            titleRow.getCell(0).setCellStyle(headerStyle);

            String[] headers = {"Student ID", "Attendance %"};
            writeHeader(sheet.createRow(1), headers, headerStyle);

            int rowIndex = 2;
            for (Map.Entry<Long, Double> entry : report.getPerStudentPercentage().entrySet()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(entry.getKey());
                Cell percentCell = row.createCell(1);
                percentCell.setCellValue(entry.getValue() / 100.0);
                percentCell.setCellStyle(percentStyle);
            }

            Row averageRow = sheet.createRow(rowIndex + 1);
            averageRow.createCell(0).setCellValue("Class Average");
            Cell averageCell = averageRow.createCell(1);
            averageCell.setCellValue(report.getClassAveragePercentage() / 100.0);
            averageCell.setCellStyle(percentStyle);

            autoSizeColumns(sheet, 2);
            writeToFile(workbook, filePath);
        }
    }

    private void writeHeader(Row row, String[] headers, CellStyle headerStyle) {
        for (int i = 0; i < headers.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createPercentStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("0.00%"));
        return style;
    }

    private CellStyle createWarningPercentStyle(Workbook workbook, CellStyle percentStyle) {
        CellStyle warning = workbook.createCellStyle();
        warning.cloneStyleFrom(percentStyle);

        Font redFont = workbook.createFont();
        redFont.setColor(IndexedColors.RED.getIndex());
        redFont.setBold(true);
        warning.setFont(redFont);

        return warning;
    }

    private void autoSizeColumns(Sheet sheet, int columnCount) {
        for (int i = 0; i < columnCount; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private void writeToFile(Workbook workbook, String filePath) throws IOException {
        try (FileOutputStream out = new FileOutputStream(filePath)) {
            workbook.write(out);
        }
    }
}
