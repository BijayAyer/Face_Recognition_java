package com.fras.dao.impl;

import com.fras.dao.SubjectDAO;
import com.fras.model.Subject;
import com.fras.service.ApiService;

import java.util.List;

/**
 * Subjects, over {@code /academic/subjects}. See {@link DepartmentDAOImpl} for
 * why the reads throw rather than answering a failed call with an empty list.
 *
 * <p>Blocking. Call from a background thread; the screens use
 * {@link com.fras.ui.Async}.
 */
public class SubjectDAOImpl implements SubjectDAO {

    private final ApiService apiService = ApiService.getShared();

    @Override
    public void save(Subject subject) {
        try {
            apiService.post("/academic/subjects", AcademicJson.toJson(subject));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("add the subject", e);
        }
    }

    @Override
    public void update(Subject subject) {
        try {
            apiService.put("/academic/subjects/" + subject.getId(),
                    AcademicJson.toJson(subject));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("update the subject", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            apiService.delete("/academic/subjects/" + id);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("delete the subject", e);
        }
    }

    @Override
    public Subject findById(Long id) {
        return findAll().stream()
                .filter(s -> s.getId() != null && s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Subject> findAll() {
        try {
            return AcademicJson.parseList(apiService.get("/academic/subjects"), Subject.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the subjects", e);
        }
    }

    @Override
    public List<Subject> findBySemesterId(Long semesterId) {
        try {
            String json = apiService.get("/academic/subjects?semesterId=" + semesterId);
            return AcademicJson.parseList(json, Subject.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the semester's subjects", e);
        }
    }
}
