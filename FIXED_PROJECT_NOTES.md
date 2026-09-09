# FRAS — Fixes in this package

This package contains the reviewed/fixed source tree.

## Fixed in this pass

- Attendance now validates that the referenced student, classroom, and subject exist before writing a record.
- Bulk absence closing rejects stale/non-existent student IDs instead of creating orphan attendance rows.
- Face-recognition confidence scores must be finite and within the valid cosine-similarity range.
- Timetable create/update checks are serialized per classroom inside the local backend JVM, closing the remaining check-then-save race for the intended single-machine deployment.
- Camera start is refused while a previous native frame pass is still unwinding after a stop timeout, preventing a new session from racing an old OpenCV `VideoCapture` operation.
- Runtime H2 database files and JVM crash dumps are excluded from the deliverable; they may contain local credentials/data and are not source files.

## Verification limitation

The source was statically reviewed and the Maven wrapper/project configuration was checked. Full Maven tests could not be executed in the build environment because Maven 3.9.16 was not available locally and the environment could not download it from Maven Central.

Before submission, run:

```bash
./mvnw clean test
./mvnw spring-boot:run
```

For the JavaFX client, use the project's configured JavaFX launch task after the backend is running.
