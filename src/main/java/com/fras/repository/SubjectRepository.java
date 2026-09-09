package com.fras.repository;

import com.fras.model.Subject;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Subjects.
 *
 * <p>The graphs are here for the same reason as in {@link TimetableRepository}:
 * a subject's semester is {@code EAGER}, and its department beyond that, so an
 * unqualified {@code findAll} issued one select for the subjects and then two
 * per row. The subject list is loaded by three separate screens - Subjects,
 * Timetable and the attendance session picker - so it is worth a join.
 */
public interface SubjectRepository extends JpaRepository<Subject, Long> {

    @Override
    @EntityGraph(attributePaths = {"semester", "semester.department"})
    List<Subject> findAll();

    @EntityGraph(attributePaths = {"semester", "semester.department"})
    List<Subject> findBySemesterId(Long semesterId);

    Optional<Subject> findByCodeIgnoreCase(String code);

    @EntityGraph(attributePaths = {"semester", "semester.department"})
    List<Subject> findAllByOrderByNameAsc();

    /**
     * How many subjects a semester still holds, asked before deleting it. A
     * count for the reason given on {@code SemesterRepository.countByDepartmentId}.
     */
    long countBySemesterId(Long semesterId);
}
