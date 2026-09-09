package com.fras.service.impl;

import com.fras.dao.DepartmentDAO;
import com.fras.dao.impl.DepartmentDAOImpl;
import com.fras.model.Department;
import com.fras.service.DepartmentService;

import java.util.List;

public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    @Override
    public void addDepartment(Department department) {
        departmentDAO.save(department);
    }

    @Override
    public void updateDepartment(Department department) {
        departmentDAO.update(department);
    }

    @Override
    public void deleteDepartment(Long id) {
        departmentDAO.delete(id);
    }

    @Override
    public Department getDepartment(Long id) {
        return departmentDAO.findById(id);
    }

    @Override
    public List<Department> getAllDepartments() {
        return departmentDAO.findAll();
    }
}