# FRAS - where the project actually stands

Written 5 September 2026. This document supersedes `BUG_REPORT.md`,
`FIXES_APPLIED.md`, `FINAL_STATUS_REPORT.md`, `QUICK_START.md` and `SUMMARY.txt`,
all of which describe a five-bug review and report zero defects remaining. That
count was too low by an order of magnitude, and the two most serious problems in
the codebase were not among the five. Those files are kept for the record with a
notice at the top of each; nothing below depends on them.

## What was wrong

The worst defect was in the attendance endpoints. They were guarded by
`authenticated()` and nothing else, and sign-up is public, so the real boundary
was "anyone who fills in a form". Because `studentId` is a sequential database id
supplied by the caller, a single account could read every student's complete
attendance history by counting upwards from one. A URL pattern cannot express the
missing rule, because the rule depends on the value of a path variable rather
than its shape, so the check now lives in `AttendanceAccessPolicy`, is called
from the controller, and refuses an id that does not exist in exactly the same
words as an id belonging to somebody else - otherwise the endpoint would answer
"which ids are real?" for anyone willing to iterate.

Public sign-up was the second. `POST /auth/register` took the `role` field at
face value, so anyone who could reach the endpoint could mint themselves an
administrator by adding one line to a JSON body: no code, no approval, no trace.
The role is now decided by the server, and a privileged role requires
`app.security.privileged-registration-code`, which ships blank and therefore
refuses.

Then a class of failure that left no trace at all. `Attendance` keeps
`student_id`, `subject_id` and `classroom_id` as plain `Long` columns with no
foreign keys, so deleting a student, a subject or a room succeeded and left every
attendance row for it pointing at an id that named nothing - counted in totals,
attributable to nobody, unreadable in every export, for as long as the database
lived. A term's register could be lost as a side effect of tidying a dropdown.
Nothing at any layer would have refused it. Those deletes are now pre-checked and
refused with a sentence that says what is in the way and how much of it. Where a
foreign key did exist, the database had always refused, but the message reaching
the user was "that record is still referenced by other data", by which point the
number and the reason had been discarded; those now say how many semesters, how
many subjects, how many classes.

Sign-up also decided the wrong thing about who you are. `POST /auth/register`
took the role from the request only to overwrite it: the server picked STUDENT
for every caller, so choosing "Teacher" on the sign-up form produced a student
account and said nothing. A teacher was reachable only by an administrator
editing the account afterwards, which nobody knew to do. Registration now honours
a staff role when the request carries
`app.security.privileged-registration-code`, and refuses it in one sentence that
names the code when it does not - the answer is now either the account you asked
for or a reason, never a different account.

And the accounts were not joined to the roster at all. `users` and `students`
were matched by email address, which meant a student's own attendance was found
by string comparison and changing either address silently broke the link.
`Student` and `Teacher` now each carry a unique `user_id`; signing up creates
the roster row, adopts an existing row that already has the address, and
promoting a student to staff creates the staff row. `AttendanceAccessPolicy`
prefers the account link and keeps the email as a fallback for rows predating it,
which is what `theAccountLinkOutranksTheEmail` pins down.

Then the first run against a database with real rows in it would not start, and
the cause was neither the code nor the data but the gap between them. `Student`
used to carry `@Min(3)` on `age`. Bean Validation constraints are exported into
the DDL, so a database created while that annotation was in place holds
`CHECK ("AGE" >= 3 AND "AGE" <= 120)` on `students`, and `ddl-auto=update` adds
missing tables, columns and unique keys but never revisits a check constraint it
wrote earlier. The annotation now allows 0, because 0 is how the roster records
an age nobody supplied - the only honest value for a row created automatically
at sign-up, where there is nobody to ask. So the entity permitted 0, the table
refused it, and nothing reconciled the two. The start-up linking pass tried to
write a roster row and got back `Check constraint violation: "CONSTRAINT_9E: "`
- a name H2 invents and reports with an empty message, so the log could not even
say which rule had been broken. It took the backend down on boot, and it would
have taken every student sign-up down with it: `register` is transactional and
the roster row is written inside it, so the account would have rolled back too
and answered 500. `SchemaConstraintRepair` now runs ahead of the rest of the
bootstrap, compares the numbers in each recorded clause against the range
`Student` declares as constants, drops what disagrees and installs what is
missing. Reading the bounds from the entity rather than repeating them is the
point: it corrects itself if they change again, and it leaves alone the
identical constraint Hibernate writes for itself on a fresh database.

