package com.fras.dao;

import com.fras.model.Semester;

import java.util.List;

public interface SemesterDAO {
    void save(Semester semester);
    void update(Semester semester);
    void delete(Long id);
    Semester findById(Long id);
    List<Semester> findAll();
    List<Semester> findByDepartmentId(Long departmentId);
}