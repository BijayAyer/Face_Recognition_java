package com.school.school_management_system.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time, idempotent data move for databases created before the
 * {@code Student} entity pinned its table name.
 *
 * <p>{@code Student} used to rely on the JPA default table name, which
 * produced the singular {@code student} while every other table in the
 * schema is plural. Pinning it to {@code students} is the right fix, but on
 * an existing database Hibernate's {@code ddl-auto=update} would simply
 * create a new empty {@code students} table and leave the rows stranded in
 * {@code student} with no error and no visible cause.
 *
 * <p>So: if {@code students} is empty and a legacy {@code student} table
 * still holds rows, copy them across (ids included) and rename the old
 * table to {@code student_legacy_backup} rather than dropping it. Runs
 * before {@link DataBootstrap} and can never fail startup - any problem is
 * logged and the app continues.
 */
@Component
@Order(10)
public class LegacyStudentTableMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LegacyStudentTableMigration.class);

    private static final String LEGACY_TABLE = "STUDENT";
    private static final String CURRENT_TABLE = "STUDENTS";
    private static final String BACKUP_TABLE = "STUDENT_LEGACY_BACKUP";

    private final JdbcTemplate jdbcTemplate;

    public LegacyStudentTableMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            migrate();
        } catch (Exception e) {
            log.warn("Legacy student table check skipped: {}", e.getMessage());
        }
    }

    private void migrate() {
        if (!tableExists(LEGACY_TABLE) || !tableExists(CURRENT_TABLE)) {
            return;
        }
        if (tableExists(BACKUP_TABLE)) {
            // Already migrated on a previous start.
            return;
        }

        long legacyRows = countRows(LEGACY_TABLE);
        long currentRows = countRows(CURRENT_TABLE);

        if (legacyRows == 0) {
            log.info("Legacy '{}' table is empty; renaming it to '{}'.", LEGACY_TABLE, BACKUP_TABLE);
            jdbcTemplate.execute("ALTER TABLE " + LEGACY_TABLE + " RENAME TO " + BACKUP_TABLE);
            return;
        }

        if (currentRows > 0) {
            log.warn("Both '{}' ({} rows) and '{}' ({} rows) contain data. "
                            + "Leaving them alone - merge them by hand to avoid clobbering anything.",
                    LEGACY_TABLE, legacyRows, CURRENT_TABLE, currentRows);
            return;
        }

        log.info("Copying {} row(s) from legacy '{}' into '{}'.", legacyRows, LEGACY_TABLE, CURRENT_TABLE);
        jdbcTemplate.update("INSERT INTO " + CURRENT_TABLE + " (id, name, email, age) "
                + "SELECT id, name, email, age FROM " + LEGACY_TABLE);
        jdbcTemplate.execute("ALTER TABLE " + LEGACY_TABLE + " RENAME TO " + BACKUP_TABLE);
        log.info("Migration done. The old table is preserved as '{}' - drop it once you are happy.",
                BACKUP_TABLE);
    }

    private boolean tableExists(String table) {
        Integer found = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE UPPER(TABLE_NAME) = ? AND TABLE_SCHEMA = SCHEMA()",
                Integer.class, table);
        return found != null && found > 0;
    }

    private long countRows(String table) {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return count == null ? 0L : count;
    }
}
