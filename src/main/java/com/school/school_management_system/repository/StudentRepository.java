package com.school.school_management_system.repository;

import com.school.school_management_system.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StudentRepository extends JpaRepository<Student, Long> {

    Optional<Student> findByEmailIgnoreCase(String email);

    /**
     * The roster row belonging to a sign-in account. Preferred over the email
     * lookup: an address can be changed on either side, and this cannot.
     */
    Optional<Student> findByUserId(Long userId);

    boolean existsByEmailIgnoreCase(String email);

    List<Student> findAllByOrderByNameAsc();

    List<Student> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email);
}
