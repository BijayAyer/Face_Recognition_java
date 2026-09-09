package com.fras;

/**
 * Implemented by FXML controllers (Department/Semester/Subject/
 * Classroom/Timetable) so their host tab can tell them to reload
 * their data when the tab becomes visible.
 */
public interface Refreshable {
    void refreshData();
}