The same failure showed that the bootstrap could kill the application, which is
a separate defect. `run` was transactional and the linking loop had no error
handling, so one account whose row could not be written threw out of the runner,
rolled back the seeding that had already succeeded, and Spring Boot exited. A
repair pass that takes the backend down with it is worse than no repair pass,
because what it was repairing was survivable and a backend that will not boot is
not. Each account is now attempted on its own, and a failure is one warning, one
account left unlinked, and a server that is still serving.

Double-booking a room was checked only in the JavaFX client, which compared each
new booking against the copy of the timetable it happened to be holding. Two
people booking at the same moment both saw the room free; a stale tab saw a
timetable from whenever it was opened; and anything that was not this client -
`curl` with an admin token - never asked. The rule is now enforced in
`TimetableRestController`, treating each class as half-open so that back-to-back
lectures do not clash, and skipping the row being edited so a booking cannot
collide with itself. The client keeps its own check as a fast path.

The JVM was dying outright, twice in two days, both times with SIGSEGV inside
`objc_msgSend` under OpenCV's `VideoCapture.read` on `fras-camera-thread`. The
cause was not the camera. `stop()` set `running = false`, called `shutdownNow()`
and released the capture device on the next line - but `shutdownNow()` only
interrupts, and a blocking native read ignores interrupts, so the camera thread
could still be inside `read` on a handle the JavaFX thread had just freed. That
is a use-after-free in native code, which ends the process rather than throwing
something a `catch` block could see, and it presented as an occasional random
crash when leaving the live screen or closing the window. `stop()` now stops
scheduling, waits for the pass in flight to finish, and only then releases; if
the wait times out it deliberately leaves the device open, because a handle
leaked until the process exits is recoverable and a crash is not. The same
condition now gates the camera thread's own frame and detection buffers, which
were being freed on the timeout path while `drawResults` could still be walking
them.

Alongside those: attendance writes went through without a transaction and
swallowed their own errors, so a partially written register reported success; the
PDF export never closed its content stream and the Excel export built a fresh
`CellStyle` for every row, which is a documented way to exhaust a workbook's
style table; the face pipeline leaked native `Mat` buffers ten times a second and
rebuilt a 37 MB recogniser on every visit to the live screen, keeping the
previous copy alive with no way to release it; enrolment templates had the
overlay rectangle and label baked into them, which handicaps every later match
rather than merely looking untidy; and unauthenticated requests were answered 403
instead of 401, which left the desktop client unable to tell an expired token
from a permission problem.

## What changed in the client

The Overview screen used to be twelve outlined rectangles - four metric tiles, a
bordered panel and seven bordered cards - on the one screen whose job is to be
read at a glance, and it still left features out. It is now type separated by
hairlines: a figure strip, one sentence saying whether anything needs attention,
and one row per feature grouped by what the signed-in role can actually do, built
from `canRecordAttendance()` and `isAdmin()` so nothing appears that would refuse
on arrival.

The whole interface is black and white. There is no hue anywhere in
`css/app.css`: every value is a pure grey, which means the question each rule
answers is how loud something should be rather than what colour it is. Where hue
had been the only signal it was replaced with something that survives a
colour-blind reader, a photocopier and a dim projector - border width for a
refused field, shape for the camera lamp, fill-versus-outline for a destructive
button, weight and width for toast severity. Attendance is the clearest case:
ABSENT is a solid near-black field, LATE the only outlined state, PRESENT a pale
field, so attention rises with fill weight.

