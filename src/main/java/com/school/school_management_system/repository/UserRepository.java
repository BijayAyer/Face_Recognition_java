package com.school.school_management_system.repository;

import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /**
     * Emails are normalised to lower case on write, but logins arrive
     * however the person typed them, so look up case-insensitively.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByRole(Role role);

    List<User> findAllByOrderByEmailAsc();
}
