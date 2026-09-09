package com.attendance.export;

import com.attendance.dto.RegisterEntry;
import com.attendance.entity.Status;
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

import java.io.Closeable;
import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PDF exports of the attendance reports.
 *
 * <p>The drawing was rewritten around a {@link Sheet} that owns the document,
 * the current page and the current content stream, because the previous
 * version had three copies of the same four defects:
 *
 * <ul>
 *   <li><b>The content stream leaked and the file could come out corrupt.</b>
 *       {@code PDPageContentStream} was created outside the
 *       try-with-resources, so any {@code IOException} while writing a row
 *       skipped every {@code contentStream.close()} and left
 *       {@code PDDocument.close()} to run with a stream still open. A page
 *       break made it worse: it closed the old stream and opened a new one, so
 *       a failure mid-table leaked whichever stream was current.</li>
 *   <li><b>Text outside WinAnsi threw.</b> The Standard 14 Helvetica is
 *       WinAnsi-encoded, and {@code showText} throws
 *       {@code IllegalArgumentException} - not {@code IOException}, so nothing
 *       declared it - for any character it cannot encode, and for {@code \n}
 *       and {@code \r} outright. One student named with a character outside
 *       Latin-1 would turn the whole export into a 500. Everything now goes
 *       through {@link #encodable}.</li>
 *   <li><b>Nothing was clipped to its column.</b> Values were written at a
 *       fixed offset per column with no width check, so a long value ran
 *       straight through the next one.</li>
 *   <li><b>Long lines ran off the page.</b> "Absent Student IDs: " plus the
 *       list was one {@code showText} call; a class with more than about a
 *       dozen absentees printed off the right edge. The trailing summary lines
 *       also had no page-break check, so on a full page they were drawn below
 *       the bottom margin - at a negative y, i.e. nowhere.</li>
 * </ul>
 */
@Service
public class AttendancePdfExport {

    private static final PDRectangle PAGE_SIZE = PDRectangle.LETTER;
    private static final float MARGIN = 50;
    private static final float ROW_HEIGHT = 20;
    private static final float PAGE_HEIGHT = PAGE_SIZE.getHeight();
    private static final float CONTENT_WIDTH = PAGE_SIZE.getWidth() - (2 * MARGIN);

    /** Gap kept clear at the right of every cell so clipped text does not touch the next column. */
    private static final float CELL_GUTTER = 6;

    /**
     * The one status a register does not need to draw attention to. Read from
     * the enum rather than typed, because {@link RegisterEntry} carries the
     * status as the enum's own name.
     */
    private static final String PRESENT = Status.PRESENT.name();

    public void exportStudentSummaryReport(List<StudentSubjectReport> reports,
                                           String filePath) throws IOException {
        String[] headers = {"Student", "Subject", "Sessions", "Present", "Absent", "Late", "Attendance %"};
        float[] colWidths = {70, 70, 65, 60, 60, 50, 90};

        try (Sheet sheet = new Sheet()) {
            sheet.heading("Attendance Summary Report", 16);
            sheet.gap(10);
            sheet.headerRow(headers, colWidths);

            for (StudentSubjectReport report : reports) {
                if (sheet.wouldOverflow(ROW_HEIGHT)) {
                    sheet.newPage();
                    sheet.headerRow(headers, colWidths);
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
                sheet.bodyRow(row, colWidths, report.getAttendancePercentage() < 75.0);
            }

            sheet.save(filePath);
        }
    }

    /**
     * The daily register: a counted summary, then one line per student.
     *
     * <p>The lines are new. This page used to be four counts and a wrapped
     * paragraph of absent ids, which is a document that reports how many people
     * missed a lecture without saying who - and the ids it did print named
     * nobody, so answering that question meant looking every number up in
     * another screen. The students who attended did not appear at all.
     */
    public void exportClassDailyReport(ClassDailyReport report,
                                       String sessionTitle,
                                       List<RegisterEntry> entries,
                                       String filePath) throws IOException {
        String[] totals = {"Total Students", "Present", "Absent", "Late"};
        float[] totalWidths = {100, 80, 80, 80};

        String[] headers = {"ID", "Student", "Section", "Status", "Time", "Marked by", "Confidence"};
        // Sums to exactly CONTENT_WIDTH, so the last column ends on the right
        // margin instead of past it.
        float[] colWidths = {38, 140, 50, 60, 58, 96, 70};

        try (Sheet sheet = new Sheet()) {
            sheet.heading("Daily Attendance Register - " + sessionTitle
                    + " | " + report.getDate(), 14);
            sheet.gap(10);

            sheet.headerRow(totals, totalWidths);
            sheet.bodyRow(new String[]{
                    String.valueOf(report.getTotalStudents()),
                    String.valueOf(report.getPresentCount()),
                    String.valueOf(report.getAbsentCount()),
                    String.valueOf(report.getLateCount())
            }, totalWidths, false);

            sheet.gap(10);
            sheet.headerRow(headers, colWidths);

            for (RegisterEntry entry : entries) {
                if (sheet.wouldOverflow(ROW_HEIGHT)) {
                    sheet.newPage();
                    sheet.headerRow(headers, colWidths);
                }
                String[] row = {
                        entry.getStudentId() == null ? "" : String.valueOf(entry.getStudentId()),
                        entry.getStudentName(),
                        entry.getSection(),
                        entry.getStatus(),
                        entry.getAttendanceTime(),
                        entry.getMarkedBy(),
                        percent(entry.getConfidenceScore())
                };
                // Bold for anything that is not a plain "present" - the lines a
                // register gets printed to find.
                sheet.bodyRow(row, colWidths, !PRESENT.equals(entry.getStatus()));
            }

            sheet.save(filePath);
        }
    }

    public void exportClassRangeReport(ClassRangeReport report, String filePath) throws IOException {
        String[] headers = {"Student ID", "Attendance %"};
        float[] colWidths = {150, 120};

        try (Sheet sheet = new Sheet()) {
            sheet.heading("Attendance Trend - Class " + report.getClassroomId()
                    + " | Subject " + report.getSubjectId(), 14);
            sheet.paragraph(report.getStartDate() + " to " + report.getEndDate(), 10, false);
            sheet.gap(10);
            sheet.headerRow(headers, colWidths);

            for (Map.Entry<Long, Double> entry : report.getPerStudentPercentage().entrySet()) {
                if (sheet.wouldOverflow(ROW_HEIGHT)) {
                    sheet.newPage();
                    sheet.headerRow(headers, colWidths);
                }
                String[] row = {
                        String.valueOf(entry.getKey()),
                        String.format("%.2f%%", entry.getValue())
                };
                sheet.bodyRow(row, colWidths, entry.getValue() < 75.0);
            }

            sheet.gap(10);
            sheet.paragraph(String.format("Class Average: %.2f%%",
                    report.getClassAveragePercentage()), 11, true);

            sheet.save(filePath);
        }
    }

    /**
     * A confidence score as whole percent, blank when there is none.
     *
     * <p>Blank rather than "0%": no score means nobody was matched by a camera -
     * a teacher marked the row by hand, or nobody marked it at all - and "0%"
     * would read as a recognition that failed.
     */
    private static String percent(Double score) {
        return score == null ? "" : Math.round(score * 100) + "%";
    }

    /**
     * A document being written, one page at a time.
     *
     * <p>The point of this class is that the content stream is never a local
     * variable in a caller. It is closed by {@link #close()} whatever happens,
     * including on the way out of a failed export, so there is no path that
     * saves or closes a {@code PDDocument} with a stream still open.
     */
    private static final class Sheet implements Closeable {

        private final PDType1Font bold =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        private final PDType1Font body =
                new PDType1Font(Standard14Fonts.FontName.HELVETICA);

        private final PDDocument document = new PDDocument();
        private PDPageContentStream stream;
        private float y;

        private Sheet() throws IOException {
            newPage();
        }

        /** Starts a fresh page, closing the previous page's stream first. */
        void newPage() throws IOException {
            closeStream();
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_HEIGHT - MARGIN;
        }

        /** True when the next {@code height} points would fall below the bottom margin. */
        boolean wouldOverflow(float height) {
            return y - height < MARGIN;
        }

        void gap(float points) {
            y -= points;
        }

        void heading(String text, float fontSize) throws IOException {
            paragraph(text, fontSize, true);
        }

        /**
         * One run of text, wrapped to the content width and broken across
         * pages if it does not fit on this one.
         */
        void paragraph(String text, float fontSize, boolean strong) throws IOException {
            PDType1Font font = strong ? bold : body;
            for (String line : wrap(encodable(text), font, fontSize, CONTENT_WIDTH)) {
                float lineHeight = fontSize + 8;
                if (wouldOverflow(lineHeight)) {
                    newPage();
                }
                write(font, fontSize, MARGIN, y, line);
                y -= lineHeight;
            }
        }

        /** A table header row, underlined, with the rule drawn under the used width only. */
        void headerRow(String[] values, float[] colWidths) throws IOException {
            if (wouldOverflow(ROW_HEIGHT)) {
                newPage();
            }
            float end = row(bold, 10, values, colWidths);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y - 4);
            stream.lineTo(end, y - 4);
            stream.stroke();
            y -= ROW_HEIGHT;
        }

        /**
         * A table body row. {@code warn} sets it in the bold face for a
         * percentage under the threshold.
         *
         * <p>It used to be set in red instead. A register is a printed
         * document, and on a mono printer or a photocopy a red figure is
         * simply a grey figure - so the flag that mattered most was the one
         * most likely to be lost. Weight survives both, and matches the
         * desktop client, which now carries no hue at all.
         */
        void bodyRow(String[] values, float[] colWidths, boolean warn) throws IOException {
            if (wouldOverflow(ROW_HEIGHT)) {
                newPage();
            }
            row(warn ? bold : body, 10, values, colWidths);
            y -= ROW_HEIGHT;
        }

        /** Draws the cells of one row and returns the x where the row ends. */
        private float row(PDType1Font font, float fontSize,
                          String[] values, float[] colWidths) throws IOException {
            float cursorX = MARGIN;
            for (int i = 0; i < values.length; i++) {
                float width = i < colWidths.length ? colWidths[i] : 0;
                String text = clip(encodable(values[i]), font, fontSize, width - CELL_GUTTER);
                if (!text.isEmpty()) {
                    write(font, fontSize, cursorX, y, text);
                }
                cursorX += width;
            }
            return cursorX;
        }

        private void write(PDType1Font font, float fontSize, float x, float baseline, String text)
                throws IOException {
            stream.beginText();
            stream.setFont(font, fontSize);
            stream.newLineAtOffset(x, baseline);
            stream.showText(text);
            stream.endText();
        }

        void save(String filePath) throws IOException {
            // The stream has to be closed before the save, not after: PDFBox
            // writes the page's content from the stream's buffer, and an open
            // stream at save time is how the old code produced truncated files.
            closeStream();
            document.save(filePath);
        }

        private void closeStream() throws IOException {
            if (stream != null) {
                PDPageContentStream open = stream;
                stream = null;
                open.close();
            }
        }

        @Override
        public void close() throws IOException {
            try {
                closeStream();
            } finally {
                document.close();
            }
        }
    }

    /**
     * The same text, guaranteed to survive {@code showText}.
     *
     * <p>{@code showText} on a Standard 14 font throws
     * {@code IllegalArgumentException} for any character WinAnsi cannot
     * encode, and for {@code \n} and {@code \r} whatever the font. It is an
     * unchecked exception nobody declared, so a single unusual character in a
     * name turned an export into a 500 with no clue as to why.
     *
     * <p>Accented letters are decomposed rather than discarded, because "Zoe"
     * is a better rendering of a name spelled with a diaeresis than "Zo?e" is.
     * Only a character with no Latin-1 form at all becomes a question mark.
     * Losing an accent is a cosmetic defect; throwing loses the whole report.
     */
    private static String encodable(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\n':
                case '\r':
                case '\t':
                    out.append(' ');
                    continue;
                // The literals below are written as escapes on purpose: this
                // file stays ASCII, so a build with a different -encoding
                // cannot silently change which characters these cases match.
                case '\u2018':  // left single quote
                case '\u2019':  // right single quote / apostrophe
                case '\u201a':  // single low quote
                    out.append('\'');
                    continue;
                case '\u201c':  // left double quote
                case '\u201d':  // right double quote
                case '\u201e':  // double low quote
                    out.append('"');
                    continue;
                case '\u2013':  // en dash
                case '\u2014':  // em dash
                case '\u2212':  // minus sign
                    out.append('-');
                    continue;
                case '\u2026':  // ellipsis
                    out.append("...");
                    continue;
                case '\u20ac':  // euro sign
                    out.append("EUR");
                    continue;
                default:
                    break;
            }
            if (c < 0x20 || c == 0x7F || (c > 0x7F && c < 0xA0)) {
                // Control characters, and the 0x80-0x9F block where WinAnsi
                // has holes. Dropped rather than substituted: they are not
                // meant to be visible in the first place.
                continue;
            }
            if (c <= 0xFF) {
                out.append(c);
                continue;
            }
            out.append(latin1Fallback(c));
        }
        return out.toString();
    }

    /**
     * A Latin-1 stand-in for one character outside it, or {@code "?"}.
     *
     * <p>NFD splits a precomposed letter into a base letter plus its combining
     * marks, so dropping the marks leaves the base letter. It does nothing for
     * a character that is not a decomposable letter, which is exactly when the
     * question mark is the honest answer.
     */
    private static String latin1Fallback(char c) {
        String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
        StringBuilder out = new StringBuilder(1);
        for (int i = 0; i < decomposed.length(); i++) {
            char part = decomposed.charAt(i);
            if (part >= 0x20 && part <= 0xFF && !(part > 0x7F && part < 0xA0)) {
                out.append(part);
            }
        }
        return out.length() > 0 ? out.toString() : "?";
    }

    /** The width of {@code text} in points at {@code fontSize}. */
    private static float widthOf(String text, PDType1Font font, float fontSize) throws IOException {
        return font.getStringWidth(text) / 1000f * fontSize;
    }

    /**
     * {@code text} shortened until it fits {@code maxWidth}, with an ellipsis
     * to show that it was shortened.
     *
     * <p>Cells used to be written at a fixed offset with no width check at all,
     * so a long value ran straight through the columns to its right and the
     * table became unreadable rather than merely truncated.
     */
    private static String clip(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        if (text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (widthOf(text, font, fontSize) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        if (widthOf(suffix, font, fontSize) > maxWidth) {
            return "";
        }
        int end = text.length();
        while (end > 0) {
            end--;
            String candidate = text.substring(0, end) + suffix;
            if (widthOf(candidate, font, fontSize) <= maxWidth) {
                return candidate;
            }
        }
        return suffix;
    }

    /**
     * {@code text} broken into lines that each fit {@code maxWidth}.
     *
     * <p>Breaks at spaces where it can and mid-word where it cannot, so a
     * single unbroken run - a long comma-free id list, say - is still confined
     * to the page instead of continuing past the right margin.
     */
    private static List<String> wrap(String text, PDType1Font font, float fontSize, float maxWidth)
            throws IOException {
        List<String> lines = new ArrayList<>();
        if (text.isEmpty()) {
            lines.add("");
            return lines;
        }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (word.isEmpty()) {
                continue;
            }
            String candidate = line.length() == 0 ? word : line + " " + word;
            if (widthOf(candidate, font, fontSize) <= maxWidth) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (line.length() > 0) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // The word alone may still be too wide, so cut it into pieces that
            // fit rather than emitting one line that overflows. All but the
            // last piece are complete lines; the last one stays open so the
            // next word can join it.
            List<String> pieces = hardBreak(word, font, fontSize, maxWidth);
            for (int i = 0; i < pieces.size() - 1; i++) {
                lines.add(pieces.get(i));
            }
            line.append(pieces.get(pieces.size() - 1));
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            lines.add("");
        }
        return lines;
    }

    private static List<String> hardBreak(String word, PDType1Font font, float fontSize,
                                          float maxWidth) throws IOException {
        List<String> pieces = new ArrayList<>();
        StringBuilder piece = new StringBuilder();
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            if (piece.length() > 0 && widthOf(piece.toString() + c, font, fontSize) > maxWidth) {
                pieces.add(piece.toString());
                piece.setLength(0);
            }
            piece.append(c);
        }
        if (piece.length() > 0) {
            pieces.add(piece.toString());
        }
        return pieces;
    }
}
