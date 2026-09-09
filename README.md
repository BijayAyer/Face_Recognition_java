# Face Recognition Attendance System (FRAS)

FRAS is a Java-based attendance system with a JavaFX desktop client and a
Spring Boot REST API. It supports account-based access, student and teacher
management, academic setup, attendance tracking, face-recognition-assisted
marking, and Excel/PDF attendance exports.

## Features

- JavaFX desktop interface for administrators, teachers, and students
- Spring Boot REST API secured with JWT authentication
- Student, teacher, subject, classroom, semester, and timetable management
- Manual and face-recognition attendance marking
- Attendance registers, summaries, and reports
- Excel (`.xlsx`) and PDF report export
- H2 file database for local development, with an optional MySQL profile
- OpenCV-based face detection and recognition

## Requirements

- JDK 21
- Maven 3.9+ (or use the included Maven Wrapper)
- A webcam for live face-recognition features
- MySQL 8+ only if using the MySQL profile

Check your Java installation:

```bash
java -version
```

## Run locally

The desktop client and backend are separate processes. Start the backend first,
then open the client in another terminal.

### 1. Start the backend

```bash
./mvnw spring-boot:run
```

The API starts at `http://localhost:8080`. By default, it stores local data in
an H2 database under `data/`.

On the first run, FRAS creates an administrator account. Set a known password
before starting the server:

```bash
export FRAS_ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

Optional environment settings:

```bash
export FRAS_JWT_SECRET='a-secret-with-at-least-32-characters'
export FRAS_ADMIN_EMAIL='admin@example.com'
export FRAS_REGISTRATION_CODE='staff-registration-code'
```

### 2. Start the desktop client

In a second terminal, from the project directory:

```bash
./mvnw javafx:run
```

## Build and test

```bash
./mvnw test
./mvnw package
```

## Use MySQL instead of H2

Create the database and a user, then provide credentials through environment
variables. The repository does not store database passwords.

```bash
export MYSQL_HOST=localhost
export MYSQL_PORT=3306
export MYSQL_DB=school_db
export MYSQL_USER=fras
export MYSQL_PASSWORD='your-password'
./mvnw spring-boot:run -Dspring-boot.run.profiles=mysql
```

## Project layout

```text
src/main/java/com/school/...  Spring Boot accounts, security, and user management
src/main/java/com/attendance/... Attendance services, reports, exports, and API
src/main/java/com/fras/app/... JavaFX desktop application
src/main/java/com/fras/face/... OpenCV face detection and recognition
src/main/resources/... Application configuration, FXML views, CSS, and models
```

## Security notes

- Do not commit `data/`; it can contain local account data and biometric images.
- Configure `FRAS_JWT_SECRET` for a stable production session-signing key.
- Configure `FRAS_ADMIN_PASSWORD` before the first startup.
- Restrict access to the machine and database that store facial data.

## Troubleshooting

**The desktop app cannot sign in:** confirm the backend is running at
`http://localhost:8080` before launching JavaFX.

**Camera or OpenCV errors:** verify operating-system camera permissions and
that the camera is not being used by another application.

**Build error:** confirm JDK 21 is active, then run:

```bash
./mvnw clean test
```

## License

No license has been specified for this repository.
