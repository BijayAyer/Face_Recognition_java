package com.fras.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A department, the root of the academic hierarchy.
 *
 * <p>The constraints are declared here as well as on the columns, and the
 * distinction matters: {@code nullable = false} stops a null but not an empty
 * string, so {@code {"departmentCode": ""}} used to be stored happily and
 * showed up in the client's tables as a blank row that could not be told apart
 * from a real one. {@code length = 160} on its own is worse than useless - an
 * over-length name reached the driver and came back as a 500 with no usable
 * message, where {@code @Size} names the field and the limit in a 400.
 *
 * <p>Every academic entity in this package was missing these, while
 * {@code Student} and {@code Teacher} next door have had them all along.
 */
@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A department code is required")
    @Size(max = 32, message = "The department code must be at most 32 characters")
    @Column(name = "department_code", unique = true, nullable = false, length = 32)
    private String departmentCode;

    @NotBlank(message = "A department name is required")
    @Size(max = 160, message = "The department name must be at most 160 characters")
    @Column(name = "department_name", nullable = false, length = 160)
    private String departmentName;

    @Size(max = 500, message = "The description must be at most 500 characters")
    @Column(name = "description", length = 500)
    private String description;

    public Department() {
    }

    public Department(Long id, String departmentCode, String departmentName, String description) {
        this.id = id;
        this.departmentCode = departmentCode;
        this.departmentName = departmentName;
        this.description = description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDepartmentName() {
        return departmentName;
    }

    public void setDepartmentName(String departmentName) {
        this.departmentName = departmentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}