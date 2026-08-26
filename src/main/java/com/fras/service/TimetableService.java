package com.fras.service;

import com.fras.model.Timetable;

import java.util.List;

public interface TimetableService {
    void addTimetable(Timetable timetable) throws IllegalStateException;
    void updateTimetable(Timetable timetable) throws IllegalStateException;
    void deleteTimetable(Long id);
    Timetable getTimetable(Long id);
    List<Timetable> getAllTimetables();
}