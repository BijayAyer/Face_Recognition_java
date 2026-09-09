package com.fras.app.dto;

/**
 * Lightweight client-side row model used to render the JSON array
 * returned by GET /students in a JavaFX TableView instead of dumping
 * the raw JSON text. Field names mirror the backend Student entity
 * so Jackson can deserialize it with no custom configuration, and
 * the getters follow the JavaBean convention so
 * javafx.scene.control.cell.PropertyValueFactory can read them.
 *
 * <p>{@link #getAccount()}, {@link #getAgeLabel()} and
 * {@link #getSectionLabel()} are read-only properties with no field behind
 * them. They exist because three values the roster has to show are not the
 * values the server stores: a null {@code userId} means "nobody can sign in as
 * this student", an age of 0 means "not recorded" rather than a newborn, and a
 * missing section is one absence of a value rather than an empty string.
 * Deciding that in the table cell would put it out of reach of every other
 * reader of this row.
 */
public class StudentRow {

    /** What the roster shows for a student with no section recorded. */
    public static final String NO_SECTION = "-";

    private Long id;
    private String name;
    private String email;
    private int age;
    private String section;
    private Long userId;

    public StudentRow() {
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

    /** The stored value, which is null for a student with no section. */
    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    /**
     * Section as the roster and the register should read it. Null and blank both
     * become "-", the same as the server prints on a register line, so the two
     * screens agree about a student nobody has put in a group.
     */
    public String getSectionLabel() {
        return section == null || section.isBlank() ? NO_SECTION : section;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Whether anybody can sign in as this student. A roster row with no
     * account cannot enrol a face or open its own attendance screen, which
     * is worth saying on the roster rather than leaving to be discovered.
     */
    public String getAccount() {
        return userId == null ? "No account" : "Linked";
    }

    /**
     * Age as the roster should read it. Accounts created at sign-up have no
     * age to record, and 0 there means "nobody has said", so printing the
     * digit would state something the database does not actually know.
     */
    public String getAgeLabel() {
        return age <= 0 ? "Not recorded" : String.valueOf(age);
    }
}
