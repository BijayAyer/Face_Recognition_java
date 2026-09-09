package com.fras.test;

import com.attendance.entity.Attendance;
import com.attendance.entity.MarkedBy;
import com.attendance.entity.Status;
import com.attendance.repository.AttendanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fras.model.Classroom;
import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.repository.ClassroomRepository;
import com.fras.repository.DepartmentRepository;
import com.fras.repository.SemesterRepository;
import com.fras.repository.SubjectRepository;
import com.fras.repository.TimetableRepository;
import com.school.school_management_system.SchoolManagementSystemApplication;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.Student;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.repository.StudentRepository;
import com.school.school_management_system.repository.TeacherRepository;
import com.school.school_management_system.repository.UserRepository;
import com.school.school_management_system.security.CustomUserDetails;
import com.school.school_management_system.security.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * What every test here needs: a wired-up MockMvc that goes through the real
 * security filter chain, a clean database, and a way to become a particular
 * kind of account.
 *
 * <p><b>Tokens are real.</b> Nothing here stubs the principal in. A test asks
 * for a bearer token, the JWT filter parses it, {@code CustomUserDetailsService}
 * loads the account and the authorities come out of the database - which is the
 * only way a test can prove that {@code SecurityConfig}'s matchers and
 * {@code AttendanceAccessPolicy} actually hold. Stubbing the authentication
 * would test the assertion and skip the mechanism.
 *
 * <p><b>The database is wiped rather than rolled back.</b> A test transaction
 * would be simpler, but half of what is under test throws deliberately -
 * refused deletes, refused bookings - and an exception crossing a joined
 * transaction marks it rollback-only, which turns a passing assertion into an
 * obscure failure at commit. Deleting in child-to-parent order costs a few
 * milliseconds and has no such edges.
 * <p><b>The application class is named explicitly.</b> These tests live under
 * {@code com.fras.test}, and {@code @SpringBootTest} finds its configuration by
 * walking <em>up</em> the package tree - which never reaches
 * {@code com.school.school_management_system}. Without the {@code classes}
 * attribute the context would simply fail to start, with an error that reads
 * like a missing annotation rather than a package-layout problem. It is named
 * here once, in the base class, so no individual test has to know that.
 */
@SpringBootTest(classes = SchoolManagementSystemApplication.class)
@AutoConfigureMockMvc
abstract class ApiTestSupport {

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected JwtUtil jwt;
    @Autowired protected PasswordEncoder passwordEncoder;

    @Autowired protected UserRepository users;
    @Autowired protected StudentRepository students;
    @Autowired protected TeacherRepository teachers;
    @Autowired protected AttendanceRepository attendance;
    @Autowired protected DepartmentRepository departments;
    @Autowired protected SemesterRepository semesters;
    @Autowired protected SubjectRepository subjects;
    @Autowired protected ClassroomRepository classrooms;
    @Autowired protected TimetableRepository timetables;

    /** Children before parents, or the foreign keys refuse the tidy-up. */
    @BeforeEach
    void emptyTheDatabase() {
        timetables.deleteAll();
        attendance.deleteAll();
        subjects.deleteAll();
        semesters.deleteAll();
        departments.deleteAll();
        classrooms.deleteAll();
        students.deleteAll();
        // Creating an account now creates the roster or staff row that goes
        // with it, and both tables hold a unique email - so a leftover row
        // fails the next test's sign-up with a 409 rather than the fixture it
        // was actually asking about.
        teachers.deleteAll();
        users.deleteAll();
    }

    // =========================================================
    // ACCOUNTS
    // =========================================================

    /** Creates an enabled account and returns the {@code Authorization} value for it. */
    protected String bearerFor(String email, Role role) {
        return "Bearer " + jwt.generateToken(new CustomUserDetails(accountFor(email, role, "test-password-1")));
    }

    /**
     * The account for this address, created if it does not exist yet.
     *
     * <p>Idempotent deliberately. It used to insert unconditionally, so asking
     * twice for the same person's token - {@code teacher()} in front of two
     * consecutive requests, which is how anyone would write it - hit the unique
     * index on {@code users.email} and failed with a constraint violation
     * naming the fixture rather than anything under test. That is the worst
     * shape a test failure can take: it points at the wrong file.
     *
     * <p>Looked up case-insensitively because {@link User#setEmail} lower-cases
     * on write, so a fixture asking for {@code Teacher@fras.test} would
     * otherwise miss the row it just created and try to insert a second one.
     *
     * <p>One address is one account here, exactly as in the application. A
     * second call with a different role reassigns it rather than duplicating
     * the person, which is what the caller asked for - authorities are read
     * from the database on every request, not baked into the token.
     */
    protected User accountFor(String email, Role role, String password) {
        User user = users.findByEmailIgnoreCase(email).orElseGet(User::new);
        user.setEmail(email);
        user.setFullName(role + " account");
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setRole(role);
        user.setEnabled(Boolean.TRUE);
        return users.save(user);
    }

    // =========================================================
    // ROSTER AND ACADEMIC FIXTURES
    // =========================================================

    protected Student student(String name, String email) {
        return students.save(new Student(null, name, email, 20));
    }

    /**
     * The roster row an account would have been given at sign-up. Used by tests
     * that need the link to already exist before they exercise something else,
     * without going through the register endpoint and making the test about
     * sign-up as well.
     */
    protected Student rosterRowFor(User account) {
        Student row = new Student(null, account.getDisplayName(), account.getEmail(), 20);
        row.setUserId(account.getId());
        return students.save(row);
    }

    protected Department department(String code, String name) {
        return departments.save(new Department(null, code, name, "Created by a test."));
    }

    protected Semester semester(Department department, String name, int number) {
        return semesters.save(new Semester(null, name, number, department));
    }

    protected Subject subject(Semester semester, String code, String name) {
        return subjects.save(new Subject(null, code, name, 3, semester));
    }

    protected Classroom classroom(String roomNumber) {
        return classrooms.save(new Classroom(null, roomNumber, "Main Building", 1, 40));
    }

    /**
     * One attendance row, written straight to the table.
     *
     * <p>Deliberately not via {@code /attendance/mark}: these fixtures exist to
     * put history in front of a delete, and going through the endpoint would
     * make a test about deleting a student also a test about recording
     * attendance.
     */
    protected Attendance attendanceFor(Long studentId, Long classroomId, Long subjectId, LocalDate date) {
        return attendance.save(new Attendance(studentId, classroomId, subjectId, date,
                LocalTime.of(9, 5), Status.PRESENT, MarkedBy.MANUAL_TEACHER, null));
    }
}


