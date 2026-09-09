package com.fras.web;

import com.attendance.repository.AttendanceRepository;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.repository.SemesterRepository;
import com.fras.repository.SubjectRepository;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;

/**
 * REST endpoints for the Academic Setup "Subjects" screen. Replaces
 * the old SubjectDAOImpl in-memory list.
 *
 * <p>Carries both of the corrections made to its siblings: the duplicate subject
 * code is named rather than reported as an unrecognised index
 * ({@link DepartmentRestController}), and a bad semester reference comes back
 * with a sentence rather than an empty 400
 * ({@link SemesterRestController}).
 */
@RestController
@RequestMapping("/academic/subjects")
public class SubjectRestController {

    private final SubjectRepository subjectRepository;
    private final SemesterRepository semesterRepository;
    private final TimetableRepository timetableRepository;
    private final AttendanceRepository attendanceRepository;

    public SubjectRestController(SubjectRepository subjectRepository,
                                 SemesterRepository semesterRepository,
                                 TimetableRepository timetableRepository,
                                 AttendanceRepository attendanceRepository) {
        this.subjectRepository = subjectRepository;
        this.semesterRepository = semesterRepository;
        this.timetableRepository = timetableRepository;
        this.attendanceRepository = attendanceRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Subject> getAll(@RequestParam(required = false) Long semesterId) {
        if (semesterId != null) {
            return subjectRepository.findBySemesterId(semesterId);
        }
        return subjectRepository.findAllByOrderByNameAsc();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Subject> create(@Valid @RequestBody Subject subject) {
        tidy(subject);
        requireCodeFree(subject.getCode(), null);

        subject.setId(null);
        subject.setSemester(resolveSemester(subject));

        Subject saved = subjectRepository.save(subject);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Subject> update(@PathVariable Long id,
                                         @Valid @RequestBody Subject subject) {
        if (!subjectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tidy(subject);
        requireCodeFree(subject.getCode(), id);

        subject.setId(id);
        subject.setSemester(resolveSemester(subject));

        return ResponseEntity.ok(subjectRepository.save(subject));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!subjectRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        requireNotInUse(id);
        subjectRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Refuses to delete a subject that is still on the timetable or that has
     * attendance history.
     *
     * <p>The two halves of this are not the same kind of check. The timetable
     * holds a real foreign key, so the database would refuse that one anyway and
     * asking first only improves the sentence. Attendance holds
     * {@code subject_id} as a plain column with no foreign key, so <b>nothing</b>
     * would have refused the second one: the delete succeeded and every
     * attendance row for the subject was left pointing at an id that named
     * nothing, which no screen in the application can show and no export can
     * label. A term's register, quietly unreadable.
     *
     * <p>Refusing is the right answer rather than cascading. A subject is removed
     * because a timetable is being tidied; the attendance taken against it is a
     * record of who was in the room, and that is not tidy-up material. If a
     * subject really is finished with, its attendance can be exported first.
     */
    private void requireNotInUse(Long subjectId) {
        long classes = timetableRepository.countBySubjectId(subjectId);
        if (classes > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This subject is still on the timetable ("
                            + Plural.of(classes, "class", "classes")
                            + "). Remove those first.");
        }
        long records = attendanceRepository.countBySubjectId(subjectId);
        if (records > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This subject has " + Plural.of(records, "attendance record")
                            + " against it. Deleting it would leave them unreadable, "
                            + "so it cannot be removed.");
        }
    }

    private static void tidy(Subject subject) {
        subject.setCode(trim(subject.getCode()));
        subject.setName(trim(subject.getName()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * @param keepId the subject being updated, whose own code does not count as
     *               a clash; null when creating
     */
    private void requireCodeFree(String code, Long keepId) {
        if (code == null || code.isBlank()) {
            return;
        }
        subjectRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), keepId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The code " + code + " is already used by "
                                + existing.getName() + ".");
            }
        });
    }

    /**
     * Looked up again by id rather than trusted, for the reasons set out on
     * {@code SemesterRestController.resolveDepartment}: the nested copy the
     * client sends may be stale or partial, and in a request the client did not
     * make it may be anything at all.
     */
    private Semester resolveSemester(Subject subject) {
        Semester sent = subject.getSemester();
        Long semesterId = sent == null ? null : sent.getId();
        if (semesterId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say which semester this subject belongs to.");
        }
        return semesterRepository.findById(semesterId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That semester no longer exists. Refresh and try again."));
    }
}
