package com.fras.web;

import com.attendance.repository.AttendanceRepository;
import com.fras.model.Classroom;
import com.fras.repository.ClassroomRepository;
import com.fras.repository.TimetableRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * REST endpoints for the Academic Setup "Classrooms" screen. Replaces
 * the old ClassroomDAOImpl in-memory list.
 *
 * <p>Same three corrections as {@link DepartmentRestController}: {@code @Valid}
 * so the constraints on {@link Classroom} actually run, a named duplicate
 * instead of an unrecognised index name, and a case-insensitive comparison so
 * "b12" and "B12" are one room rather than two.
 */
@RestController
@RequestMapping("/academic/classrooms")
public class ClassroomRestController {

    private final ClassroomRepository classroomRepository;
    private final TimetableRepository timetableRepository;
    private final AttendanceRepository attendanceRepository;

    public ClassroomRestController(ClassroomRepository classroomRepository,
                                   TimetableRepository timetableRepository,
                                   AttendanceRepository attendanceRepository) {
        this.classroomRepository = classroomRepository;
        this.timetableRepository = timetableRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Classroom> getAll() {
        return classroomRepository.findAllByOrderByRoomNumberAsc();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Classroom> create(@Valid @RequestBody Classroom classroom) {
        tidy(classroom);
        requireRoomNumberFree(classroom.getRoomNumber(), null);

        classroom.setId(null);
        Classroom saved = classroomRepository.save(classroom);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Classroom> update(@PathVariable Long id,
                                           @Valid @RequestBody Classroom classroom) {
        if (!classroomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tidy(classroom);
        requireRoomNumberFree(classroom.getRoomNumber(), id);

        classroom.setId(id);
        return ResponseEntity.ok(classroomRepository.save(classroom));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!classroomRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        requireNotInUse(id);
        classroomRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Refuses to delete a classroom that is still booked or that has attendance
     * taken in it. The reasoning, including why the attendance half is the
     * important one, is on {@code SubjectRestController.requireNotInUse}.
     */
    private void requireNotInUse(Long classroomId) {
        long classes = timetableRepository.countByClassroomId(classroomId);
        if (classes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This room is still booked on the timetable ("
                            + Plural.of(classes, "class", "classes")
                            + "). Remove those first.");
        }
        long records = attendanceRepository.countByClassroomId(classroomId);
        if (records > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This room has " + Plural.of(records, "attendance record")
                            + " taken in it. Deleting it would leave them unreadable, "
                            + "so it cannot be removed.");
        }
    }

    private static void tidy(Classroom classroom) {
        classroom.setRoomNumber(trim(classroom.getRoomNumber()));
        classroom.setBuilding(trim(classroom.getBuilding()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * @param keepId the classroom being updated, whose own room number does not
     *               count as a clash; null when creating
     */
    private void requireRoomNumberFree(String roomNumber, Long keepId) {
        if (roomNumber == null || roomNumber.isBlank()) {
            return;
        }
        classroomRepository.findByRoomNumberIgnoreCase(roomNumber).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), keepId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Room " + roomNumber + " already exists in "
                                + existing.getBuilding() + ".");
            }
        });
    }
}
