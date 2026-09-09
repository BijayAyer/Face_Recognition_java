> **Superseded.** See `PROJECT_STATUS.md`. This was a five-bug review, and the
> five are real - the SIGSEGV analysis below is sound. It is kept because it is
> the record of how the crash was found. Treat it as a snapshot of 2 September
> 2026 rather than as the list of what is wrong with the project: a later audit
> found considerably more, including a privilege escalation at sign-up and an
> IDOR on the attendance endpoints, neither of which appears here.

# FRAS Project - Bug Report & Analysis

**Date:** September 2, 2026  
**Project:** Face Recognition Attendance System (FRAS)  
**Status:** Critical issues identified - Database working, Camera integration failing

---

## 1. CRITICAL BUGS & ERRORS

### 🔴 Bug #1: OpenCV Native Crash (SIGSEGV) - Camera Thread Collapse
**Severity:** CRITICAL  
**Type:** Native Code Segmentation Fault  
**Location:** `CameraService.java:278-299` (processFrame method)

**Issue:**
- Both crash logs show identical SIGSEGV (0xb) crashes in `libobjc.A.dylib+0x9808`
- Crash occurs inside OpenCV's native `VideoCapture.read()` call
- Thread: `fras-camera-thread` (daemon thread)
- Runtime: ~88-117 seconds before crash (app runs successfully but crashes after ~1-2 minutes)

**Root Cause:**
The crash happens in native Objective-C code within OpenCV's macOS camera capture. This is likely due to:
1. **OpenCV version incompatibility** with Java 26.0.2 + macOS 26.6.2 (ARM64/Apple Silicon)
2. **Camera resource not properly released** between frames
3. **Race condition** in OpenCV's native camera handling on macOS

**Stack Trace Analysis:**
```
C  [libobjc.A.dylib+0x9808]  objc_msgSend+0x8
C  [libopencv_java490.dylib+0x910818]  CvCaptureCAM::grabFrame()+0x40
C  [libopencv_java490.dylib+0x8f5e28]  cvGrabFrame+0x18
...→ org.opencv.videoio.VideoCapture.read_0(JJ)Z
...→ com.fras.config.CameraService.processFrame()V
```

**Impact:**
- Application crashes after 1-2 minutes of running
- Face recognition feature completely non-functional
- Makes the system unusable for attendance marking

---

### 🔴 Bug #2: Mismatched Main Application Class
**Severity:** HIGH  
**Type:** Configuration Mismatch  
**Location:** `pom.xml:166-168` vs `pom.xml:204`

**Issue:**
```xml
<!-- In Spring Boot plugin: -->
<mainClass>com.school.school_management_system.SchoolManagementSystemApplication</mainClass>

<!-- In JavaFX plugin: -->
<mainClass>com.fras.app.Launcher</mainClass>
```

**Problem:**
- Spring Boot and JavaFX use different entry points
- This causes confusion about which main class to use
- Could lead to Spring context not being initialized when running via Maven

**Solution:**
Spring context should only be initialized for REST API calls, not for JavaFX GUI. However, the configuration needs clarification.

---

### 🟡 Bug #3: Potential Null Pointer in AttendanceService
**Severity:** MEDIUM  
**Type:** Runtime Exception Risk  
**Location:** `AttendanceService.java:107-112`

**Issue:**
```java
private Status determineStatus(LocalTime sessionStartTime, LocalTime markedTime) {
    if (sessionStartTime == null) {
        return Status.PRESENT;
    }
    long minutesLate = Duration.between(sessionStartTime, markedTime).toMinutes();
    // BUG: markedTime could also be null!
    return minutesLate > LATE_THRESHOLD_MINUTES ? Status.LATE : Status.PRESENT;
}
```

**Problem:**
- `markedTime` parameter not null-checked before `Duration.between()`
- If `markedTime` is null, will throw `NullPointerException`
- Breaks attendance marking flow

**Fix:** Add null check for `markedTime`

---

### 🟡 Bug #4: Missing Confidence Score Validation
**Severity:** MEDIUM  
**Type:** Logic Error  
**Location:** `AttendanceService.java:46-52`

**Issue:**
```java
if (request.getMarkedBy() == MarkedBy.FACE_RECOGNITION
        && (request.getConfidenceScore() == null
        || request.getConfidenceScore() < MIN_FACE_MATCH_CONFIDENCE)) {
    throw new IllegalArgumentException(...);
}
```

