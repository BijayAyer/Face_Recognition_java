package com.fras.dao;

import com.fras.model.Department;

import java.util.List;

public interface DepartmentDAO {

    void save(Department department);

    void update(Department department);

    void delete(Long id);

    Department findById(Long id);

    List<Department> findAll();
}