Two places drew in colour outside the stylesheet and no CSS change could have
reached them. The OpenCV overlay on the live video is now two inks and three
shapes - corner brackets while detecting, a plain box when recognised, a
double-ruled box with a filled label plate when not - with every stroke drawn
dark underneath and light on top, because a camera frame is the one surface whose
background cannot be predicted. And both export writers flagged low attendance in
red, which is the worst place for it: a register is printed, and on a mono printer
or a photocopy a red figure is just a grey figure, so the flag that mattered most
was the one most likely to be lost. Both now use weight, and the spreadsheet adds
an underline and a fill.

## The register says who, not which row

Every attendance read endpoint returned the stored rows unchanged, and
`Attendance` keeps the student, the subject and the room as bare `Long` columns,
so the register printed `2 | Student 1 | Room 2 | Subject 2` while the two
dropdowns directly above it read `cse212 - oop` and `404, Hall`. The one screen
whose purpose is to be read by a person was naming records by their position in a
table. The three read endpoints now return `RegisterEntry`, and `RegisterView`
resolves the ids in bulk - three `findAllById` calls for a whole register, not a
lookup per row - so all three attendance tables in the client show the student,
the subject and the room by name. Not every id went: the student id stayed,
because it is also the folder a face is enrolled under and the number the Enrol
screen asks for.

The exported register was worse than the screen. It was five cells - four counts
and a comma-separated list of absent ids - so a file handed to somebody else
reported how many people missed a lecture without saying who, and the students
who attended did not appear at all. Both writers now put one line per student
under a heading that names the session, in the order the roster was sent, with a
line for a student who was never marked as well as for one who was.

Section is new. Nothing in the codebase had a concept of a section, group or
division, so it is a nullable 40-character field on `Student`: optional
everywhere, recorded on the student form, read on the roster, the register and
both exports. Nothing computes from it - it is a label, so a school can call one
group "A" and another "BSc CSIT 3rd Sem" - which is why nothing validates its
shape beyond the length the column holds. `ddl-auto=update` adds a missing
column, which is why this is safe on a database that already has rows in it: the
mechanism that cannot fix a moved constraint adds a new nullable column without
complaint.

Editing a roster row was impossible until now, which is how a section gets onto
the students who are already there. `PUT /students/{id}` and `PUT /teachers/{id}`
had existed the whole time with nothing calling them, so a mistyped email or a
student who changed group meant deleting the row and adding it again - refused
outright for a student with attendance on file, and for anybody else throwing
away the id their face was enrolled under and the link to their sign-in account.
Both rosters now have an Edit button, open a row on a double-click, and keep the
selection across the reload that follows a save.

## What is verified, and how

There are 52 integration tests across six suites under
`src/test/java/com/fras/test/`, covering sign-up and sign-in, role assignment and
account-to-roster linking, the attendance access rules, roster read and write
permissions, the delete refusals, the timetable overlap rule and the schema
repair. They go through the real Spring Security filter chain with real JWTs
rather than a stubbed principal, because a stubbed principal would test the
assertion and skip the mechanism - and the mechanism is what was broken. Every
asserted status code and message fragment was read out of
`GlobalExceptionHandler`, `SecurityConfig` and the throw sites rather than
assumed.

`SchemaConstraintRepairTest` is the one suite that touches DDL rather than rows,
and it has to manufacture the stale constraint by hand: a test schema is built
from the current annotations and is correct by construction, so a test that only
ran against a fresh database would pass whatever the repair did. It installs the
old `age >= 3` rule, proves an unrecorded age is refused, runs the repair, and
proves the same insert now succeeds - then proves age 121 is still refused by the
database, so the protection was replaced rather than deleted. That first refusal
matters: `run` logs and swallows every failure, so without it a repair that had
inspected nothing would look identical in the log.

Structural checks pass over all 146 Java sources: balanced braces, ASCII-only
content, no unused imports, no leftover edit markers. The stylesheet is checked
the same way: braces balanced, all 37 hex values confirmed pure grey, no named
colour keywords, every one of its 32 tokens both defined and used, and every
style class named in Java or FXML resolving to a rule. All five FXML files parse,
their controllers exist, and every `fx:id` and handler binds to a real field and
method.

