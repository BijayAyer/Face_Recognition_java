package com.school.school_management_system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Single Spring Boot entry point for the whole backend.
 *
 * The attendance module (com.attendance.*) originally shipped as its
 * own standalone Spring Boot app (com.attendance.AttendanceApplication)
 * with its own component scan rooted at com.attendance. It has been
 * folded into this application so there is only one backend process:
 * its @Service/@RestController beans, @Entity classes, and JPA
 * repositories are picked up via the explicit scan/entity-scan
 * packages below instead of Spring Boot's default
 * "scan the main class's own package" behavior.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.school.school_management_system",
        "com.attendance",
        "com.fras.web"
})
@EntityScan(basePackages = {
        "com.school.school_management_system.entity",
        "com.attendance.entity",
        "com.fras.model"
})
@EnableJpaRepositories(basePackages = {
        "com.school.school_management_system.repository",
        "com.attendance.repository",
        "com.fras.repository"
})
public class SchoolManagementSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchoolManagementSystemApplication.class, args);
    }
}
