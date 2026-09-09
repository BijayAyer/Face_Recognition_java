package com.fras.repository;

import com.fras.model.Classroom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Classrooms.
 *
 * <p>{@code existsByRoomNumberIgnoreCase} was removed for the reason given on
 * {@link DepartmentRepository}: it had no callers and could not have served the
 * one that mattered, which needs to say which room already exists and in which
 * building.
 */
public interface ClassroomRepository extends JpaRepository<Classroom, Long> {

    Optional<Classroom> findByRoomNumberIgnoreCase(String roomNumber);

    List<Classroom> findAllByOrderByRoomNumberAsc();
}
