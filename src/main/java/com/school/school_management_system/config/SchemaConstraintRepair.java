package com.school.school_management_system.config;

import com.school.school_management_system.entity.Student;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings a check constraint that Hibernate wrote once and will never revisit
 * back in line with the entity it was generated from.
 *
 * <p>The failure this exists for took the whole backend down at start-up.
 * {@code Student.age} used to be annotated {@code @Min(3)}, and Bean
 * Validation constraints are exported into the DDL, so every database created
 * while that annotation was in place carries
 * {@code CHECK ("AGE" >= 3 AND "AGE" <= 120)} on {@code students}. The
 * annotation now allows 0, because 0 is how the roster records an age nobody
 * has supplied - which is the only honest value for a row created
 * automatically for a new sign-up, where there is nobody to ask.
 *
 * <p>{@code spring.jpa.hibernate.ddl-auto=update} adds missing tables,
 * columns and unique keys. It does not touch a check constraint that already
 * exists, and has no way to know this one no longer matches the annotation it
 * came from. So the entity said 0 was allowed, the database said the floor was
 * 3, and nothing reconciled them: {@link DataBootstrap} tried to write a
 * roster row with {@code age = 0} and got back
 * {@code Check constraint violation: "CONSTRAINT_9E: "} - a name H2 invents
 * and reports with an empty message, so the log could not even say which rule
 * had been broken.
 *
 * <p>The bounds are read out of {@link Student} rather than repeated here, and
 * a stale constraint is recognised by comparing the numbers in the recorded
 * clause against them. That way this corrects itself if the bounds change
 * again, and leaves a correct constraint alone - including the one Hibernate
 * writes for itself on a fresh database, which must not be fought over.
 *
 * <p>Like {@link LegacyStudentTableMigration}, this can never fail start-up.
 * Every problem is logged and the application carries on: a missing age check
 * is a far smaller problem than a backend that will not boot, and Bean
 * Validation still refuses a bad age before it reaches the table.
 */
