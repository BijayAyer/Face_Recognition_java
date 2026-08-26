package com.school.school_management_system.service;

import com.school.school_management_system.entity.Teacher;
import com.school.school_management_system.repository.TeacherRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;

    public TeacherService(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    public Teacher saveTeacher(Teacher teacher) {
        return teacherRepository.save(teacher);
    }

    public List<Teacher> getAllTeachers() {
        return teacherRepository.findAll();
    }

    public Optional<Teacher> getTeacherById(Long id) {
        return teacherRepository.findById(id);
    }

    public Optional<Teacher> updateTeacher(Long id, Teacher teacher) {
        return teacherRepository.findById(id).map(existingTeacher -> {
            existingTeacher.setName(teacher.getName());
            existingTeacher.setEmail(teacher.getEmail());
            existingTeacher.setSubject(teacher.getSubject());
            return teacherRepository.save(existingTeacher);
        });
    }

    public boolean deleteTeacher(Long id) {
        if (!teacherRepository.existsById(id)) {
            return false;
        }
        teacherRepository.deleteById(id);
        return true;
    }
}
