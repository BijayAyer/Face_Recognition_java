package com.fras.dao.impl;

import com.fras.dao.SemesterDAO;
import com.fras.model.Semester;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SemesterDAOImpl implements SemesterDAO {

    private static final List<Semester> semesters = new ArrayList<>();
    private static long nextId = 1;

    @Override
    public void save(Semester semester) {
        semester.setId(nextId++);
        semesters.add(semester);
    }

    @Override
    public void update(Semester semester) {
        for (int i = 0; i < semesters.size(); i++) {
            if (semesters.get(i).getId().equals(semester.getId())) {
                semesters.set(i, semester);
                return;
            }
        }
    }

    @Override
    public void delete(Long id) {
        semesters.removeIf(s -> s.getId().equals(id));
    }

    @Override
    public Semester findById(Long id) {
        return semesters.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Semester> findAll() {
        return new ArrayList<>(semesters);
    }

    @Override
    public List<Semester> findByDepartmentId(Long departmentId) {
        return semesters.stream()
                .filter(s -> s.getDepartment() != null && s.getDepartment().getId().equals(departmentId))
                .collect(Collectors.toList());
    }
}
