package com.fras.service.impl;

import com.fras.dao.TimetableDAO;
import com.fras.dao.impl.TimetableDAOImpl;
import com.fras.model.Timetable;
import com.fras.service.TimetableService;

import java.util.List;

public class TimetableServiceImpl implements TimetableService {

    private final TimetableDAO timetableDAO = new TimetableDAOImpl();

    @Override
    public void addTimetable(Timetable timetable) {
        if (timetableDAO.hasClassroomConflict(timetable)) {
            throw new IllegalStateException("This classroom is already booked for an overlapping time on " + timetable.getDay() + ".");
        }
        timetableDAO.save(timetable);
    }

    @Override
    public void updateTimetable(Timetable timetable) {
        if (timetableDAO.hasClassroomConflict(timetable)) {
            throw new IllegalStateException("This classroom is already booked for an overlapping time on " + timetable.getDay() + ".");
        }
        timetableDAO.update(timetable);
    }

    @Override
    public void deleteTimetable(Long id) {
        timetableDAO.delete(id);
    }

    @Override
    public Timetable getTimetable(Long id) {
        return timetableDAO.findById(id);
    }

    @Override
    public List<Timetable> getAllTimetables() {
        return timetableDAO.findAll();
    }
}
