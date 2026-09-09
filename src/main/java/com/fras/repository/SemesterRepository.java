package com.fras.repository;

import com.fras.model.Semester;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Semesters. The graphs fetch the owning department in the same select; see
 * {@link TimetableRepository} for why {@code EAGER} alone did not.
 */
public interface SemesterRepository extends JpaRepository<Semester, Long> {

    @Override
    @EntityGraph(attributePaths = {"department"})
    List<Semester> findAll();

    @EntityGraph(attributePaths = {"department"})
    List<Semester> findByDepartmentId(Long departmentId);

    @EntityGraph(attributePaths = {"department"})
    List<Semester> findAllByOrderByNumberAsc();

    /**
     * How many semesters a department still holds, asked before deleting it.
     *
     * <p>Deliberately a count and not a list: the answer goes straight into the
     * sentence explaining the refusal, and no entity graph runs, so this stays
     * one aggregate query instead of hydrating every semester and its department
     * to discard them.
     */
    long countByDepartmentId(Long departmentId);

    /**
     * Unused, and deliberately so. It reads like a uniqueness rule - one
     * "Semester 2" per department - but no such constraint exists in the schema,
     * and it should not: semester names in this system are year-scoped ("Spring
     * 2026"), so number 2 recurs every year. Enforcing it would refuse a
     * legitimate second year. Kept because Spring Data validates the property
     * path at startup, which is a free check that these column names still
     * exist.
     */
    boolean existsByDepartmentIdAndNumber(Long departmentId, int number);
}
