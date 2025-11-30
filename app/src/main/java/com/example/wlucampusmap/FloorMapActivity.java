package com.example.wlucampusmap;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class FloorMapActivity extends AppCompatActivity {
    private static final String PREFS = "WLUCampusMapPrefs";

    private ImageView floorMapImageView;
    private ListView classroomListView;
    private TextView floorInfoTextView, roomSectionTitle;
    private Spinner floorSpinner;
    private FloatingActionButton fabAddRoom;

    private String buildingName;
    private int totalFloors;
    private List<Classroom> allClassrooms;
    private int currentFloor = 1;

    private SupabaseManager supabaseManager;
    private AdminManager adminManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_floor_map);

        buildingName = getIntent().getStringExtra("building_name");
        totalFloors = getIntent().getIntExtra("building_floors", 1);

        supabaseManager = new SupabaseManager();
        adminManager = new AdminManager(this);

        floorMapImageView = findViewById(R.id.floormap_imageview);
        classroomListView = findViewById(R.id.classroom_list_view);
        floorInfoTextView = findViewById(R.id.floor_info_text_view);
        roomSectionTitle = findViewById(R.id.room_section_title);
        floorSpinner = findViewById(R.id.floor_spinner);
        fabAddRoom = findViewById(R.id.fab_add_room);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(buildingName + " - Floor Maps");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        allClassrooms = loadClassroomsFromPrefs(buildingName, totalFloors);
        setupFloorSpinner();

        // Only show add button to admin
        if (!adminManager.isAdminLoggedIn()) {
            fabAddRoom.setVisibility(View.GONE);
        }
        fabAddRoom.setOnClickListener(v -> showAddEditRoomDialog(null, currentFloor));
    }

    private void setupFloorSpinner() {
        String[] floorOptions = new String[totalFloors];
        for (int i = 0; i < totalFloors; i++)
            floorOptions[i] = "Floor " + (i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, floorOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        floorSpinner.setAdapter(adapter);

        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFloor = position + 1;
                floorInfoTextView.setText("Floor " + currentFloor);
                fetchAndShowFloorMap(currentFloor);
                showClassroomsForFloor(currentFloor);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        });
        floorSpinner.setSelection(0);
    }

    private void fetchAndShowFloorMap(int floor) {
        supabaseManager.fetchFloorMaps(buildingName, String.valueOf(floor), new SupabaseManager.FetchCallback() {
            @Override
            public void onResult(List<FloorMap> floorMaps) {
                runOnUiThread(() -> {
                    if (floorMaps != null && !floorMaps.isEmpty()) {
                        floorMapImageView.setVisibility(View.VISIBLE);
                        FloorMap fm = floorMaps.get(0);
                        String url = fm.getFloorMapUri();

                        // ADD CACHE BUSTING - Add timestamp to URL to prevent caching
                        String cacheBustedUrl = url + "?t=" + System.currentTimeMillis();

                        Glide.with(FloorMapActivity.this)
                                .load(cacheBustedUrl)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)  // Disable disk cache
                                .skipMemoryCache(true)  // Disable memory cache
                                .placeholder(android.R.color.darker_gray)
                                .error(android.R.drawable.stat_notify_error)
                                .into(floorMapImageView);
                    } else {
                        floorMapImageView.setVisibility(View.GONE);
                        // Clear any cached image
                        Glide.with(FloorMapActivity.this).clear(floorMapImageView);
                        floorMapImageView.setImageDrawable(null);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    floorMapImageView.setVisibility(View.GONE);
                    // Clear any cached image on error
                    Glide.with(FloorMapActivity.this).clear(floorMapImageView);
                    floorMapImageView.setImageDrawable(null);
                    Toast.makeText(FloorMapActivity.this, "Could not fetch floor map: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void showClassroomsForFloor(int floor) {
        List<Classroom> rooms = getClassroomsByFloor(allClassrooms, floor);
        List<String> strings = new ArrayList<>();
        for (Classroom r : rooms) strings.add(r.toString());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, strings);
        classroomListView.setAdapter(adapter);

        // Only admins can edit/delete on tap
        if (!adminManager.isAdminLoggedIn()) {
            classroomListView.setOnItemClickListener(null);
        } else {
            classroomListView.setOnItemClickListener((parent, view, position, id) -> {
                Classroom selected = rooms.get(position);
                showClassroomOptions(selected, floor);
            });
        }
    }

    private void showClassroomOptions(Classroom classroom, int floor) {
        // Only admins reach here
        CharSequence[] options = {"Edit", "Delete", "Cancel"};
        new AlertDialog.Builder(this)
                .setTitle(classroom.getRoomNumber())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) showAddEditRoomDialog(classroom, floor);
                    else if (which == 1) showDeleteConfirmationDialog(classroom, floor);
                })
                .show();
    }

    private void showAddEditRoomDialog(Classroom classroom, int floor) {
        boolean isEdit = classroom != null;
        EditText etRoomNumber = new EditText(this);
        etRoomNumber.setHint("Room Number");
        etRoomNumber.setInputType(InputType.TYPE_CLASS_TEXT);
        if (isEdit) etRoomNumber.setText(classroom.getRoomNumber());

        EditText etRoomType = new EditText(this);
        etRoomType.setHint("Room Type");
        etRoomType.setInputType(InputType.TYPE_CLASS_TEXT);
        if (isEdit) etRoomType.setText(classroom.getRoomType());

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        container.setPadding(padding, padding, padding, padding);
        container.addView(etRoomNumber);
        container.addView(etRoomType);

        new AlertDialog.Builder(this)
                .setTitle(isEdit ? "Edit Room/Office" : "Add Room/Office")
                .setView(container)
                .setPositiveButton(isEdit ? "Save" : "Add", (dialog, which) -> {
                    String num = etRoomNumber.getText().toString().trim();
                    String type = etRoomType.getText().toString().trim();
                    if (num.isEmpty() || type.isEmpty()) {
                        Toast.makeText(this, "Both fields required", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (isEdit) {
                        classroom.setRoomNumber(num);
                        classroom.setRoomType(type);
                    } else {
                        allClassrooms.add(new Classroom(num, type, floor));
                    }
                    saveClassroomsToPrefs(buildingName, allClassrooms);
                    showClassroomsForFloor(floor);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteConfirmationDialog(Classroom classroom, int floor) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Room/Office")
                .setMessage("Delete " + classroom.getRoomNumber() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    allClassrooms.remove(classroom);
                    saveClassroomsToPrefs(buildingName, allClassrooms);
                    showClassroomsForFloor(floor);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    public static List<Classroom> getClassroomsByFloor(List<Classroom> allClassrooms, int floor) {
        List<Classroom> floorClassrooms = new ArrayList<>();
        for (Classroom classroom : allClassrooms) {
            if (classroom.getFloor() == floor) {
                floorClassrooms.add(classroom);
            }
        }
        return floorClassrooms;
    }

    private List<Classroom> loadClassroomsFromPrefs(String buildingName, int totalFloors) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "rooms_" + buildingName.replaceAll("\\s+", "_").toLowerCase();
        String json = prefs.getString(key, null);

        List<Classroom> list = new ArrayList<>();
        try {
            if (json != null) {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String rn = o.optString("roomNumber", "Room");
                    String rt = o.optString("roomType", "General Purpose Room");
                    int f = o.optInt("floor", 1);
                    list.add(new Classroom(rn, rt, f));
                }
            } else {
                for (int f = 1; f <= totalFloors; f++)
                    for (int i = 1; i <= 6; i++)
                        list.add(new Classroom("R" + (f * 100 + i), "General", f));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    private void saveClassroomsToPrefs(String buildingName, List<Classroom> list) {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = "rooms_" + buildingName.replaceAll("\\s+", "_").toLowerCase();
        try {
            JSONArray arr = new JSONArray();
            for (Classroom c : list) {
                JSONObject o = new JSONObject();
                o.put("roomNumber", c.getRoomNumber());
                o.put("roomType", c.getRoomType());
                o.put("floor", c.getFloor());
                arr.put(o);
            }
            prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) {}
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Add this method to manually refresh the current floor map
    public void refreshCurrentFloorMap() {
        fetchAndShowFloorMap(currentFloor);
    }
}