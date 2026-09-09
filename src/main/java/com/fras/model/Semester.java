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
 * A semester within a department. See {@link Department} for why these
 * constraints exist.
 *
 * <p>{@code @NotNull} on the department is what turns a missing relation into a
 * 400 naming the field. {@code optional = false} alone produced a
 * {@code PropertyValueException} out of the flush, which reached the client as
 * an opaque 500.
 */
@Entity
@Table(
        name = "semesters",
        indexes = @Index(name = "idx_semester_department", columnList = "department_id")
)
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A semester name is required")
    @Size(max = 120, message = "The semester name must be at most 120 characters")
    @Column(name = "semester_name", nullable = false, length = 120)
    private String name;

    @Min(value = 1, message = "Semester numbers start at 1")
    @Max(value = 20, message = "That semester number is not plausible")
    @Column(name = "semester_number", nullable = false)
    private int number;

    @NotNull(message = "A semester must belong to a department")
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    public Semester() {
    }

    public Semester(Long id, String name, int number, Department department) {
        this.id = id;
        this.name = name;
        this.number = number;
        this.department = department;
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

    public int getNumber() {
        return number;
    }

    public void setNumber(int number) {
        this.number = number;
    }

    public Department getDepartment() {
        return department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }
}