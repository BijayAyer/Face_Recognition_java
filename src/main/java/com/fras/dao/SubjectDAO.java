package com.fras.dao;

import com.fras.model.Subject;

import java.util.List;

public interface SubjectDAO {
    void save(Subject subject);
    void update(Subject subject);
    void delete(Long id);
    Subject findById(Long id);
    List<Subject> findAll();
    List<Subject> findBySemesterId(Long semesterId);
}
