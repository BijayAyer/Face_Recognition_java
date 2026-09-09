package com.fras.web;

import com.fras.model.Classroom;
import com.fras.model.Subject;
import com.fras.model.Timetable;
import com.fras.repository.ClassroomRepository;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * REST endpoints for the Academic Setup "Timetable" screen. Replaces
 * the old TimetableDAOImpl in-memory list.
 *
 * <p>Double-booking is refused here, and that is the change worth explaining.
 * The check used to live only in the JavaFX client, which fetched the whole
 * timetable and compared the new booking against the copy it happened to be
 * holding. Three things follow from that, and all three are real:
 * <ul>
 *   <li><b>Two people defeat it.</b> Both clients read a timetable with the room
 *       free at nine, both decide the booking is fine, both post it. Neither
 *       ever saw the other's row.</li>
 *   <li><b>One stale tab defeats it.</b> The list is fetched when the tab opens.
 *       Anything booked after that is invisible to the comparison.</li>
 *   <li><b>Anything that is not the client defeats it.</b> {@code curl} with an
 *       admin token books whatever it likes.</li>
 * </ul>
 * The client keeps its own check, because refusing a clash without a round trip
 * is faster and the message is the same. It is now a convenience rather than
 * the only thing standing between two lecturers and the same room.
 *
 * <p>The write methods are {@code @Transactional} so the read that checks and
 * the insert that follows are one unit of work. Being honest about the limit:
 * at the default isolation level two transactions can still interleave between
 * the check and the commit, and no unique index can express "these two time
 * ranges overlap", so the last gap is closed by whoever is prepared to add a
 * database-level exclusion constraint. What is gone is the much larger hole -
 * that the rule was not enforced on the server at all.
 */
@RestController
@RequestMapping("/academic/timetables")
public class TimetableRestController {

    /** For times inside messages the user reads. */
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final TimetableRepository timetableRepository;
    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;

    /**
     * Serialises timetable writes for the same classroom inside this JVM.
     * Interval overlap cannot be represented by a normal unique index, so the
     * check-and-insert must not be allowed to interleave between two HTTP
     * requests. The backend is intentionally a single local JVM, making this a
     * practical last line of defence in addition to the transactional check.
     */
    private static final ConcurrentHashMap<Long, Object> ROOM_WRITE_LOCKS = new ConcurrentHashMap<>();

    public TimetableRestController(TimetableRepository timetableRepository,
                                   ClassroomRepository classroomRepository,
                                   SubjectRepository subjectRepository) {
        this.timetableRepository = timetableRepository;
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<Timetable> getAll() {
        return timetableRepository.findAll();
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Timetable> create(@Valid @RequestBody Timetable timetable) {
        resolveRelations(timetable);
        requireOrderedTimes(timetable);
        return withRoomLock(timetable.getClassroom().getId(), () -> {
            requireRoomFree(timetable, null);
            timetable.setId(null);
            Timetable saved = timetableRepository.save(timetable);
            return ResponseEntity.status(HttpStatus.CREATED).body(saved);
        });
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Timetable> update(@PathVariable Long id,
                                           @Valid @RequestBody Timetable timetable) {
        if (!timetableRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        resolveRelations(timetable);
        requireOrderedTimes(timetable);
        return withRoomLock(timetable.getClassroom().getId(), () -> {
            requireRoomFree(timetable, id);
            timetable.setId(id);
            return ResponseEntity.ok(timetableRepository.save(timetable));
        });
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!timetableRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        timetableRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * The client sends whole Classroom and Subject objects, the ones selected in
     * its combo boxes, nested inside the Timetable payload. Both are looked up
     * again by id and the stored rows replace what arrived: the nested copies may
     * be stale, may be partial, and in a request the client did not make may be
     * anything at all.
     *
     * <p>{@code @NotNull} on both fields has already rejected a payload that
     * omits them, so what is left to report is an id that names nothing.
     */
    private void resolveRelations(Timetable timetable) {
        Classroom sentClassroom = timetable.getClassroom();
        Long classroomId = sentClassroom == null ? null : sentClassroom.getId();
        if (classroomId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say which classroom this class is in.");
        }

        Subject sentSubject = timetable.getSubject();
        Long subjectId = sentSubject == null ? null : sentSubject.getId();
        if (subjectId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Say which subject this class teaches.");
        }

        timetable.setClassroom(classroomRepository.findById(classroomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That classroom no longer exists. Refresh and try again.")));
        timetable.setSubject(subjectRepository.findById(subjectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "That subject no longer exists. Refresh and try again.")));
    }

    /**
     * Not expressed as a constraint on the entity because it is a rule about two
     * fields together, and a property constraint would put the property's name in
     * the sentence the user reads.
     */
    private static void requireOrderedTimes(Timetable timetable) {
        LocalTime start = timetable.getStartTime();
        LocalTime end = timetable.getEndTime();
        if (start == null || end == null || !start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The class has to end after it starts.");
        }
    }

    /**
     * Refuses a booking that overlaps one already in the room on that weekday.
     *
     * <p>Only that room and that day are fetched, not the whole timetable - the
     * question is local, and the index on {@code (classroom_id, day_of_week)}
     * exists to answer it.
     *
     * @param keepId the row being updated, which cannot clash with itself; null
     *               when creating
     */
    private <T> T withRoomLock(Long classroomId, java.util.function.Supplier<T> action) {
        Object lock = ROOM_WRITE_LOCKS.computeIfAbsent(classroomId, ignored -> new Object());
        synchronized (lock) {
            try {
                return action.get();
            } finally {
                ROOM_WRITE_LOCKS.remove(classroomId, lock);
            }
        }
    }

    private void requireRoomFree(Timetable candidate, Long keepId) {
        Classroom room = candidate.getClassroom();
        List<Timetable> booked = timetableRepository
                .findByClassroomIdAndDayIgnoreCase(room.getId(), candidate.getDay());

        for (Timetable existing : booked) {
            if (Objects.equals(existing.getId(), keepId)) {
                continue;
            }
            if (!overlaps(candidate.getStartTime(), candidate.getEndTime(),
                    existing.getStartTime(), existing.getEndTime())) {
                continue;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Room " + room.getRoomNumber() + " is already booked on "
                            + candidate.getDay() + " from " + at(existing.getStartTime())
                            + " to " + at(existing.getEndTime())
                            + " for " + describe(existing.getSubject()) + ".");
        }
    }

    /**
     * Treats each class as half-open - it occupies its start instant and not its
     * end instant - so a class ending at 10:00 and one starting at 10:00 sit next
     * to each other rather than clashing. Back-to-back lectures are the normal
     * case, and a rule that refused them would be worse than no rule.
     *
     * <p>A row already stored with a null time cannot be compared, so it is
     * treated as no obstacle. Both columns are {@code nullable = false} and both
     * fields are {@code @NotNull}, so this needs a row that predates those; the
     * alternative is a {@code NullPointerException} out of a request that has
     * nothing wrong with it.
     */
    private static boolean overlaps(LocalTime start, LocalTime end,
                                    LocalTime otherStart, LocalTime otherEnd) {
        if (start == null || end == null || otherStart == null || otherEnd == null) {
            return false;
        }
        return start.isBefore(otherEnd) && otherStart.isBefore(end);
    }

    private static String at(LocalTime time) {
        return time == null ? "an unknown time" : time.format(HH_MM);
    }

    private static String describe(Subject subject) {
        if (subject == null) {
            return "another class";
        }
        return subject.getCode() == null || subject.getCode().isBlank()
                ? "another class"
                : subject.getCode();
    }
}
