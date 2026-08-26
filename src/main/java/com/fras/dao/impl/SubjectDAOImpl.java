package com.fras.dao.impl;

import com.fras.dao.SubjectDAO;
import com.fras.model.Subject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class SubjectDAOImpl implements SubjectDAO {

    private static final List<Subject> subjects = new ArrayList<>();
    private static long nextId = 1;

    @Override
    public void save(Subject subject) {
        subject.setId(nextId++);
        subjects.add(subject);
    }

    @Override
    public void update(Subject subject) {
        for (int i = 0; i < subjects.size(); i++) {
            if (subjects.get(i).getId().equals(subject.getId())) {
                subjects.set(i, subject);
                return;
            }
        }
    }

    @Override
    public void delete(Long id) {
        subjects.removeIf(s -> s.getId().equals(id));
    }

    @Override
    public Subject findById(Long id) {
        return subjects.stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Subject> findAll() {
        return new ArrayList<>(subjects);
    }

    @Override
    public List<Subject> findBySemesterId(Long semesterId) {
        return subjects.stream()
                .filter(s -> s.getSemester() != null && s.getSemester().getId().equals(semesterId))
                .collect(Collectors.toList());
    }
}
