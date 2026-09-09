package com.fras.dao.impl;

import com.fras.dao.ClassroomDAO;
import com.fras.model.Classroom;
import com.fras.service.ApiService;

import java.util.List;

/**
 * Classrooms, over {@code /academic/classrooms}. See {@link DepartmentDAOImpl}
 * for why the reads throw rather than answering a failed call with an empty
 * list.
 *
 * <p>Blocking. Call from a background thread; the screens use
 * {@link com.fras.ui.Async}.
 */
public class ClassroomDAOImpl implements ClassroomDAO {

    private final ApiService apiService = ApiService.getShared();

    @Override
    public void save(Classroom classroom) {
        try {
            apiService.post("/academic/classrooms", AcademicJson.toJson(classroom));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("add the classroom", e);
        }
    }

    @Override
    public void update(Classroom classroom) {
        try {
            apiService.put("/academic/classrooms/" + classroom.getId(),
                    AcademicJson.toJson(classroom));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("update the classroom", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            apiService.delete("/academic/classrooms/" + id);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("delete the classroom", e);
        }
    }

    @Override
    public Classroom findById(Long id) {
        return findAll().stream()
                .filter(c -> c.getId() != null && c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Classroom> findAll() {
        try {
            return AcademicJson.parseList(apiService.get("/academic/classrooms"),
                    Classroom.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the classrooms", e);
        }
    }
}
