package com.fras.app.dto;

/**
 * Lightweight client-side row model used to render the JSON array
 * returned by GET /teachers in a JavaFX TableView instead of dumping
 * the raw JSON text. Field names mirror the backend Teacher entity
 * so Jackson can deserialize it with no custom configuration, and
 * the getters follow the JavaBean convention so
 * javafx.scene.control.cell.PropertyValueFactory can read them.
 *
 * <p>{@link #getAccount()} is a read-only property with no field behind it,
 * for the same reason as on the student roster: a null {@code userId} means
 * nobody can sign in as this member of staff, and that is a fact about the row
 * rather than a decision for one table cell to make.
 */
public class TeacherRow {

    private Long id;
    private String name;
    private String email;
    private String subject;
    private Long userId;

    public TeacherRow() {
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

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * Whether anybody can sign in as this member of staff. A staff row with no
     * account is a name on a list; it cannot open a session or take a register.
     */
    public String getAccount() {
        return userId == null ? "No account" : "Linked";
    }
}
