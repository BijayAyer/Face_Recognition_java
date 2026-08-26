package com.fras.service;

import com.fras.model.Semester;

import java.util.List;

public interface SemesterService {
    void addSemester(Semester semester);
    void updateSemester(Semester semester);
    void deleteSemester(Long id);
    Semester getSemester(Long id);
    List<Semester> getAllSemesters();
    List<Semester> getSemestersByDepartment(Long departmentId);
}