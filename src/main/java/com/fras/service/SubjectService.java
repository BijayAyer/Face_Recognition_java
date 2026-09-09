package com.fras.service;

import com.fras.model.Subject;

import java.util.List;

public interface SubjectService {
    void addSubject(Subject subject);
    void updateSubject(Subject subject);
    void deleteSubject(Long id);
    Subject getSubject(Long id);
    List<Subject> getAllSubjects();
    List<Subject> getSubjectsBySemester(Long semesterId);
}
