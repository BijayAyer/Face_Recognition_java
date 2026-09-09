package com.fras.web;

import com.fras.model.Department;
import com.fras.repository.DepartmentRepository;
import com.fras.repository.SemesterRepository;
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
 * REST endpoints for the Academic Setup "Departments" screen.
 *
 * <p>This replaces the DepartmentDAOImpl in-memory list that the JavaFX
 * client used to talk to - the client now calls these endpoints (via
 * ApiService) and everything is persisted through DepartmentRepository
 * into the real database.
 *
 * <p>Three things were added after the fact, and the same three apply to the
 * four sibling controllers in this package:
 * <ul>
 *   <li><b>{@code @Valid}.</b> Without it the constraints on {@link Department}
 *       do not run, and an empty-string code - which is not null, so
 *       {@code nullable = false} allows it - was stored as a blank row.</li>
 *   <li><b>The duplicate is named.</b> The unique index on
 *       {@code department_code} is declared with {@code unique = true} on the
 *       column, so it has no name of its own; H2 raises
 *       {@code CONSTRAINT_INDEX_x}, which
 *       {@code GlobalExceptionHandler.describeIntegrityViolation} cannot
 *       recognise, and the user was told only "that change conflicts with data
 *       already in the database". Checking first says which code, and which
 *       department already holds it. The index is still the authority: the
 *       check narrows a race, it does not close it, so a
 *       {@code DataIntegrityViolationException} arriving anyway is still a
 *       correct 409.</li>
 *   <li><b>Case.</b> The database compares codes exactly, so "cs" and "CS"
 *       were two departments. The check is case-insensitive, which is what a
 *       person means by a department code.</li>
 *   <li><b>A delete says what is in the way.</b> Every record in this hierarchy
 *       has something below it, and deleting a parent out from under its children
 *       is refused - correctly - by the foreign key. But a foreign key can only
 *       report a "referential integrity violation", which
 *       {@code GlobalExceptionHandler} turns into "that record is still
 *       referenced by other data": true, useless, and by then the number and the
 *       reason have been thrown away. Each delete now asks first and answers with
 *       the count and the next step. Where no foreign key exists at all - the
 *       attendance table, which keeps its subject, classroom and student as plain
 *       id columns - asking first is not a nicety but the only thing standing
 *       between a tidy-up and an orphaned term of attendance history; see
 *       {@code AttendanceRepository.countByStudentId}.</li>
 * </ul>
 */
@RestController
@RequestMapping("/academic/departments")
public class DepartmentRestController {

    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;

    public DepartmentRestController(DepartmentRepository departmentRepository,
                                    SemesterRepository semesterRepository) {
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Department> getAll() {
        return departmentRepository.findAllByOrderByDepartmentNameAsc();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<Department> getById(@PathVariable Long id) {
        return departmentRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Department> create(@Valid @RequestBody Department department) {
        tidy(department);
        requireCodeFree(department.getDepartmentCode(), null);

        department.setId(null);
        Department saved = departmentRepository.save(department);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Department> update(@PathVariable Long id,
                                            @Valid @RequestBody Department department) {
        if (!departmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        tidy(department);
        requireCodeFree(department.getDepartmentCode(), id);

        department.setId(id);
        return ResponseEntity.ok(departmentRepository.save(department));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!departmentRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        requireNoSemesters(id);
        departmentRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Refuses to delete a department that still has semesters under it.
     *
     * <p>{@code semesters.department_id} is {@code nullable = false}, so the
     * database would refuse this anyway. The difference is the sentence: the
     * refusal now carries the count and says what to remove first, rather than
     * leaving the user to guess which of four screens is holding the reference.
     */
    private void requireNoSemesters(Long departmentId) {
        long semesters = semesterRepository.countByDepartmentId(departmentId);
        if (semesters > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This department still has " + Plural.of(semesters, "semester")
                            + ". Delete those first.");
        }
    }

    /**
     * Trims the text fields. Surrounding space is invisible in a table, so
     * " CS" and "CS" looked identical while the unique index treated them as
     * two different codes.
     */
    private static void tidy(Department department) {
        department.setDepartmentCode(trim(department.getDepartmentCode()));
        department.setDepartmentName(trim(department.getDepartmentName()));
        department.setDescription(trim(department.getDescription()));
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    /**
     * @param keepId the department being updated, whose own code does not count
     *               as a clash; null when creating
     */
    private void requireCodeFree(String code, Long keepId) {
        if (code == null || code.isBlank()) {
            return;
        }
        departmentRepository.findByDepartmentCodeIgnoreCase(code).ifPresent(existing -> {
            if (!Objects.equals(existing.getId(), keepId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The code " + code + " is already used by "
                                + existing.getDepartmentName() + ".");
            }
        });
    }
}
