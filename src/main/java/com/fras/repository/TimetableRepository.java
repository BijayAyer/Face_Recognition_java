package com.fras.repository;

import com.fras.model.Timetable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Timetable rows.
 *
 * <p>Both row-returning finders declare an entity graph, and the reason is the
 * shape of the entity: {@code Timetable} holds two
 * {@code @ManyToOne(fetch = EAGER)} relations, one of which - the subject -
 * leads on to a semester and then to a department. EAGER does not mean joined.
 * On a query, as opposed to a {@code findById}, Hibernate returns the rows first
 * and then satisfies each eager relation with a select of its own, so a list of
 * 100 classes cost one query plus four hundred more, and this is the request the
 * timetable tab makes every time it is opened or refreshed. Naming the paths
 * turns all of it into a single select with four left joins.
 *
 * <p>Three finders that had no callers anywhere - {@code findByDayIgnoreCase},
 * {@code findByClassroomId} and {@code findBySubjectId} - were removed. The
 * timetable tab loads every row once and filters in memory, so nothing asked
 * those questions; the two that a delete needs to ask are answered by the counts
 * below, which want a number rather than four joined rows.
 */
public interface TimetableRepository extends JpaRepository<Timetable, Long> {

    @Override
    @EntityGraph(attributePaths = {
            "classroom", "subject", "subject.semester", "subject.semester.department"})
    List<Timetable> findAll();

    /**
     * Every class booked in one room on one weekday - the set an incoming
     * booking has to be checked against. Present but unused until the overlap
     * check moved to the server; before that, the client fetched the whole
     * timetable and did the comparison itself, which is no check at all once two
     * people are using the app.
     */
    @EntityGraph(attributePaths = {
            "classroom", "subject", "subject.semester", "subject.semester.department"})
    List<Timetable> findByClassroomIdAndDayIgnoreCase(Long classroomId, String day);

    /**
     * What a classroom is still booked for, and what a subject is still taught
     * as, asked before deleting either. Both are real foreign keys, so the
     * database would refuse the delete anyway - but only as
     * "referential integrity violation", which
     * {@code GlobalExceptionHandler.describeIntegrityViolation} can do no better
     * than "that record is still referenced by other data". By then the number
     * and the reason are gone. Asking first is what lets the refusal say how many
     * classes and where to remove them.
     */
    long countByClassroomId(Long classroomId);

    long countBySubjectId(Long subjectId);
}
