package com.fras.dao.impl;

import com.fras.dao.TimetableDAO;
import com.fras.model.Timetable;
import com.fras.service.ApiService;

import java.util.List;
import java.util.Objects;

/**
 * Timetable entries, over {@code /academic/timetables}.
 *
 * <p>Blocking. Call from a background thread; the screens use
 * {@link com.fras.ui.Async}.
 */
public class TimetableDAOImpl implements TimetableDAO {

    private final ApiService apiService = ApiService.getShared();

    @Override
    public void save(Timetable timetable) {
        try {
            apiService.post("/academic/timetables", AcademicJson.toJson(timetable));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("add the timetable entry", e);
        }
    }

    @Override
    public void update(Timetable timetable) {
        try {
            apiService.put("/academic/timetables/" + timetable.getId(),
                    AcademicJson.toJson(timetable));
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("update the timetable entry", e);
        }
    }

    @Override
    public void delete(Long id) {
        try {
            apiService.delete("/academic/timetables/" + id);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("delete the timetable entry", e);
        }
    }

    @Override
    public Timetable findById(Long id) {
        return findAll().stream()
                .filter(t -> t.getId() != null && t.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Timetable> findAll() {
        try {
            return AcademicJson.parseList(apiService.get("/academic/timetables"),
                    Timetable.class);
        } catch (ApiService.ApiException e) {
            throw AcademicJson.failed("load the timetable", e);
        }
    }

    /**
     * Whether this entry would double-book its classroom.
     *
     * <p><b>This is no longer the only thing enforcing the rule.</b>
     * {@code TimetableRestController} refuses an overlapping booking on the
     * server, which is where it has to be: this check reads the timetable and
     * compares against the copy it just fetched, so two people booking the same
     * room at the same moment both see it free, and anything that is not this
     * client is not asking at all. What it still buys is a refusal without a
     * round trip, with the same message. The two tests are deliberately
     * identical - half-open, so a class ending at 10:00 and one starting at 10:00
     * do not overlap - because a client that disagreed with the server about the
     * same booking would be worse than a client that did not check.
     *
     * <p><b>It fails closed.</b> It works by reading the whole timetable, and
     * {@link #findAll()} used to answer a failed read with an empty list - so a
     * server that could not be reached produced a loop with nothing to compare
     * against, this method returned false, and {@code TimetableServiceImpl} took
     * that as clearance and wrote the booking. The one case the check exists for
     * was the case it could not see. A failed read now throws out of here, and
     * the entry is refused with the reason.
     */
    @Override
    public boolean hasClassroomConflict(Timetable candidate) {

        for (Timetable existing : findAll()) {

            // Skip the row being edited. Objects.equals, not equals: a new entry
            // has no id yet, and existing rows from the server always do.
            if (Objects.equals(existing.getId(), candidate.getId())) {
                continue;
            }

            // A row missing any of these cannot be compared, so it is treated as
            // no obstacle rather than thrown over. Every one of these fields is
            // now non-null in the schema and validated on the way in, so this
            // needs a row that predates those - but the alternative is a
            // NullPointerException surfacing as an unreadable toast on a booking
            // that has nothing wrong with it. The server's overlaps(..) is
            // careful in the same way, for the same reason.
            if (!comparable(existing) || !comparable(candidate)) {
                continue;
            }

            boolean sameRoom = existing.getClassroom().getId()
                    .equals(candidate.getClassroom().getId());
            boolean sameDay = existing.getDay().equalsIgnoreCase(candidate.getDay());
            boolean overlaps = candidate.getStartTime().isBefore(existing.getEndTime())
                    && existing.getStartTime().isBefore(candidate.getEndTime());

            if (sameRoom && sameDay && overlaps) {
                return true;
            }
        }

        return false;
    }

    /** Whether every field the overlap test reads is present. */
    private static boolean comparable(Timetable entry) {
        return entry != null
                && entry.getClassroom() != null
                && entry.getClassroom().getId() != null
                && entry.getDay() != null
                && entry.getStartTime() != null
                && entry.getEndTime() != null;
    }
}
