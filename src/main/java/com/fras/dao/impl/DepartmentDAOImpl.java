package com.fras.dao.impl;

import com.fras.dao.DepartmentDAO;
import com.fras.model.Department;
import com.fras.service.ApiService;

import java.util.List;

/**
 * Departments, over {@code /academic/departments}.
 *
 * <p>Every method reaches the network, and every method now reports a failure
 * instead of absorbing it. {@link #findAll()} used to answer an unreachable
 * server with an empty list, which the screen showed as "no departments" - and
 * because {@link #findById(Long)} is built on {@code findAll}, the same failure
 * also turned into "no such department". Both throw now.
 *
 * <p>Blocking. Call from a background thread; the screens use
 * {@link com.fras.ui.Async}.
 */
public class DepartmentDAOImpl implements DepartmentDAO {

    private final ApiService apiService = ApiService.getShared();

    @Override
    public void save(Department department) {
        try {
            apiService.post("/academic/departments", AcademicJson.toJson(department));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("add the department", e);
        }
    }

    @Override
    public void update(Department department) {
        try {
            apiService.put("/academic/departments/" + department.getId(),
                    AcademicJson.toJson(department));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("update the department", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            apiService.delete("/academic/departments/" + id);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("delete the department", e);
        }
    }

    @Override
    public Department findById(Long id) {
        return findAll().stream()
                .filter(d -> d.getId() != null && d.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Department> findAll() {
        try {
            return AcademicJson.parseList(apiService.get("/academic/departments"),
                    Department.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the departments", e);
        }
    }
}
