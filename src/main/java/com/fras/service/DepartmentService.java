package com.fras.service;

import com.fras.model.Department;

import java.util.List;

public interface DepartmentService {

    void addDepartment(Department department);

    void updateDepartment(Department department);

    void deleteDepartment(Long id);

    Department getDepartment(Long id);

    List<Department> getAllDepartments();
}
