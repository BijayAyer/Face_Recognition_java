package com.fras.repository;

import com.fras.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Departments, the root of the academic hierarchy.
 *
 * <p>A {@code existsByDepartmentCodeIgnoreCase} used to sit beside the finder
 * below and had no callers. It was not merely unused but strictly weaker: the
 * duplicate-code check in {@code DepartmentRestController} has to name the
 * department already holding the code, and a bare {@code boolean} cannot. Two
 * ways to ask one question, one of them unable to answer it usefully, is an
 * invitation to reach for the wrong one.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    Optional<Department> findByDepartmentCodeIgnoreCase(String departmentCode);

    List<Department> findAllByOrderByDepartmentNameAsc();
}
