package com.fras.dao.impl;

import com.fras.dao.SemesterDAO;
import com.fras.model.Semester;
import com.fras.service.ApiService;

import java.util.List;

/**
 * Semesters, over {@code /academic/semesters}. See
 * {@link DepartmentDAOImpl} for why the reads throw rather than answering a
 * failed call with an empty list.
 *
 * <p>Blocking. Call from a background thread; the screens use
 * {@link com.fras.ui.Async}.
 */
public class SemesterDAOImpl implements SemesterDAO {

    private final ApiService apiService = ApiService.getShared();

    @Override
    public void save(Semester semester) {
        try {
            apiService.post("/academic/semesters", AcademicJson.toJson(semester));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("add the semester", e);
        }
    }

    @Override
    public void update(Semester semester) {
        try {
            apiService.put("/academic/semesters/" + semester.getId(),
                    AcademicJson.toJson(semester));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("update the semester", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            apiService.delete("/academic/semesters/" + id);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("delete the semester", e);
        }
    }

    @Override
    public Semester findById(Long id) {
        return findAll().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Semester> findAll() {
        try {
            return AcademicJson.parseList(apiService.get("/academic/semesters"), Semester.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the semesters", e);
        }
    }

    @Override
    public List<Semester> findByDepartmentId(Long departmentId) {
        try {
            String json = apiService.get("/academic/semesters?departmentId=" + departmentId);
            return AcademicJson.parseList(json, Semester.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the department's semesters", e);
        }
    }
}
