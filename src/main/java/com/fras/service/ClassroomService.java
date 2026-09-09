package com.fras.service;

import com.fras.model.Classroom;

import java.util.List;

public interface ClassroomService {
    void addClassroom(Classroom classroom);
    void updateClassroom(Classroom classroom);
    void deleteClassroom(Long id);
    Classroom getClassroom(Long id);
    List<Classroom> getAllClassrooms();
}