package com.fras.dao;

import com.fras.model.Classroom;

import java.util.List;

public interface ClassroomDAO {
    void save(Classroom classroom);
    void update(Classroom classroom);
    void delete(Long id);
    Classroom findById(Long id);
    List<Classroom> findAll();
}
