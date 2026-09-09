package com.fras.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A subject within a semester. See {@link Department} for why these constraints
 * exist.
 *
 * <p>The credit is the one worth naming: nothing stopped a subject worth zero
 * or minus three credits, because {@code nullable = false} on an {@code int}
 * column is satisfied by any value at all.
 */
@Entity
@Table(
        name = "subjects",
        indexes = @Index(name = "idx_subject_semester", columnList = "semester_id")
)
public class Subject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A subject code is required")
    @Size(max = 32, message = "The subject code must be at most 32 characters")
    @Column(name = "subject_code", unique = true, nullable = false, length = 32)
    private String code;

    @NotBlank(message = "A subject name is required")
    @Size(max = 160, message = "The subject name must be at most 160 characters")
    @Column(name = "subject_name", nullable = false, length = 160)
    private String name;

    @Min(value = 1, message = "A subject must be worth at least one credit")
    @Max(value = 30, message = "That credit value is not plausible")
    @Column(name = "credit", nullable = false)
    private int credit;

    @NotNull(message = "A subject must belong to a semester")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    public Subject() {
    }

    public Subject(Long id, String code, String name, int credit, Semester semester) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.credit = credit;
        this.semester = semester;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getCredit() {
        return credit;
    }

    public void setCredit(int credit) {
        this.credit = credit;
    }

    public Semester getSemester() {
        return semester;
    }

    public void setSemester(Semester semester) {
        this.semester = semester;
    }
}