package com.example.wlucampusmap;

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class SupabaseManager {

    private static final String TAG = "SupabaseManager";

    // ==== Make sure these exactly match your Supabase project and bucket!! ====
    private static final String SUPABASE_URL = "https://xpbaxoqcrhyuezwmmbmq.supabase.co";
    private static final String SUPABASE_KEY = "sb_secret_DQVLTnM2wPyFW_tOJ-GUBw_ByL1r_-R"; // service_role key for dev only, never production!
    private static final String BUCKET = "WLUMap"; // Be sure this matches your real bucket's name, case-sensitive

    private final OkHttpClient client = new OkHttpClient();

    public interface SupabaseCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    public interface UploadCallback {
        void onUploaded(String publicUrl);
        void onError(String error);
    }

    public interface FetchCallback {
        void onResult(List<FloorMap> floorMaps);
        void onError(String error);
    }

    public void uploadFile(File file, String destPath, UploadCallback callback) {
        try {
            String url = SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + destPath;
            MediaType mediaType = MediaType.parse("application/octet-stream");
            RequestBody body = RequestBody.create(file, mediaType);

            Request request = new Request.Builder()
                    .url(url)
                    .put(body)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .header("x-upsert", "true") // <--- allow overwriting existing floor map!
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "uploadFile onFailure", e);
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "unknown";
                        Log.e(TAG, "uploadFile failed: " + err);
                        callback.onError("Upload failed: " + response.code() + " " + err);
                        return;
                    }
                    String publicUrl = SUPABASE_URL + "/storage/v1/object/public/" + BUCKET + "/" + destPath;
                    callback.onUploaded(publicUrl);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "uploadFile exception", e);
            callback.onError(e.getMessage());
        }
    }

    public void saveFloorMapRecord(FloorMap floorMap, SupabaseCallback callback) {
        try {
            String endpoint = SUPABASE_URL + "/rest/v1/floor_maps";
            JSONObject payload = new JSONObject();
            payload.put("building_name", floorMap.getBuildingName());
            payload.put("floor_number", floorMap.getFloorNumber());
            payload.put("floor_map_url", floorMap.getFloorMapUri());

            if (floorMap.getRoomPins() != null) {
                JSONArray pins = new JSONArray();
                for (RoomPin rp : floorMap.getRoomPins()) {
                    JSONObject o = new JSONObject();
                    o.put("roomName", rp.getRoomName());
                    o.put("x", rp.getX());
                    o.put("y", rp.getY());
                    o.put("floor", rp.getFloor());
                    pins.put(o);
                }
                payload.put("room_pins", pins);
            } else {
                payload.put("room_pins", new JSONArray());
            }

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(payload.toString(), JSON);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .post(body)
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .header("Prefer", "return=minimal")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "unknown";
                        callback.onError("Save failed: " + response.code() + " " + err);
                        return;
                    }
                    String respBody = response.body() != null ? response.body().string() : "";
                    callback.onSuccess(respBody);
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    public void fetchFloorMaps(String buildingName, String floorNumber, FetchCallback callback) {
        try {
            String endpoint = SUPABASE_URL + "/rest/v1/floor_maps"
                    + "?building_name=eq." + URLEncoder.encode(buildingName, "UTF-8")
                    + "&floor_number=eq." + URLEncoder.encode(floorNumber, "UTF-8")
                    + "&select=*";
            Request request = new Request.Builder()
                    .url(endpoint)
                    .get()
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "unknown";
                        callback.onError("Fetch failed: " + response.code() + " " + err);
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "[]";
                    try {
                        JSONArray arr = new JSONArray(body);
                        List<FloorMap> list = new ArrayList<>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            FloorMap fm = new FloorMap();
                            fm.setId(String.valueOf(o.optLong("id", 0)));
                            fm.setBuildingName(o.optString("building_name"));
                            fm.setFloorNumber(o.optString("floor_number"));
                            fm.setFloorMapUri(o.optString("floor_map_url"));
                            JSONArray pins = o.optJSONArray("room_pins");
                            if (pins != null) {
                                List<RoomPin> rpList = new ArrayList<>();
                                for (int j = 0; j < pins.length(); j++) {
                                    JSONObject p = pins.getJSONObject(j);
                                    RoomPin rp = new RoomPin();
                                    rp.setRoomName(p.optString("roomName"));
                                    rp.setX((float) p.optDouble("x", 0.0));
                                    rp.setY((float) p.optDouble("y", 0.0));
                                    rp.setFloor(p.optInt("floor", 1));
                                    rpList.add(rp);
                                }
                                fm.setRoomPins(rpList);
                            }
                            list.add(fm);
                        }
                        callback.onResult(list);
                    } catch (Exception e) {
                        callback.onError(e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }

    // Improved: Deletes all files in the given floor folder (correct prefix param handling)
    public void deleteFloorMapImage(String folder, SupabaseCallback callback) {
        try {
            // Ensure no leading slash for prefix and remove double slashes
            while (folder.startsWith("/")) folder = folder.substring(1);
            folder = folder.replaceAll("//+", "/");
            // If bucket is case-sensitive, check BUCKET against the dashboard!
            String prefixParam = URLEncoder.encode(folder, "UTF-8");
            String url = SUPABASE_URL + "/storage/v1/object/list/" + BUCKET + "?prefix=" + prefixParam;

            Request listReq = new Request.Builder()
                    .url(url)
                    .get()
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("apikey", SUPABASE_KEY)
                    .build();

            client.newCall(listReq).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("List error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        callback.onError("List failed: " + response.code() + " | " + (response.body() != null ? response.body().string() : ""));
                        return;
                    }
                    String body = response.body() != null ? response.body().string() : "";
                    try {
                        JSONArray arr = new JSONArray(body);
                        if (arr.length() == 0) {
                            callback.onSuccess("No files found");
                            return;
                        }
                        // Delete all files found in folder (usually 1 per floor)
                        for (int i = 0; i < arr.length(); i++) {
                            String name = arr.getJSONObject(i).optString("name");
                            String delUrl = SUPABASE_URL + "/storage/v1/object/" + BUCKET + "/" + URLEncoder.encode(name, "UTF-8");
                            Request delReq = new Request.Builder()
                                    .url(delUrl)
                                    .delete()
                                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                                    .header("apikey", SUPABASE_KEY)
                                    .build();

                            client.newCall(delReq).enqueue(new Callback() {
                                @Override
                                public void onFailure(Call call, IOException e) { /* Ignored per file */ }
                                @Override
                                public void onResponse(Call call, Response response) throws IOException { /* Ignored per file */ }
                            });
                        }
                        callback.onSuccess("Floor map(s) deleted");
                    } catch (Exception e) {
                        callback.onError("Parse error: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("Delete error: " + e.getMessage());
        }
    }

    // Clears the floor_map_url field after deleting from storage.
    public void clearFloorMapUrl(String buildingName, String floorNumber, SupabaseCallback callback) {
        try {
            String endpoint = SUPABASE_URL + "/rest/v1/floor_maps"
                    + "?building_name=eq." + URLEncoder.encode(buildingName, "UTF-8")
                    + "&floor_number=eq." + URLEncoder.encode(floorNumber, "UTF-8");

            JSONObject payload = new JSONObject();
            payload.put("floor_map_url", JSONObject.NULL);

            MediaType JSON = MediaType.parse("application/json; charset=utf-8");
            RequestBody body = RequestBody.create(payload.toString(), JSON);

            Request request = new Request.Builder()
                    .url(endpoint)
                    .patch(body)
                    .header("apikey", SUPABASE_KEY)
                    .header("Authorization", "Bearer " + SUPABASE_KEY)
                    .header("Accept", "application/json")
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override public void onFailure(Call call, IOException e) {
                    callback.onError(e.getMessage());
                }
                @Override public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String err = response.body() != null ? response.body().string() : "unknown";
                        callback.onError("Clear URL failed: " + response.code() + " " + err);
                        return;
                    }
                    callback.onSuccess("Floor map URL cleared");
                }
            });
        } catch (Exception e) {
            callback.onError(e.getMessage());
        }
    }
}