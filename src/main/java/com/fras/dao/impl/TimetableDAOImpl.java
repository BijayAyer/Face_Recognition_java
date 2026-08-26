package com.fras.dao.impl;

import com.fras.dao.TimetableDAO;
import com.fras.model.Timetable;

import java.util.ArrayList;
import java.util.List;

public class TimetableDAOImpl implements TimetableDAO {

    private static final List<Timetable> timetables = new ArrayList<>();
    private static long nextId = 1;

    @Override
    public void save(Timetable timetable) {
        timetable.setId(nextId++);
        timetables.add(timetable);
    }

    @Override
    public void update(Timetable timetable) {
        for (int i = 0; i < timetables.size(); i++) {
            if (timetables.get(i).getId().equals(timetable.getId())) {
                timetables.set(i, timetable);
                return;
            }
        }
    }

    @Override
    public void delete(Long id) {
        timetables.removeIf(t -> t.getId().equals(id));
    }

    @Override
    public Timetable findById(Long id) {
        return timetables.stream()
                .filter(t -> t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Timetable> findAll() {
        return new ArrayList<>(timetables);
    }

    @Override
    public boolean hasClassroomConflict(Timetable candidate) {
        for (Timetable existing : timetables) {
            if (existing.getId().equals(candidate.getId())) continue; // skip self when updating
            boolean sameRoom = existing.getClassroom().getId().equals(candidate.getClassroom().getId());
            boolean sameDay = existing.getDay().equalsIgnoreCase(candidate.getDay());
            boolean overlaps = candidate.getStartTime().isBefore(existing.getEndTime())
                    && existing.getStartTime().isBefore(candidate.getEndTime());
            if (sameRoom && sameDay && overlaps) {
                return true;
            }
        }
        return false;
    }
}