@Component
@Order(5)
public class SchemaConstraintRepair implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchemaConstraintRepair.class);

    private static final String TABLE = "STUDENTS";
    private static final String COLUMN = "AGE";

    /** The name this class gives its own work, so a later run recognises it. */
    private static final String CHECK_NAME = "CK_STUDENTS_AGE";

    /**
     * Every check constraint on one table, with the text of the rule. H2 keeps
     * the two halves apart in the standard views: the kind of constraint and
     * the table it belongs to are in {@code TABLE_CONSTRAINTS}, the clause
     * itself is in {@code CHECK_CONSTRAINTS}.
     */
    private static final String CHECKS_ON_TABLE =
            "SELECT cc.CONSTRAINT_NAME, cc.CHECK_CLAUSE"
            + " FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc"
            + " JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc"
            + " ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA"
            + " AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
            + " WHERE tc.TABLE_SCHEMA = SCHEMA()"
            + " AND UPPER(tc.TABLE_NAME) = ?"
            + " AND tc.CONSTRAINT_TYPE = 'CHECK'";

    /** Whole word, so a rule about a different column is not mistaken for this one. */
    private static final Pattern MENTIONS_COLUMN = Pattern.compile("\\b" + COLUMN + "\\b");

    private static final Pattern INTEGER_LITERAL = Pattern.compile("-?\\d+");

    /**
     * Constraint names come back out of the catalogue rather than from anyone
     * outside, but they are concatenated into DDL, so a name that is not a
     * plain identifier is reported and left alone instead of quoted and hoped
     * for.
     */
    private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z_0-9$]*");

    /** Enough of a database message to identify it, without a wall of SQL in the log. */
    private static final int MESSAGE_LIMIT = 240;

    private final JdbcTemplate jdbc;

    public SchemaConstraintRepair(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            alignAgeCheck();
        } catch (Exception e) {
            // Reading the catalogue is the one step that can fail without
            // having changed anything, and a database that does not expose the
            // standard views is not necessarily broken - so this is a note
            // rather than a warning, and nothing further is attempted.
            log.info("Age check constraint not inspected ({}). Continuing.", brief(e.getMessage()));
        }
    }

    /**
     * Drops any age check on the roster table that disagrees with the entity,
     * and installs the right one if none of them was already correct.
     */
    private void alignAgeCheck() {
        List<Map<String, Object>> recorded = jdbc.queryForList(CHECKS_ON_TABLE, TABLE);
        List<String> dropped = new ArrayList<>();
        boolean correctOneExists = false;

        for (Map<String, Object> row : recorded) {
            String name = text(row.get("CONSTRAINT_NAME"));
            String clause = text(row.get("CHECK_CLAUSE"));

            if (!MENTIONS_COLUMN.matcher(clause.toUpperCase(Locale.ROOT)).find()) {
                continue;
            }
            List<Integer> numbers = integersIn(clause);
            if (numbers.isEmpty()) {
                // Something about age that is not a range - a NOT NULL
                // rendered as a check, say. Not this class's business.
                continue;
            }
            if (matchesTheEntity(numbers)) {
                correctOneExists = true;
                continue;
            }
            if (!PLAIN_IDENTIFIER.matcher(name).matches()) {
                log.warn("Leaving the check constraint named '{}' on '{}' alone: that name is not a plain"
                        + " identifier, so it cannot be dropped safely from here. Drop it by hand.",
                        name, TABLE);
                continue;
            }
            jdbc.execute("ALTER TABLE \"" + TABLE + "\" DROP CONSTRAINT \"" + name + "\"");
            dropped.add(name + " (" + brief(clause) + ")");
        }

        if (!dropped.isEmpty()) {
            log.warn("Dropped {} check constraint(s) on '{}' that no longer matched the entity's age range"
                    + " of {}..{}: {}. An older version of the entity put them there and"
                    + " ddl-auto=update never revisits one.",
                    dropped.size(), TABLE, Student.AGE_MIN, Student.AGE_MAX, String.join("; ", dropped));
        }
        if (!correctOneExists) {
            install();
        }
    }

    /**
     * Writes the constraint the entity implies. Named, unlike the one
     * Hibernate generates, so the log can refer to it and a person can find it
     * again.
     */
    private void install() {
        String ddl = "ALTER TABLE \"" + TABLE + "\" ADD CONSTRAINT \"" + CHECK_NAME + "\""
                + " CHECK (\"" + COLUMN + "\" >= " + Student.AGE_MIN
                + " AND \"" + COLUMN + "\" <= " + Student.AGE_MAX + ")";
        try {
            jdbc.execute(ddl);
            log.info("Added check constraint {} on '{}': age {}..{}.",
                    CHECK_NAME, TABLE, Student.AGE_MIN, Student.AGE_MAX);
        } catch (Exception e) {
            log.warn("Could not add the age check constraint on '{}' ({}). The application is running and"
                    + " every write still goes through Bean Validation, which enforces {}..{}; the"
                    + " database itself will not. A row already outside that range is the usual cause.",
                    TABLE, brief(e.getMessage()), Student.AGE_MIN, Student.AGE_MAX);
        }
    }

    /**
     * True when the clause is exactly the entity's range: two numbers, one at
     * each end. Order-insensitive on purpose - H2 rewrites what Hibernate
     * emitted, and nothing promises which way round it comes back.
     */
    private static boolean matchesTheEntity(List<Integer> numbers) {
        return numbers.size() == 2
                && numbers.contains(Student.AGE_MIN)
                && numbers.contains(Student.AGE_MAX);
    }

    /** Every integer literal in the clause, in the order they appear. */
    private static List<Integer> integersIn(String clause) {
        List<Integer> found = new ArrayList<>();
        Matcher matcher = INTEGER_LITERAL.matcher(clause);
        while (matcher.find()) {
            try {
                found.add(Integer.valueOf(matcher.group()));
            } catch (NumberFormatException e) {
                // Too large to be an age bound. Left out, which makes the
                // count disagree with the entity and so treats the whole
                // clause as one to replace - the safe direction.
            }
        }
        return found;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /** One line, clipped. Recorded clauses and H2 errors both carry newlines. */
    private static String brief(String value) {
        if (value == null) {
            return "no detail";
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= MESSAGE_LIMIT ? flat : flat.substring(0, MESSAGE_LIMIT) + "...";
    }
}
