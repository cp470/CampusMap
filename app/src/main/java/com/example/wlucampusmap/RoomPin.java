package com.example.wlucampusmap;

public class RoomPin {
    private String roomName;
    private String roomType;
    private String description;
    private float xCoordinate;
    private float yCoordinate;
    private int floor;

    public RoomPin() {}

    public RoomPin(String roomName, String roomType, String description,
                   float xCoordinate, float yCoordinate, int floor) {
        this.roomName = roomName;
        this.roomType = roomType;
        this.description = description;
        this.xCoordinate = xCoordinate;
        this.yCoordinate = yCoordinate;
        this.floor = floor;
    }

    // Main label for pin
    public String getRoomName() { return roomName; }
    public void setRoomName(String roomName) { this.roomName = roomName; }

    public String getRoomType() { return roomType; }
    public void setRoomType(String roomType) { this.roomType = roomType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public float getXCoordinate() { return xCoordinate; }
    public void setXCoordinate(float xCoordinate) { this.xCoordinate = xCoordinate; }

    public float getYCoordinate() { return yCoordinate; }
    public void setYCoordinate(float yCoordinate) { this.yCoordinate = yCoordinate; }

    public int getFloor() { return floor; }
    public void setFloor(int floor) { this.floor = floor; }

    // Aliases for compatibility (do NOT remove: required by SupabaseManager and others)
    public float getX() { return getXCoordinate(); }
    public void setX(float x) { setXCoordinate(x); }

    public float getY() { return getYCoordinate(); }
    public void setY(float y) { setYCoordinate(y); }
}