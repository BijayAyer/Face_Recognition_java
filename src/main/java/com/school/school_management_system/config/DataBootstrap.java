package com.school.school_management_system.config;

import com.fras.model.Classroom;
import com.fras.model.Department;
import com.fras.model.Semester;
import com.fras.model.Subject;
import com.fras.repository.ClassroomRepository;
import com.fras.repository.DepartmentRepository;
import com.fras.repository.SemesterRepository;
import com.fras.repository.SubjectRepository;
import com.school.school_management_system.entity.Role;
import com.school.school_management_system.entity.User;
import com.school.school_management_system.repository.UserRepository;
import com.school.school_management_system.service.AccountProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * First-run bootstrap.
 *
 * <p>Three problems this solves. First, on a brand-new database there was no
 * way to obtain an administrator: every account had to come from the public
 * sign-up form, which is exactly why that form used to accept a
 * client-supplied role. Now sign-up can only produce a STUDENT and the very
 * first ADMIN is created here instead.
 *
 * <p>Second, the attendance screens need a classroom and a subject to point
 * at, and a fresh install had neither, so "Start Session" could not be used
 * at all. A single demo department/semester/subject/classroom is seeded when
 * those tables are empty.
 *
 * <p>Third, accounts that were created before an account carried a roster or
 * staff row are given one on the next start-up. Without that pass the repair
 * would only apply to people who sign up from now on, and every existing
 * student account would stay unenrollable and unable to see its own
 * attendance - which is the state the database is actually in.
 *
 * <p>Everything here is conditional on the relevant table being empty, or is
 * idempotent, so restarting the app never overwrites real data.
 *
 * <p><b>And none of it can stop the application starting.</b> That is not
 * defensive habit, it is a fix: {@code run} used to be {@code @Transactional}
 * and the linking pass had no error handling, so one account whose roster row
 * could not be written threw all the way out of the runner, rolled back the
 * seeding that had already succeeded, and Spring Boot exited. A repair pass
 * that takes the backend down with it is worse than no repair pass, because
 * the thing it was repairing was survivable and a backend that will not boot
 * is not. Each step is now atomic on its own - the seeds are guarded by their
 * own emptiness checks, and {@link AccountProfileService#linkOrCreate} carries
 * its own transaction - so a single failure is one warning and one account
 * left unlinked.
 */
@Component
@Order(20)
@ConditionalOnProperty(name = "app.bootstrap.enabled", havingValue = "true", matchIfMissing = true)
public class DataBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataBootstrap.class);

    /** Enough of a database message to identify the cause, without a wall of SQL. */
    private static final int CAUSE_LIMIT = 240;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final DepartmentRepository departmentRepository;
    private final SemesterRepository semesterRepository;
    private final SubjectRepository subjectRepository;
    private final ClassroomRepository classroomRepository;
    private final AccountProfileService accountProfiles;

    private final String adminEmail;
    private final String configuredAdminPassword;
    private final boolean seedAcademicData;

    public DataBootstrap(UserRepository userRepository,
                         PasswordEncoder passwordEncoder,
                         DepartmentRepository departmentRepository,
                         SemesterRepository semesterRepository,
                         SubjectRepository subjectRepository,
                         ClassroomRepository classroomRepository,
                         AccountProfileService accountProfiles,
                         @Value("${app.bootstrap.admin-email:admin@fras.local}") String adminEmail,
                         @Value("${app.bootstrap.admin-password:}") String configuredAdminPassword,
                         @Value("${app.bootstrap.sample-academic-data:true}") boolean seedAcademicData) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.departmentRepository = departmentRepository;
        this.semesterRepository = semesterRepository;
        this.subjectRepository = subjectRepository;
        this.classroomRepository = classroomRepository;
        this.accountProfiles = accountProfiles;
        this.adminEmail = adminEmail;
        this.configuredAdminPassword = configuredAdminPassword;
        this.seedAcademicData = seedAcademicData;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedAdministrator();
        if (seedAcademicData) {
            seedAcademicStructure();
        }
        linkExistingAccounts();
    }

    /**
     * Gives every account the row that represents the person, if it has not
     * got one. Idempotent: an account that is already linked costs one lookup
     * and changes nothing, so this runs on every start-up rather than being
     * gated on a flag that could drift out of step with the data.
     *
     * <p>Each account is attempted on its own. One that cannot be written -
     * because somebody typed an address longer than the column, or the schema
     * disagrees with the entity - is counted and named in a warning, and the
     * rest of the accounts still get their rows.
     */
    private void linkExistingAccounts() {
        int created = 0;
        int adopted = 0;
        int blocked = 0;
        int failed = 0;

        for (User user : userRepository.findAll()) {
            try {
                switch (accountProfiles.linkOrCreate(user)) {
                    case CREATED -> created++;
                    case ADOPTED -> adopted++;
                    case BLOCKED -> blocked++;
                    default -> { }
                }
            } catch (RuntimeException e) {
                // The id, not the address: this goes to a console log, and the
                // id is what an administrator needs to find the row anyway.
                failed++;
                log.warn("Could not give account {} its roster or staff row: {}",
                        user.getId(), rootCauseMessage(e));
            }
        }

        if (created > 0 || adopted > 0) {
            log.info("Accounts repaired on start-up: {} roster/staff row(s) created, {} existing row(s) linked.",
                    created, adopted);
        }
        if (blocked > 0) {
            log.warn("{} account(s) still have no roster or staff row: that email already belongs to a row"
                    + " linked to a different account. An administrator needs to correct one of the two"
                    + " addresses.", blocked);
        }
        if (failed > 0) {
            log.warn("{} account(s) could not be given a row at all - see the warnings above. The backend is"
                    + " running; until the cause is fixed those accounts cannot have a face enrolled and"
                    + " will not see their own attendance.", failed);
        }
    }

    /**
     * The innermost message. A constraint violation arrives wrapped three
     * deep, and only the bottom exception says which constraint - the outer
     * ones say "could not execute statement".
     */
    private static String rootCauseMessage(Throwable thrown) {
        Throwable cause = thrown;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null || message.isBlank()) {
            return cause.getClass().getSimpleName();
        }
        String flat = message.replaceAll("\\s+", " ").trim();
        return flat.length() <= CAUSE_LIMIT ? flat : flat.substring(0, CAUSE_LIMIT) + "...";
    }

    private void seedAdministrator() {
        if (userRepository.count() > 0) {
            if (userRepository.countByRole(Role.ADMIN) == 0) {
                log.warn("No ADMIN account exists. Promote one with: "
                        + "UPDATE users SET role = 'ADMIN' WHERE email = '<your-email>';");
            }
            return;
        }

        boolean generated = configuredAdminPassword == null || configuredAdminPassword.isBlank();
        String password = generated ? randomPassword() : configuredAdminPassword;

        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setFullName("Administrator");
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setRole(Role.ADMIN);
        admin.setEnabled(Boolean.TRUE);
        userRepository.save(admin);

        log.warn("");
        log.warn("=======================================================================");
        log.warn(" First run: created the initial administrator account.");
        log.warn("   email    : {}", admin.getEmail());
        if (generated) {
            log.warn("   password : {}", password);
            log.warn(" This password was generated randomly and is shown only once.");
            log.warn(" Change it after signing in, or set FRAS_ADMIN_PASSWORD and delete");
            log.warn(" the row to have a known one created instead.");
        } else {
            log.warn("   password : (taken from app.bootstrap.admin-password)");
        }
        log.warn("=======================================================================");
        log.warn("");
    }

    private void seedAcademicStructure() {
        if (departmentRepository.count() == 0) {
            Department department = new Department();
            department.setDepartmentCode("GEN");
            department.setDepartmentName("General Studies");
            department.setDescription("Auto-created on first run so the academic screens are usable.");
            department = departmentRepository.save(department);
            log.info("Seeded demo department '{}'.", department.getDepartmentCode());

            if (semesterRepository.count() == 0) {
                Semester semester = new Semester();
                semester.setName("Semester 1");
                semester.setNumber(1);
                semester.setDepartment(department);
                semester = semesterRepository.save(semester);

                if (subjectRepository.count() == 0) {
                    Subject subject = new Subject();
                    subject.setCode("GEN101");
                    subject.setName("Orientation");
                    subject.setCredit(3);
                    subject.setSemester(semester);
                    subjectRepository.save(subject);
                    log.info("Seeded demo subject 'GEN101'.");
                }
            }
        }

        if (classroomRepository.count() == 0) {
            Classroom classroom = new Classroom();
            classroom.setRoomNumber("R-101");
            classroom.setBuilding("Main Building");
            classroom.setFloor(1);
            classroom.setCapacity(40);
            classroomRepository.save(classroom);
            log.info("Seeded demo classroom 'R-101'.");
        }
    }

    /**
     * 18 random bytes rendered URL-safe: ~144 bits of entropy, no
     * ambiguous padding characters to mistype.
     */
    private String randomPassword() {
        byte[] bytes = new byte[18];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
