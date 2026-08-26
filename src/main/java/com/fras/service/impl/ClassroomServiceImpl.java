package com.fras.service.impl;

import com.fras.dao.ClassroomDAO;
import com.fras.dao.impl.ClassroomDAOImpl;
import com.fras.model.Classroom;
import com.fras.service.ClassroomService;

import java.util.List;

public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomDAO classroomDAO = new ClassroomDAOImpl();

    @Override
    public void addClassroom(Classroom classroom) {
        classroomDAO.save(classroom);
    }

    @Override
    public void updateClassroom(Classroom classroom) {
        classroomDAO.update(classroom);
    }

    @Override
    public void deleteClassroom(Long id) {
        classroomDAO.delete(id);
    }

    @Override
    public Classroom getClassroom(Long id) {
        return classroomDAO.findById(id);
    }

    @Override
    public List<Classroom> getAllClassrooms() {
        return classroomDAO.findAll();
    }
}
