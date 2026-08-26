package com.attendance.export;

import com.attendance.report.AttendanceReports.ClassDailyReport;
import com.attendance.report.AttendanceReports.ClassRangeReport;
import com.attendance.report.AttendanceReports.StudentSubjectReport;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Service
public class AttendancePdfExport {

    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 20;
    private static final float PAGE_HEIGHT = PDRectangle.LETTER.getHeight();

    public void exportStudentSummaryReport(List<StudentSubjectReport> reports,
                                           String filePath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            y = writeText(contentStream, titleFont, 16, MARGIN, y, "Attendance Summary Report");
            y -= 10;

            String[] headers = {"Student", "Subject", "Sessions", "Present", "Absent", "Late", "Attendance %"};
            float[] colWidths = {70, 70, 65, 60, 60, 50, 90};

            y = writeTableRow(contentStream, headerFont, 10, MARGIN, y, headers, colWidths, true);

            for (StudentSubjectReport report : reports) {
                if (y < MARGIN + ROW_HEIGHT) {
                    contentStream.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = PAGE_HEIGHT - MARGIN;
                    y = writeTableRow(contentStream, headerFont, 10, MARGIN, y, headers, colWidths, true);
                }

                String[] row = {
                        String.valueOf(report.getStudentId()),
                        String.valueOf(report.getSubjectId()),
                        String.valueOf(report.getTotalSessions()),
                        String.valueOf(report.getPresentCount()),
                        String.valueOf(report.getAbsentCount()),
                        String.valueOf(report.getLateCount()),
                        String.format("%.2f%%", report.getAttendancePercentage())
                };

                if (report.getAttendancePercentage() < 75.0) {
                    contentStream.setNonStrokingColor(0.78f, 0.0f, 0.0f);
                }
                y = writeTableRow(contentStream, bodyFont, 10, MARGIN, y, row, colWidths, false);
                contentStream.setNonStrokingColor(0.0f, 0.0f, 0.0f);
            }

            contentStream.close();
            document.save(filePath);
        }
    }

    public void exportClassDailyReport(ClassDailyReport report, String filePath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            y = writeText(contentStream, titleFont, 14, MARGIN, y,
                    "Daily Attendance Register - Class " + report.getClassroomId()
                            + " | Subject " + report.getSubjectId()
                            + " | " + report.getDate());
            y -= 10;

            String[] headers = {"Total Students", "Present", "Absent", "Late"};
            float[] colWidths = {100, 80, 80, 80};

            y = writeTableRow(contentStream, headerFont, 10, MARGIN, y, headers, colWidths, true);
            y = writeTableRow(contentStream, bodyFont, 10, MARGIN, y, new String[]{
                    String.valueOf(report.getTotalStudents()),
                    String.valueOf(report.getPresentCount()),
                    String.valueOf(report.getAbsentCount()),
                    String.valueOf(report.getLateCount())
            }, colWidths, false);

            y -= 10;
            writeText(contentStream, bodyFont, 10, MARGIN, y,
                    "Absent Student IDs: " + report.getAbsentStudentIds());

            contentStream.close();
            document.save(filePath);
        }
    }

    public void exportClassRangeReport(ClassRangeReport report, String filePath) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            document.addPage(page);

            PDType1Font titleFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDType1Font bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            float y = PAGE_HEIGHT - MARGIN;

            y = writeText(contentStream, titleFont, 14, MARGIN, y,
                    "Attendance Trend - Class " + report.getClassroomId()
                            + " | Subject " + report.getSubjectId());
            y = writeText(contentStream, bodyFont, 10, MARGIN, y,
                    report.getStartDate() + " to " + report.getEndDate());
            y -= 10;

            String[] headers = {"Student ID", "Attendance %"};
            float[] colWidths = {150, 120};

            y = writeTableRow(contentStream, headerFont, 10, MARGIN, y, headers, colWidths, true);

            for (Map.Entry<Long, Double> entry : report.getPerStudentPercentage().entrySet()) {
                if (y < MARGIN + ROW_HEIGHT) {
                    contentStream.close();
                    page = new PDPage(PDRectangle.LETTER);
                    document.addPage(page);
                    contentStream = new PDPageContentStream(document, page);
                    y = PAGE_HEIGHT - MARGIN;
                    y = writeTableRow(contentStream, headerFont, 10, MARGIN, y, headers, colWidths, true);
                }

                String[] row = {String.valueOf(entry.getKey()), String.format("%.2f%%", entry.getValue())};
                if (entry.getValue() < 75.0) {
                    contentStream.setNonStrokingColor(0.78f, 0.0f, 0.0f);
                }
                y = writeTableRow(contentStream, bodyFont, 10, MARGIN, y, row, colWidths, false);
                contentStream.setNonStrokingColor(0.0f, 0.0f, 0.0f);
            }

            y -= 10;
            writeText(contentStream, headerFont, 11, MARGIN, y,
                    String.format("Class Average: %.2f%%", report.getClassAveragePercentage()));

            contentStream.close();
            document.save(filePath);
        }
    }

    private float writeText(PDPageContentStream contentStream, PDType1Font font, float fontSize,
                            float x, float y, String text) throws IOException {
        contentStream.beginText();
        contentStream.setFont(font, fontSize);
        contentStream.newLineAtOffset(x, y);
        contentStream.showText(text);
        contentStream.endText();
        return y - (fontSize + 8);
    }

    private float writeTableRow(PDPageContentStream contentStream, PDType1Font font, float fontSize,
                                float x, float y, String[] values, float[] colWidths,
                                boolean header) throws IOException {
        float cursorX = x;
        for (int i = 0; i < values.length; i++) {
            contentStream.beginText();
            contentStream.setFont(font, fontSize);
            contentStream.newLineAtOffset(cursorX, y);
            contentStream.showText(values[i] != null ? values[i] : "");
            contentStream.endText();
            cursorX += colWidths[i];
        }

        if (header) {
            contentStream.setLineWidth(0.5f);
            contentStream.moveTo(x, y - 4);
            contentStream.lineTo(cursorX, y - 4);
            contentStream.stroke();
        }

        return y - ROW_HEIGHT;
    }
}
