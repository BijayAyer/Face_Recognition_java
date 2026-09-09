package com.fras.test;

import com.school.school_management_system.config.SchemaConstraintRepair;
import com.school.school_management_system.entity.Student;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The schema disagreeing with the entity, and the repair that reconciles them.
 *
 * <p>This is the one failure in the project that no amount of reading the code
 * could have found, because the defect was not in the code: {@code Student}
 * used to declare {@code @Min(3)} on {@code age}, Hibernate exports validation
 * bounds into the DDL, and {@code ddl-auto=update} never revisits a check
 * constraint it wrote earlier. Relaxing the annotation to allow 0 therefore
 * changed nothing on disk, and the first insert of an unrecorded age against a
 * database created under the old annotation took the whole backend down at
 * start-up.
 *
 * <p>So the stale constraint is manufactured here by hand. A test that only
 * ran against a freshly created schema would pass no matter what the repair
 * did, because a fresh schema is built from the current annotations and is
 * correct by construction - the bug only exists in the gap between an old
 * database and new code, and the gap has to be recreated to be tested.
 *
 * <p>The schema outlives each test method, so {@code @AfterEach} puts it back:
 * these are the only tests here that touch DDL rather than rows.
 */
class SchemaConstraintRepairTest extends ApiTestSupport {

    /**
     * What an older {@code Student} put into every database created while it
     * was current. Named, unlike the one H2 invents, only so that a failure
     * here is readable - the repair identifies it by the numbers in the clause,
     * not by its name, which is the whole point: the real one was called
     * {@code CONSTRAINT_9E}.
     */
    private static final String STALE_CHECK =
            "ALTER TABLE \"STUDENTS\" ADD CONSTRAINT \"CK_STALE_AGE\""
            + " CHECK (\"AGE\" >= 3 AND \"AGE\" <= 120)";

    /**
     * Deliberately not the query the class under test uses, so that a wrong
     * catalogue query cannot agree with itself.
     */
    private static final String AGE_CHECKS =
            " FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc"
            + " JOIN INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc"
            + " ON tc.CONSTRAINT_SCHEMA = cc.CONSTRAINT_SCHEMA"
            + " AND tc.CONSTRAINT_NAME = cc.CONSTRAINT_NAME"
            + " WHERE tc.TABLE_SCHEMA = SCHEMA()"
            + " AND UPPER(tc.TABLE_NAME) = 'STUDENTS'"
            + " AND tc.CONSTRAINT_TYPE = 'CHECK'"
            + " AND UPPER(cc.CHECK_CLAUSE) LIKE '%AGE%'";

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private SchemaConstraintRepair repair;

    /**
     * Rows are cleared before each test by the base class, but DDL is not, and
     * the in-memory database is shared by every suite in this Spring context.
     * A test here that left the roster table without an age check would hand
     * the next suite a table that accepts anything.
     */
    @AfterEach
    void leaveTheSchemaAsItShouldBe() {
        dropEveryAgeCheck();
        repair.run(null);
    }

    @Test
    @DisplayName("a stale age check written by an older entity is replaced")
    void aStaleAgeCheckIsReplaced() {
        dropEveryAgeCheck();
        jdbc.execute(STALE_CHECK);

        // The fixture has to actually reproduce the failure first, or the
        // assertion after the repair proves nothing at all. H2 reports this as
        // error 23513, which Spring translates by SQL state.
        assertThatThrownBy(() -> students.save(unrecordedAge()))
                .isInstanceOf(DataIntegrityViolationException.class);

        repair.run(null);

        // And this is the assertion that catches a wrong INFORMATION_SCHEMA
        // query: run() logs and swallows everything, so a repair that inspected
        // nothing would look identical in the log and fail right here.
        assertThat(students.save(unrecordedAge()).getId()).isNotNull();
    }

    @Test
    @DisplayName("the age range is still enforced by the database afterwards")
    void theRangeIsStillEnforcedAfterwards() {
        dropEveryAgeCheck();
        repair.run(null);

        // Straight through JDBC on purpose. Going via the repository would be
        // refused by Bean Validation before it reached the table, so it would
        // prove nothing about what the table itself will accept - and a repair
        // that dropped the stale rule without installing a replacement has
        // removed a protection rather than corrected one.
        //
        // The exception type is the specific one, not DataAccessException: a
        // mistyped column name in the statement below would also be a
        // DataAccessException, and this test would then pass without the
        // database having refused anything.
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO \"STUDENTS\" (NAME, EMAIL, AGE) VALUES ('Too Old', 'too.old@fras.test', ?)",
                Student.AGE_MAX + 1))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("a check that already matches the entity is left untouched")
    void repairIsIdempotent() {
        repair.run(null);
        List<String> afterFirstRun = ageCheckClauses();

        repair.run(null);

        // Framed as idempotence rather than "Hibernate's own constraint is left
        // alone", because whether Hibernate wrote one on this schema is its
        // business, not this test's. Either way there is exactly one age check
        // when the repair has finished, and running again must not churn it -
        // this runs on every single start-up.
        assertThat(afterFirstRun).hasSize(1);
        assertThat(ageCheckClauses()).isEqualTo(afterFirstRun);
    }

    /** Age 0 is how the roster records an age nobody supplied. */
    private static Student unrecordedAge() {
        return new Student(null, "Unrecorded Age", "unrecorded.age@fras.test", 0);
    }

    private List<String> ageCheckClauses() {
        return jdbc.queryForList("SELECT cc.CHECK_CLAUSE" + AGE_CHECKS, String.class);
    }

    private void dropEveryAgeCheck() {
        for (String name : jdbc.queryForList("SELECT cc.CONSTRAINT_NAME" + AGE_CHECKS, String.class)) {
            jdbc.execute("ALTER TABLE \"STUDENTS\" DROP CONSTRAINT \"" + name + "\"");
        }
    }
}
