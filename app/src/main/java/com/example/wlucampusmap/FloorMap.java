package com.example.wlucampusmap;

import java.util.Date;
import java.util.List;

public class FloorMap {
    private String id;
    private String buildingName;
    private String floorNumber;
    private String floorMapUri;
    private List<RoomPin> roomPins;
    private Date uploadDate;
    private String uploadedBy;

    public FloorMap() {
        this.uploadDate = new Date();
    }

    public FloorMap(String buildingName, String floorNumber, String floorMapUri) {
        this();
        this.buildingName = buildingName;
        this.floorNumber = floorNumber;
        this.floorMapUri = floorMapUri;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBuildingName() { return buildingName; }
    public void setBuildingName(String buildingName) { this.buildingName = buildingName; }

    public String getFloorNumber() { return floorNumber; }
    public void setFloorNumber(String floorNumber) { this.floorNumber = floorNumber; }

    public String getFloorMapUri() { return floorMapUri; }
    public void setFloorMapUri(String floorMapUri) { this.floorMapUri = floorMapUri; }

    public List<RoomPin> getRoomPins() { return roomPins; }
    public void setRoomPins(List<RoomPin> roomPins) { this.roomPins = roomPins; }

    public Date getUploadDate() { return uploadDate; }
    public void setUploadDate(Date uploadDate) { this.uploadDate = uploadDate; }

    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
}