package com.fras.app.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Just enough of a classroom to choose one.
 *
 * <p>Deliberately not the {@code Classroom} entity: the entity is what the
 * server persists, and deserializing it in the client would tie every picker to
 * the shape of the JPA graph behind it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ClassroomRow {

    private Long id;
    private String roomNumber;
    private String building;
    private int floor;
    private int capacity;

    public ClassroomRow() {
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

    /** How the room names itself in a list: "B-204, Science Block". */
    public String label() {
        StringBuilder text = new StringBuilder();
        text.append(roomNumber == null || roomNumber.isBlank() ? "Room " + id : roomNumber);
        if (building != null && !building.isBlank()) {
            text.append(", ").append(building);
        }
        return text.toString();
    }
}
