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

## Frequently Asked Questions (Q&A)

### 1. What is FRAS?
FRAS stands for Face Recognition Attendance System. It is a Java-based application for managing academic records and recording attendance manually or with face recognition assistance.

### 2. Which technologies does FRAS use?
FRAS uses Java 21, JavaFX for the desktop interface, Spring Boot for the REST API, JWT for authentication, and OpenCV for face detection and recognition. It supports H2 and MySQL databases.

### 3. Who can use the system?
FRAS provides interfaces for administrators, teachers, and students. Available functions depend on the user's role and configured permissions.

### 4. How does face recognition assist attendance?
The system uses camera input and OpenCV to detect and recognize faces, helping associate attendance with registered students. Recognition quality can vary with lighting, camera quality, and face visibility.

### 5. What is the difference between face detection and face recognition?
Face detection locates a face in an image or camera frame. Face recognition attempts to determine whose face it is by comparing it with registered facial data.

### 6. Can attendance be recorded without a webcam?
Yes. FRAS supports manual attendance marking. A webcam is required for live face-recognition features.

### 7. Why must the backend and desktop client run separately?
The Spring Boot backend handles API requests and data services. The JavaFX client provides the user interface and communicates with the backend. Start the backend before launching the client.

### 8. Is MySQL required?
No. FRAS uses an H2 file database by default for local development. You can enable the optional MySQL profile after configuring a MySQL database and its connection settings.

### 9. Can attendance reports be exported?
Yes. FRAS supports attendance report exports in Excel (`.xlsx`) and PDF formats.

### 10. How do I configure the initial administrator account?
Set the `FRAS_ADMIN_PASSWORD` environment variable before the first backend startup. You can also configure the administrator email using `FRAS_ADMIN_EMAIL`.

### 11. How does authentication work?
The REST API uses JWT authentication. After signing in, the client uses an authentication token to access protected API resources.

### 12. Why can’t I sign in through the desktop application?
Confirm that the backend is running at `http://localhost:8080`. Then check your credentials and review the backend logs for errors.

### 13. What should I check if the camera does not work?
Check the operating system's camera permissions, confirm that the webcam is connected, and close other applications that may be using it. Also check the application logs for OpenCV errors.

### 14. How accurate is face recognition?
This README does not provide a verified accuracy benchmark. Evaluate recognition under realistic conditions, including different lighting, face angles, and camera distances, before relying on it for attendance.

### 15. How should facial data be protected?
Restrict access to the machine and database storing facial data. Do not commit the `data/` directory to version control, and establish appropriate consent, retention, and deletion practices before using real student data.

### 16. How do I build and test the project?
Use the included Maven Wrapper:

```bash
./mvnw test
./mvnw package
```

On Windows PowerShell, use:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

### 17. What improvements could be added in the future?
Possible improvements include evaluating recognition under difficult conditions, adding liveness detection, improving attendance correction history, and strengthening biometric data management. These are proposed enhancements, not confirmed current features.
./mvnw clean test
```

## License

No license has been specified for this repository.
