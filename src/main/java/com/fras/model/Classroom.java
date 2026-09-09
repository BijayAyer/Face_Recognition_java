package com.fras.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A room attendance is taken in.
 *
 * <p>See {@link Department} for why these constraints exist. The two numbers
 * are the reason this one matters most: {@code int} columns are never null, so
 * the database accepted a classroom on floor -5 with a capacity of 0 without
 * complaint, and the client had no way to tell such a row from a real one.
 */
@Entity
@Table(name = "classrooms")
public class Classroom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "A room number is required")
    @Size(max = 32, message = "The room number must be at most 32 characters")
    @Column(name = "room_number", unique = true, nullable = false, length = 32)
    private String roomNumber;

    @NotBlank(message = "A building is required")
    @Size(max = 120, message = "The building must be at most 120 characters")
    @Column(name = "building", nullable = false, length = 120)
    private String building;

    @Min(value = 0, message = "A floor cannot be negative")
    @Max(value = 200, message = "That floor number is not plausible")
    @Column(name = "floor", nullable = false)
    private int floor;

    @Min(value = 1, message = "A classroom needs room for at least one person")
    @Max(value = 10000, message = "That capacity is not plausible")
    @Column(name = "capacity", nullable = false)
    private int capacity;

    public Classroom() {
    }

    public Classroom(Long id, String roomNumber, String building, int floor, int capacity) {
        this.id = id;
        this.roomNumber = roomNumber;
        this.building = building;
        this.floor = floor;
        this.capacity = capacity;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public int getFloor() {
        return floor;
    }

    public void setFloor(int floor) {
        this.floor = floor;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}