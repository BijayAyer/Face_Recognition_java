package com.school.school_management_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A student on the roster.
 *
 * <p>The table is pinned to {@code students} (previously it relied on the
 * JPA default, which produced the singular {@code student}). Bean
 * Validation constraints are declared here as well as on the columns so
 * bad input is rejected with a 400 before it ever reaches the database
 * and turns into a 500.
 *
 * <p>A roster row and a sign-in account are two different things, and this
 * row is the one that matters: attendance is recorded against
 * {@code students.id}, and a face is enrolled into
 * {@code data/faces/<students.id>}. {@link #userId} is what ties the two
 * together. It used to not exist, so the only join between an account and
 * its roster row was the email address, and nothing created the row in the
 * first place - registering as a student produced a login that appeared on
 * no roster, could not be enrolled, and had no attendance to show.
 */
@Entity
@Table(name = "students")
public class Student {

    /**
     * The range {@code age} may take. Declared as constants because two things
     * have to agree on them: the Bean Validation annotations below, and
     * {@code SchemaConstraintRepair}, which compares them against the check
     * constraint recorded in the database in order to spot one left behind by
     * an older version of this class. Repeating the numbers in both places is
     * exactly the drift that broke start-up once already.
     */
    public static final int AGE_MIN = 0;
    public static final int AGE_MAX = 120;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Name is required")
    @Size(max = 120, message = "Name must be at most 120 characters")
    @Column(nullable = false, length = 120)
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 180, message = "Email must be at most 180 characters")
    @Column(nullable = false, unique = true, length = 180)
    private String email;

    /**
     * Zero means "not recorded", which is why the floor is 0 and not 3.
     * The roster form says age may be left blank and sends 0 when it is, and
     * a row created automatically for a new sign-up has nobody to ask - so a
     * minimum of 3 rejected both with "Age must be at least 3", a 400 for
     * leaving an optional field empty.
     *
     * <p>The messages are built from the same constants as the bounds, so a
     * change to one cannot leave the other saying something untrue. String
     * concatenation of compile-time constants is itself constant, which is
     * what lets it appear in an annotation.
     */
    @Min(value = AGE_MIN, message = "Age cannot be negative")
    @Max(value = AGE_MAX, message = "Age must be at most " + AGE_MAX)
    private int age;

    /**
     * Which teaching group this student sits in: "A", "4B", "Morning". Free
     * text, because a section is whatever the timetable calls it, and optional,
     * because a school that does not split its intake has none - and a roster
     * row created automatically at sign-up has nobody to ask.
     *
     * <p>Nullable deliberately. {@code ddl-auto=update} will add a nullable
     * column to a table that already holds rows; it cannot add a
     * {@code NOT NULL} one without inventing a value for every existing
     * student, and {@code SchemaConstraintRepair} exists because of what
     * happens when the schema and this class stop agreeing.
     *
     * <p>It is read far more often than it is written - it appears against
     * every line of every register - which is the reason it lives here on the
     * roster row rather than being asked for once per session.
     */
    @Size(max = 40, message = "Section must be at most 40 characters")
    @Column(name = "section", length = 40)
    private String section;

    /**
     * The account this roster row belongs to, or null for a row that was
     * typed in before anyone signed up with that address.
     *
     * <p>Deliberately a plain id rather than a mapped {@code @OneToOne User}.
     * This entity is what {@code /students} serialises, and an association
     * would drag the whole {@code User} - password hash included - into every
     * roster response, or hand Jackson a lazy proxy it cannot write. Accounts
     * are disabled rather than deleted, so a foreign key here would buy little.
     */
    @Column(name = "user_id", unique = true)
    private Long userId;

    /** Set once, on insert. Nullable so existing rows stay valid. */
    @Column(name = "created_at")
    private Instant createdAt;

    public Student() {
    }

    public Student(Long id, String name, String email, int age) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.age = age;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Student{id=" + id
                + ", name='" + name + "'"
                + ", email='" + email + "'"
                + ", age=" + age
                + ", section='" + section + "'"
                + ", userId=" + userId + '}';
    }
}
