package com.school.school_management_system.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * A teacher. Email is unique so the same person cannot be added twice,
 * which previously produced silent duplicates in the teacher list.
 *
 * <p>{@link #userId} ties this row to the account that signs in as this
 * person, the same way {@code Student.userId} does for the roster. Without
 * it, creating a teacher account left the Teachers page empty: the login
 * existed and the staff row did not.
 */
@Entity
@Table(name = "teachers")
public class Teacher {

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

    @NotBlank(message = "Subject is required")
    @Size(max = 160, message = "Subject must be at most 160 characters")
    @Column(nullable = false, length = 160)
    private String subject;

    /**
     * The account this staff row belongs to, or null for a row added by hand.
     * A plain id for the same reason as {@code Student.userId}: this entity is
     * serialised straight out of {@code /teachers}.
     */
    @Column(name = "user_id", unique = true)
    private Long userId;

    @Column(name = "created_at")
    private Instant createdAt;

    public Teacher() {
    }

    public Teacher(Long id, String name, String email, String subject) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.subject = subject;
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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "Teacher{id=" + id
                + ", name='" + name + "'"
                + ", email='" + email + "'"
                + ", subject='" + subject + "'"
                + ", userId=" + userId + '}';
    }
}
