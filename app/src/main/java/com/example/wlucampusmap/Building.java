package com.example.wlucampusmap;

import com.google.android.gms.maps.model.LatLng;
import java.util.ArrayList;
import java.util.List;

public class Building {
    private String name;
    private String description;
    private LatLng location;
    private int floors;
    private List<Classroom> classrooms;
    private String buildingCode;

    public Building(String name, String description, LatLng location, int floors, String buildingCode) {
        this.name = name;
        this.description = description;
        this.location = location;
        this.floors = floors;
        this.buildingCode = buildingCode;
        this.classrooms = new ArrayList<>();
        initializeClassrooms();
    }

    // Classroom initialization methods (leave as is or add your logic)
    private void initializeClassrooms() {
        // You can prepopulate classrooms here if needed.
    }

    public String getName() {
        return name;
    }
    public String getDescription() {
        return description;
    }
    public LatLng getLocation() {
        return location;
    }
    public int getFloors() {
        return floors;
    }
    public List<Classroom> getClassrooms() {
        return classrooms;
    }
    public String getBuildingCode() {
        return buildingCode;
    }
    public List<Classroom> getClassroomsByFloor(int floor) {
        List<Classroom> floorClassrooms = new ArrayList<>();
        for (Classroom classroom : classrooms) {
            if (classroom.getFloor() == floor) {
                floorClassrooms.add(classroom);
            }
        }
        return floorClassrooms;
    }

    // Admin room management
    public void addClassroom(Classroom classroom) {
        classrooms.add(classroom);
    }
    public void removeClassroom(Classroom classroom) {
        classrooms.remove(classroom);
    }
    public void editClassroomName(Classroom classroom, String newName) {
        int idx = classrooms.indexOf(classroom);
        if (idx >= 0) {
            classrooms.get(idx).setRoomNumber(newName);
        }
    }
}