Nothing here was compiled or executed. The environment this work was done in has
JDK 11, no Maven and no network, and the project needs Java 21, JavaFX 21 and a
camera. Everything above is a change to source that has been read back and
checked structurally, which is not the same as a green build.

## What you need to run

Compile and run the tests first, because neither needs a database, a camera or a
running server:

    mvn clean compile
    mvn test

The tests use an in-memory H2 database configured in
`src/test/resources/application.properties`.

The application is two programs from one tree, and both have to be running. The
backend serves HTTP on port 8080; the JavaFX client reaches it at
`http://localhost:8080`, overridable with the `FRAS_API_URL` environment
variable. In two terminals:

    mvn spring-boot:run
    mvn clean javafx:run

`spring-boot-maven-plugin` previously had its `mainClass` set to
`com.fras.app.Launcher`, the desktop entry point. `MainApplication` does not
start a Spring context, so that made `mvn spring-boot:run` and `java -jar` on
the repackaged jar both try to launch a JavaFX application with no module path
and no server behind it. It now points at the backend, which is what that plugin
is for; the client keeps its own entry point under `javafx-maven-plugin`. If you
were using `spring-boot:run` to get the window, use `javafx:run` for that now.

Worth setting before a real run: `FRAS_JWT_SECRET`, to at least 32 characters.
Without it the backend logs `app.jwt.secret is not set - generating a random
signing key for this process` and does exactly that, which is safe but means
every token stops working when the server restarts, so everybody who was signed
in is signed out by a restart.

## Known limits, still open

The schema is managed by `ddl-auto=update`, which is not a migration tool. It
adds what is missing and changes nothing that already exists, so any constraint
whose definition moves - a validation bound, a column length, a uniqueness rule
- leaves the database saying one thing and the entity another, with no error
until something writes a row that falls in the gap. That gap is what broke
start-up, and `SchemaConstraintRepair` closes exactly one instance of it. The
real answer is Flyway or Liquibase, with the current schema captured as a
baseline; until then, changing a constraint on an entity means checking whether
an existing database still agrees with it.

The overlap check reads and then inserts inside one transaction, but at the
default isolation level two transactions can still interleave between the check
and the commit, and no unique index can express "these two time ranges overlap".
Closing that last gap needs a database-level exclusion constraint.

Enrolled face samples under `data/faces/` are files on disk, not rows, so
deleting a student does not remove their biometric samples. That is a data
protection question rather than a correctness one, and it is unresolved.

Deleting a roster row that has a sign-in account linked to it is still allowed,
and leaves the account with no profile behind it - a login that is a STUDENT or a
TEACHER with no roster row to be. The obvious fix is to refuse the delete, and it
is the wrong one while there is no way to delete an account: `/auth/users` can
create, enable, disable and change a role, but not remove, so refusing would make
those rows permanently undeletable. Closing this properly means an account
delete, or a delete that takes the account with it, and both are decisions about
records rather than code.

One permission got narrower rather than wider, so it is worth stating plainly. A
teacher may still read the roster - the Enrol screen needs it to know which id a
face belongs to - but POST, PUT and DELETE on `/students/**` moved from
ADMIN-or-TEACHER to ADMIN only. A roster row now owns the link to a sign-in
account, the id a face was enrolled under, and every attendance record pointing at
that id, so removing one is a records deletion rather than a roster edit. It also
matches what `/teachers/**` already required and what the client already showed:
the Students and Teachers pages are only in the rail for an administrator, so no
screen lost a button. Eight tests in `RosterAccessTest` hold it in place. If you
were relying on a teacher account to add students, that now needs an admin.

`src/main/resources/models/` still ships `deploy.prototxt` and
`res10_300x300_ssd_iter_140000_fp16.caffemodel`, the Caffe SSD detector that
YuNet replaced. Nothing references either, and they add 5.4 MB to every build.
They are safe to delete.

The two `hs_err_pid*.log` files in the project root are the crash reports
described above. They are now ignored by git and can be deleted once you are
satisfied the crash is gone.