**Problem:**
- Only validates if `MarkedBy.FACE_RECOGNITION`
- Other marking methods (`MANUAL`, `SYSTEM_AUTO_ABSENT`) bypass confidence checks
- Inconsistent validation logic

---

### 🟡 Bug #5: Race Condition in CameraService
**Severity:** MEDIUM  
**Type:** Thread Safety Issue  
**Location:** `CameraService.java:370-384`

**Issue:**
```java
synchronized (frameLock) {
    releaseDetectedFaces(currentFaces);
    currentFaces = copyDetectedFaces(detectedFaces);
}
```

**Problem:**
- `detectedFaces` is read OUTSIDE the synchronized block (line 361-362)
- `currentFaces` is modified INSIDE the synchronized block
- Race condition between detection thread and UI thread

---

## 2. DATABASE STATUS ✅ WORKING

### Database Configuration
```properties
# Current: H2 File-based Database
spring.datasource.url=jdbc:h2:file:./data/school_db;MODE=MySQL
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
```

### Data Storage Status:
✅ **DATA IS BEING STORED** in the database  
✅ **JPA entities are properly configured** with correct mappings  
✅ **Hibernate DDL auto-update working** (creates/updates tables)  
✅ **Repositories have proper queries** for attendance tracking  

### Database Location:
```
./data/school_db.mv.db (H2 file database)
```

### Entities Properly Configured:
- ✅ `Student` - ID, name, email, age
- ✅ `Attendance` - Student/Subject/Date with unique constraint
- ✅ `Status` enum - PRESENT, LATE, ABSENT
- ✅ `MarkedBy` enum - FACE_RECOGNITION, MANUAL, SYSTEM_AUTO_ABSENT

### Verified Functionality:
- ✅ Unique constraint prevents duplicate attendance (student + subject + date)
- ✅ Attendance status properly tracked (PRESENT/LATE/ABSENT)
- ✅ Confidence scores stored for face recognition matches
- ✅ Support for MySQL and H2 (switchable via application-mysql.properties)

---

## 3. CAN I SOLVE THESE ERRORS?

### ✅ YES - I can fix these issues:

#### Fixable (Application-Level):
1. ✅ **Bug #3** - Add null check in `determineStatus()`
2. ✅ **Bug #4** - Improve validation logic in `AttendanceService`
3. ✅ **Bug #5** - Fix race condition in `CameraService` thread synchronization
4. ✅ **Bug #2** - Clarify/fix pom.xml configuration mismatch

#### Partially Fixable (OpenCV Crash):
1. ⚠️ **Bug #1** - Can be mitigated by:
   - Wrapping `capture.read()` in try-catch for native exceptions
   - Adding timeout mechanism to prevent long-running native calls
   - Upgrading OpenCV to latest version (4.10.0+)
   - Implementing graceful camera fallback/restart
   - However, underlying native crash requires OpenCV/JVM fix or platform change

---

## 4. ROOT CAUSE SUMMARY

| Bug | Root Cause | Fixable |
|-----|-----------|---------|
| OpenCV Crash | Native library incompatibility (macOS ARM64 + Java 26) | ⚠️ Workaround only |
| Config Mismatch | Multiple entry points defined | ✅ Yes |
| Null Pointer Risk | Missing validation | ✅ Yes |
| Validation Logic | Incomplete checks | ✅ Yes |
| Race Condition | Improper synchronization | ✅ Yes |

---

## 5. RECOMMENDATIONS

### Immediate (Critical):
1. Fix the OpenCV crash by implementing graceful error handling and fallback
2. Fix null checks in AttendanceService
3. Fix race conditions in CameraService

### Short-term (Important):
1. Upgrade OpenCV to latest stable version (4.10.0)
2. Add proper logging for camera failures
3. Implement camera reconnection logic

### Long-term (Enhancement):
1. Consider alternative face recognition library compatible with macOS/ARM64
2. Add integration tests for camera service
3. Improve error recovery mechanisms

---

## 6. NEXT STEPS

Would you like me to:
1. **Fix all application-level bugs** (Bugs #2-5) ✅
2. **Implement graceful OpenCV crash handling** (Bug #1) ⚠️
3. **Both of the above** ✅✅
4. **Investigate OpenCV version upgrade options** 🔍

