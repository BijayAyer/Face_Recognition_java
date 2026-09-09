package com.fras.dao;

import com.fras.model.Timetable;

import java.util.List;

public interface TimetableDAO {
    void save(Timetable timetable);
    void update(Timetable timetable);
    void delete(Long id);
    Timetable findById(Long id);
    List<Timetable> findAll();
    boolean hasClassroomConflict(Timetable timetable);
}