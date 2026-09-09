package com.fras.model;

import jakarta.persistence.*;

@Entity
@Table(name = "semesters")
public class Semester {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "semester_name", nullable = false)
    private String name;

    @Column(name = "semester_number", nullable = false)
    private int number;

    @ManyToOne
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