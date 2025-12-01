package com.example.wlucampusmap;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.channels.AlreadyBoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
//        floorInfoTextView = findViewById(R.id.floor_info_text_view);
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

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        toolbar.setNavigationOnClickListener(v ->
                getOnBackPressedDispatcher().onBackPressed()
        );
    }

    private void setupFloorSpinner() {
        String[] floorOptions = new String[totalFloors];
        for (int i = 0; i < totalFloors; i++)
            floorOptions[i] = "Floor " + (i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner, floorOptions);
        adapter.setDropDownViewResource(R.layout.spinner);
        floorSpinner.setAdapter(adapter);

        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentFloor = position + 1;
//                floorInfoTextView.setText("Floor " + currentFloor);
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
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_classroom_options, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // IMPORTANT: create the dialog AFTER setting the view
        AlertDialog dialog = builder.create();

        // Optional rounded background fix
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Now find views from dialogView
        TextView roomTitle = dialogView.findViewById(R.id.room_title);
        LinearLayout editButton = dialogView.findViewById(R.id.edit_btn);
        LinearLayout deleteButton = dialogView.findViewById(R.id.delete_btn);

        roomTitle.setText(classroom.getRoomNumber());

        editButton.setOnClickListener(v -> showAddEditRoomDialog(classroom, floor));
        deleteButton.setOnClickListener(v -> showDeleteConfirmationDialog(classroom, floor, dialog));

        dialog.show();
    }

    private void showAddEditRoomDialog(Classroom classroom, int floor) {
        boolean isEdit = classroom != null;

        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_add_room, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // IMPORTANT: create the dialog AFTER setting the view
        AlertDialog dialog = builder.create();

        // Optional rounded background fix
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Now find views from dialogView
        EditText roomNumber = dialogView.findViewById(R.id.room_number);
        EditText roomType = dialogView.findViewById(R.id.room_type);
        TextView label = dialogView.findViewById(R.id.label);
        MaterialButton button = dialogView.findViewById(R.id.button);


        label.setText(isEdit ? "Edit Room/Office" : "Add Room/Office");
        roomNumber.setText(isEdit ? classroom.getRoomNumber() : "");
        roomType.setText(isEdit ? classroom.getRoomType() : "");
        button.setText(isEdit ? "Save" : "Add");

        button.setOnClickListener(v -> {
                    String num = roomNumber.getText().toString().trim();
                    String type = roomType.getText().toString().trim();
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
                    dialog.dismiss();
                }
        );


        dialog.show();
    }

    private void showDeleteConfirmationDialog(Classroom classroom, int floor, AlertDialog previousDialog) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_delete_confirmation, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // IMPORTANT: create the dialog AFTER setting the view
        AlertDialog dialog = builder.create();

        // Optional rounded background fix
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Now find views from dialogView
        TextView roomTitle = dialogView.findViewById(R.id.room_title);
        LinearLayout deleteButton = dialogView.findViewById(R.id.delete_btn);

        roomTitle.setText("Delete Room/Office " + classroom.getRoomNumber() + "?");

        deleteButton.setOnClickListener(v -> {
            allClassrooms.remove(classroom);
            saveClassroomsToPrefs(buildingName, allClassrooms);
            showClassroomsForFloor(floor);
            previousDialog.dismiss();
            dialog.dismiss();

        });

        dialog.show();
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