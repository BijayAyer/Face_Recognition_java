package com.school.school_management_system.service;

import com.school.school_management_system.entity.Teacher;
import com.school.school_management_system.exception.EmailAlreadyExistsException;
import com.school.school_management_system.repository.TeacherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Roster operations for teachers. Same three corrections as
 * {@link StudentService}: the client cannot choose the id on create, a
 * duplicate email is a 409 rather than a 500, and every method runs in a
 * transaction of the right kind.
 */
@Service
public class TeacherService {

    private final TeacherRepository teacherRepository;

    public TeacherService(TeacherRepository teacherRepository) {
        this.teacherRepository = teacherRepository;
    }

    @Transactional
    public Teacher saveTeacher(Teacher teacher) {
        teacher.setId(null);
        // Cleared for the same reason as Student.userId: the entity is bound
        // from the request body, and the account link is not the caller's to
        // choose.
        teacher.setUserId(null);
        teacher.setName(trim(teacher.getName()));
        teacher.setEmail(trim(teacher.getEmail()));
        teacher.setSubject(trim(teacher.getSubject()));

        if (teacher.getEmail() != null && teacherRepository.existsByEmailIgnoreCase(teacher.getEmail())) {
            throw new EmailAlreadyExistsException(teacher.getEmail());
        }
        return teacherRepository.save(teacher);
    }

    @Transactional(readOnly = true)
    public List<Teacher> getAllTeachers() {
        return teacherRepository.findAllByOrderByNameAsc();
    }

    @Transactional(readOnly = true)
    public Optional<Teacher> getTeacherById(Long id) {
        return id == null ? Optional.empty() : teacherRepository.findById(id);
    }

    @Transactional
    public Optional<Teacher> updateTeacher(Long id, Teacher teacher) {
        if (id == null) {
            return Optional.empty();
        }
        return teacherRepository.findById(id).map(existing -> {
            String email = trim(teacher.getEmail());

            if (email != null && !email.equalsIgnoreCase(existing.getEmail())
                    && teacherRepository.existsByEmailIgnoreCase(email)) {
                throw new EmailAlreadyExistsException(email);
            }

            existing.setName(trim(teacher.getName()));
            existing.setEmail(email);
            existing.setSubject(trim(teacher.getSubject()));
            return teacherRepository.save(existing);
        });
    }

    @Transactional
    public boolean deleteTeacher(Long id) {
        if (id == null || !teacherRepository.existsById(id)) {
            return false;
        }
        teacherRepository.deleteById(id);
        return true;
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
