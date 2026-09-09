package com.fras.service.impl;

import com.fras.dao.SemesterDAO;
import com.fras.dao.impl.SemesterDAOImpl;
import com.fras.model.Semester;
import com.fras.service.SemesterService;

import java.util.List;

public class SemesterServiceImpl implements SemesterService {

    private final SemesterDAO semesterDAO = new SemesterDAOImpl();

    @Override
    public void addSemester(Semester semester) {
        semesterDAO.save(semester);
    }

    @Override
    public void updateSemester(Semester semester) {
        semesterDAO.update(semester);
    }

    @Override
    public void deleteSemester(Long id) {
        semesterDAO.delete(id);
    }

    @Override
    public Semester getSemester(Long id) {
        return semesterDAO.findById(id);
    }

    @Override
    public List<Semester> getAllSemesters() {
        return semesterDAO.findAll();
    }

    @Override
    public List<Semester> getSemestersByDepartment(Long departmentId) {
        return semesterDAO.findByDepartmentId(departmentId);
    }
}