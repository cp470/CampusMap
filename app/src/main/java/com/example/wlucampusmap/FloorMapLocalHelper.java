package com.example.wlucampusmap;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FloorMapLocalHelper {

    private static final String PREFS = "WLUCampusMapPrefs";

    public static List<Classroom> loadClassrooms(Context ctx, String buildingName, int totalFloors) {
        List<Classroom> list = loadFromPrefs(ctx, buildingName);
        if (list != null && !list.isEmpty()) return list;

        list = new ArrayList<>();
        if ("Science Building".equals(buildingName)) {
            list.add(new Classroom("N1001", "General Classroom", 1));
            list.add(new Classroom("N2001", "Biology Lab", 2));
        } else if ("Library".equals(buildingName)) {
            list.add(new Classroom("L101", "Main Entrance", 1));
        } else {
            for (int f = 1; f <= totalFloors; f++) {
                for (int i = 1; i <= 6; i++) {
                    list.add(new Classroom("R" + (f * 100 + i), "General", f));
                }
            }
        }
        saveToPrefs(ctx, buildingName, list);
        return list;
    }

    public static List<Classroom> getClassroomsByFloor(List<Classroom> allClassrooms, int floor) {
        List<Classroom> out = new ArrayList<>();
        for (Classroom c : allClassrooms) if (c.getFloor() == floor) out.add(c);
        return out;
    }

    private static void saveToPrefs(Context ctx, String buildingName, List<Classroom> list) {
        try {
            JSONArray arr = new JSONArray();
            for (Classroom c : list) {
                JSONObject o = new JSONObject();
                o.put("roomNumber", c.getRoomNumber());
                o.put("roomType", c.getRoomType());
                o.put("floor", c.getFloor());
                arr.put(o);
            }
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            prefs.edit().putString("rooms_" + buildingName.replaceAll("\\s+", "_").toLowerCase(), arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private static List<Classroom> loadFromPrefs(Context ctx, String buildingName) {
        try {
            SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String json = prefs.getString("rooms_" + buildingName.replaceAll("\\s+", "_").toLowerCase(), null);
            if (json == null) return null;
            JSONArray arr = new JSONArray(json);
            List<Classroom> list = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String rn = o.optString("roomNumber", "Room");
                String rt = o.optString("roomType", "General");
                int floor = o.optInt("floor", 1);
                list.add(new Classroom(rn, rt, floor));
            }
            return list;
        } catch (Exception e) {
            return null;
        }
    }
}