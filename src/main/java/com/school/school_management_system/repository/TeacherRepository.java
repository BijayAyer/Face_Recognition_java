package com.school.school_management_system.repository;

import com.school.school_management_system.entity.Teacher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeacherRepository extends JpaRepository<Teacher, Long> {

    Optional<Teacher> findByEmailIgnoreCase(String email);

    /** The staff row belonging to a sign-in account. */
    Optional<Teacher> findByUserId(Long userId);

    boolean existsByEmailIgnoreCase(String email);

    List<Teacher> findAllByOrderByNameAsc();
}
