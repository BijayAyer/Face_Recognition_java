package com.fras.service.impl;

import com.fras.dao.SubjectDAO;
import com.fras.dao.impl.SubjectDAOImpl;
import com.fras.model.Subject;
import com.fras.service.SubjectService;

import java.util.List;

public class SubjectServiceImpl implements SubjectService {

    private final SubjectDAO subjectDAO = new SubjectDAOImpl();

    @Override
    public void addSubject(Subject subject) {
        subjectDAO.save(subject);
    }

    @Override
    public void updateSubject(Subject subject) {
        subjectDAO.update(subject);
    }

    @Override
    public void deleteSubject(Long id) {
        subjectDAO.delete(id);
    }

    @Override
    public Subject getSubject(Long id) {
        return subjectDAO.findById(id);
    }

    @Override
    public List<Subject> getAllSubjects() {
        return subjectDAO.findAll();
    }

    @Override
    public List<Subject> getSubjectsBySemester(Long semesterId) {
        return subjectDAO.findBySemesterId(semesterId);
    }
}