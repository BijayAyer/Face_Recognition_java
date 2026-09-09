package com.fras.web;

import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.repository.DepartmentRepository;
import com.fras.repository.SemesterRepository;
import com.fras.repository.SubjectRepository;
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

/**
 * REST endpoints for the Academic Setup "Semesters" screen. Replaces
 * the old SemesterDAOImpl in-memory list.
 *
 * <p>{@code @Valid} and the transactions are explained in
 * {@link DepartmentRestController}. What is specific here is the refusal: a
 * missing or unknown department used to be answered with
 * {@code ResponseEntity.badRequest().build()}, which sends a 400 with <em>no
 * body</em>. {@code ApiService} looks for the server's {@code message} field,
 * finds nothing, and falls back to "The request failed (HTTP 400)" - so the one
 * thing the user needed to know, which of the two things was wrong, was the one
 * thing that never arrived. Throwing instead routes through
 * {@code GlobalExceptionHandler} and gets the standard error body.
 *
 * <p>There is deliberately no uniqueness rule on (department, number). See
 * {@link SemesterRepository#existsByDepartmentIdAndNumber} for why: semester
 * names here are year-scoped, so a department has a "Semester 1" every year.
 */
@RestController
@RequestMapping("/academic/semesters")
public class SemesterRestController {

    private final SemesterRepository semesterRepository;
    private final DepartmentRepository departmentRepository;
    private final SubjectRepository subjectRepository;

    public SemesterRestController(SemesterRepository semesterRepository,
                                  DepartmentRepository departmentRepository,
                                  SubjectRepository subjectRepository) {
        this.semesterRepository = semesterRepository;
        this.departmentRepository = departmentRepository;
        this.subjectRepository = subjectRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Semester> getAll(@RequestParam(required = false) Long departmentId) {
        if (departmentId != null) {
            return semesterRepository.findByDepartmentId(departmentId);
        }
        return semesterRepository.findAllByOrderByNumberAsc();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Semester> create(@Valid @RequestBody Semester semester) {
        semester.setName(trim(semester.getName()));
        semester.setId(null);
        semester.setDepartment(resolveDepartment(semester));

        Semester saved = semesterRepository.save(semester);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Semester> update(@PathVariable Long id,
                                          @Valid @RequestBody Semester semester) {
        if (!semesterRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        semester.setName(trim(semester.getName()));
        semester.setId(id);
        semester.setDepartment(resolveDepartment(semester));

        return ResponseEntity.ok(semesterRepository.save(semester));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!semesterRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        requireNoSubjects(id);
        semesterRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Refuses to delete a semester that still has subjects in it, for the reasons
     * given on {@code DepartmentRestController.requireNoSemesters}.
     */
    private void requireNoSubjects(Long semesterId) {
        long subjects = subjectRepository.countBySemesterId(semesterId);
        if (subjects > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This semester still has " + Plural.of(subjects, "subject")
                            + ". Delete those first.");
        }
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * The client sends a whole Department object, the one selected in its combo
     * box, nested inside the Semester payload. It is looked up again by id
     * rather than trusted: the nested copy may be stale, may be partial, and in
     * a request not made by the client may be anything at all. Only the id is
     * read from it, and the row that id names is what gets stored.
     *
     * <p>{@code @NotNull} on the field has already rejected a payload with no
     * department, so what is left to report is an id that names nothing.
     */
    private Department resolveDepartment(Semester semester) {
        Department sent = semester.getDepartment();
        Long departmentId = sent == null ? null : sent.getId();
        if (departmentId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say which department this semester belongs to.");
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That department no longer exists. Refresh and try again."));
    }
}
