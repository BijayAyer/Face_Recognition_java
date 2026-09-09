package com.fras.dao.impl;

import com.fras.dao.ClassroomDAO;
import com.fras.model.Classroom;

import java.util.ArrayList;
import java.util.List;

public class ClassroomDAOImpl implements ClassroomDAO {

    private static final List<Classroom> classrooms = new ArrayList<>();
    private static long nextId = 1;

    @Override
    public void save(Classroom classroom) {
        classroom.setId(nextId++);
        classrooms.add(classroom);
    }

    @Override
    public void update(Classroom classroom) {
        for (int i = 0; i < classrooms.size(); i++) {
            if (classrooms.get(i).getId().equals(classroom.getId())) {
                classrooms.set(i, classroom);
                return;
            }
        }
    }

    @Override
    public void delete(Long id) {
        classrooms.removeIf(c -> c.getId().equals(id));
    }

    @Override
    public Classroom findById(Long id) {
        return classrooms.stream()
                .filter(c -> c.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Classroom> findAll() {
        return new ArrayList<>(classrooms);
    }
}
