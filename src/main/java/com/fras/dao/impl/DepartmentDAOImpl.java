package com.fras.dao.impl;

import com.fras.dao.DepartmentDAO;
import com.fras.model.Department;

import java.util.ArrayList;
import java.util.List;

public class DepartmentDAOImpl implements DepartmentDAO {

    private static final List<Department> departments = new ArrayList<>();
    private static long nextId = 1;

    @Override
    public void save(Department department) {
        department.setId(nextId++);
        departments.add(department);
    }

    @Override
    public void update(Department department) {
        for (int i = 0; i < departments.size(); i++) {
            if (departments.get(i).getId().equals(department.getId())) {
                departments.set(i, department);
                return;
            }
        }
    }

    @Override
    public void delete(Long id) {
        departments.removeIf(d -> d.getId().equals(id));
    }

    @Override
    public Department findById(Long id) {
        return departments.stream()
                .filter(d -> d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Department> findAll() {
        return new ArrayList<>(departments);
    }
}