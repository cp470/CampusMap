package com.example.wlucampusmap;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private GoogleMap googleMap;
    private FusedLocationProviderClient fusedLocationClient;
    private Map<String, Building> buildings;
    private boolean markersAdded = false;
    private AdminManager adminManager;
    private Map<String, Marker> buildingMarkers = new HashMap<>();
    private Map<String, LatLng> buildingLocations = new HashMap<>();
    private Map<String, List<String>> buildingKeywords = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adminManager = new AdminManager(this);
        initializeBuildings();
        setupMap();
        setupLocationFab();
        setupAddMarkerButton();
        setupSearch();

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!isTaskRoot()) {
                    finish();
                } else {
                    finish();
                }
            }
        });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setOverflowIcon(
                ContextCompat.getDrawable(this, android.R.drawable.ic_menu_info_details)
        );
    }

    private void setupSearch() {
        TextInputEditText searchEditText = findViewById(R.id.search_edit_text);
        searchEditText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        searchEditText.setSingleLine(true);
        searchEditText.setOnEditorActionListener((TextView v, int actionId, KeyEvent event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                String query = searchEditText.getText().toString().trim();
                if (!query.isEmpty()) {
                    doSearch(query);
                }
                return true;
            }
            return false;
        });
    }

    private void doSearch(String query) {
        String queryLower = query.toLowerCase();

        // Try as exact/partial building name, using buildingKeywords for fuzzy search
        Building foundBuilding = null;
        for (Map.Entry<String, Building> entry : buildings.entrySet()) {
            String key = entry.getKey();
            List<String> keys = buildingKeywords.get(key);
            if (keys != null) {
                for (String keyword : keys) {
                    if (queryLower.contains(keyword)) {
                        foundBuilding = entry.getValue();
                        break;
                    }
                }
            }
            if (foundBuilding != null) break;

            if (entry.getValue().getName().toLowerCase().contains(queryLower)) {
                foundBuilding = entry.getValue();
                break;
            }
        }

        if (foundBuilding != null) {
            showOnlyMarkerFor(foundBuilding);
            Toast.makeText(this, "Found: " + foundBuilding.getName(), Toast.LENGTH_SHORT).show();
            return;
        }

        // Try as classroom/room abbreviation
        for (Building building : buildings.values()) {
            List<Classroom> rooms = building.getClassrooms();
            for (Classroom c : rooms) {
                if (c.getRoomNumber().equalsIgnoreCase(query)
                        || c.getRoomNumber().toLowerCase().contains(queryLower)) {
                    showOnlyMarkerFor(building);
                    String floorText = getFloorDesc(building, c);
                    Toast.makeText(this, "Room " + c.getRoomNumber() + " is in " + building.getName() +
                            (floorText.isEmpty() ? "" : (" on " + floorText)), Toast.LENGTH_LONG).show();
                    return;
                }
            }
        }

        // Try room shortcut logic: BA..., L..., etc.
        if (queryLower.matches("^ba\\d+")) {
            Building bldg = buildings.get("bricker");
            if (bldg != null) {
                showOnlyMarkerFor(bldg);
                int floor = getRoomFloorFromNumber(queryLower, "ba");
                Toast.makeText(this, "Room " + query.toUpperCase() + " is in " + bldg.getName() +
                        " on " + ordinal(floor) + " floor", Toast.LENGTH_LONG).show();
                return;
            }
        } else if (queryLower.matches("^l\\d+")) {
            Building bldg = buildings.get("lazaridis");
            if (bldg != null) {
                showOnlyMarkerFor(bldg);
                int floor = getRoomFloorFromNumber(queryLower, "l");
                String floorText = getLazFloorDesc(floor);
                Toast.makeText(this, "Room " + query.toUpperCase() + " is in " + bldg.getName() +
                        " on " + floorText + " floor", Toast.LENGTH_LONG).show();
                return;
            }
        }

        Toast.makeText(this, "Not found: " + query, Toast.LENGTH_SHORT).show();
    }

    private int getRoomFloorFromNumber(String room, String buildingPrefix) {
        room = room.toLowerCase().replace(buildingPrefix, "");
        if (room.length() < 3) return 1;
        char c = room.charAt(0);
        switch (c) {
            case '1':
                return 1;
            case '2':
                return 2;
            case '3':
                return 3;
            case '4':
                return 4;
            default:
                return 1;
        }
    }

    private String getLazFloorDesc(int floorNum) {
        if (floorNum == 1) return "ground";
        if (floorNum == 2) return "second";
        if (floorNum == 3) return "third";
        if (floorNum == 4) return "fourth";
        return "";
    }

    private String ordinal(int i) {
        if (i == 1) return "ground";
        if (i == 2) return "second";
        if (i == 3) return "third";
        if (i == 4) return "fourth";
        return "";
    }

    private String getFloorDesc(Building building, Classroom c) {
        if ("lazaridis".equalsIgnoreCase(getBuildingKeyFor(building))) {
            return getLazFloorDesc(c.getFloor());
        }
        return ordinal(c.getFloor());
    }

    private String getBuildingKeyFor(Building b) {
        for (Map.Entry<String, Building> entry : buildings.entrySet()) {
            if (entry.getValue() == b) return entry.getKey();
        }
        return "";
    }

    private void showOnlyMarkerFor(Building building) {
        if (googleMap == null) return;

        for (Marker marker : buildingMarkers.values()) marker.remove();
        buildingMarkers.clear();

        String key = getBuildingKeyFor(building);
        LatLng location = buildingLocations.get(key);
        if (location != null) {
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(location)
                    .title(building.getName())
                    .snippet(building.getDescription())
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                    );
            if (marker != null) {
                buildingMarkers.put(building.getName(), marker);
                marker.showInfoWindow();
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(marker.getPosition(), 19f));
            }
        }
        markersAdded = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        MenuItem adminItem = menu.findItem(R.id.action_admin);
        MenuItem loginItem = menu.findItem(R.id.action_login);
        MenuItem logoutItem = menu.findItem(R.id.action_logout);

        if (adminManager.isAdminLoggedIn()) {
            adminItem.setVisible(true);
            loginItem.setVisible(false);
            logoutItem.setVisible(true);
        } else {
            adminItem.setVisible(false);
            loginItem.setVisible(true);
            logoutItem.setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == R.id.action_admin) {
            openAdminPanel();
            return true;
        } else if (item.getItemId() == R.id.action_login) {
            showAdminLoginDialog();
            return true;
        } else if (item.getItemId() == R.id.action_logout) {
            adminManager.logout();
            Toast.makeText(this, "Logged out", Toast.LENGTH_SHORT).show();
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void openAdminPanel() {
        if (adminManager.isAdminLoggedIn()) {
            showAccountSettingsDialog();
        } else {
            showAdminLoginDialog();
        }
    }

    private void showAccountSettingsDialog() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_admin_settings, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // IMPORTANT: create the dialog AFTER setting the view
        AlertDialog dialog = builder.create();

        // Optional rounded background fix
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Now find views from dialogView
        LinearLayout changeName = dialogView.findViewById(R.id.change_name_btn);
        LinearLayout changeUsername = dialogView.findViewById(R.id.change_username_btn);
        LinearLayout changePassword = dialogView.findViewById(R.id.change_password_btn);


        changeName.setOnClickListener(v -> {
            showChangeNameDialog();
            dialog.dismiss();
        });

        changeUsername.setOnClickListener(v -> {
            showChangeUsernameDialog();
            dialog.dismiss();
        });

        changePassword.setOnClickListener(v -> {
            showChangePasswordDialog();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void showChangeNameDialog() {
        Toast.makeText(this, "Change Name feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void showChangeUsernameDialog() {
        Toast.makeText(this, "Change Username feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void showChangePasswordDialog() {
        Toast.makeText(this, "Change Password feature coming soon!", Toast.LENGTH_SHORT).show();
    }

    private void showAdminLoginDialog() {
        AdminLoginDialogFragment dialog = new AdminLoginDialogFragment();
        dialog.setAdminManager(adminManager);
        dialog.setLoginListener(success -> {
            if (success) {
                Toast.makeText(this, "Admin login successful!", Toast.LENGTH_SHORT).show();
                invalidateOptionsMenu();
            } else {
                Toast.makeText(this, "Login failed", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show(getSupportFragmentManager(), "AdminLoginDialog");
    }

    private void initializeBuildings() {
        buildings = new HashMap<>();
        buildings.put("science", new Building("Science Building", "Home to science departments and laboratories with research facilities", null, 4, "N"));
        buildings.put("field", new Building("Ansly Alumni Field", "Sports and recreation outdoor field", null, 0, "FIELD"));
        buildings.put("research", new Building("Laurier Science Research Centre", "Research labs & offices", null, 3, "LSRC"));
        buildings.put("bricker", new Building("Bricker Academic Building", "Business and academic classrooms with case study rooms", null, 3, "BA"));
        buildings.put("library", new Building("Wilfrid Laurier University Library", "University library with study spaces, research resources & digital media labs", null, 3, "L"));
        buildings.put("athletic", new Building("Laurier Athletic Complex", "Athletic and fitness facilities", null, 2, "AC"));
        buildings.put("lazaridis", new Building("Lazaridis School of Business and Economics", "Business and computer science building with modern facilities", null, 4, "LH"));
        buildings.put("career", new Building("Laurier Career Centre", "Career services, co-op education, and professional development", null, 2, "CC"));
        buildings.put("woods", new Building("Dr Alvin Woods Building (DAWB)", "Academic building for various departments and administrative offices", null, 3, "AW"));

        buildingLocations.put("science", new LatLng(43.47335224692251, -80.52518286067902));
        buildingLocations.put("field", new LatLng(43.474369144225626, -80.52565160689065));
        buildingLocations.put("research", new LatLng(43.472998337295984, -80.52593064044514));
        buildingLocations.put("bricker", new LatLng(43.472665489058315, -80.5265448663019));
        buildingLocations.put("library", new LatLng(43.47287455487474, -80.52999958758937));
        buildingLocations.put("athletic", new LatLng(43.47525532139178, -80.52569544217114));
        buildingLocations.put("lazaridis", new LatLng(43.47511565264205, -80.52954909754015));
        buildingLocations.put("career", new LatLng(43.47393818119128, -80.52409397609674));
        buildingLocations.put("woods", new LatLng(43.473355009317196, -80.5295020107368));

        buildingKeywords.put("science", new ArrayList<String>(){{add("science"); add("sci"); add("n building"); add("n wing"); add("sci");}});
        buildingKeywords.put("field", new ArrayList<String>(){{add("field"); add("alumni"); add("ansly"); add("sport"); add("outdoor");}});
        buildingKeywords.put("research", new ArrayList<String>(){{add("research"); add("lrc"); add("science research");}});
        buildingKeywords.put("bricker", new ArrayList<String>(){{add("bricker"); add("ba"); add("bricker academic");}});
        buildingKeywords.put("library", new ArrayList<String>(){{add("library"); add("lib"); add("study");}});
        buildingKeywords.put("athletic", new ArrayList<String>(){{add("athletic"); add("ac"); add("gym");}});
        buildingKeywords.put("lazaridis", new ArrayList<String>(){{add("laz"); add("lazaridis"); add("school of business"); add("business"); add("lh"); add("lazaridis hall");}});
        buildingKeywords.put("career", new ArrayList<String>(){{add("career"); add("coop"); add("center"); add("cc");}});
        buildingKeywords.put("woods", new ArrayList<String>(){{add("woods"); add("alvin"); add("dawb"); add("dr alvin"); add("aw");}});
    }

    private void setupMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void setupLocationFab() {
        FloatingActionButton locationFab = findViewById(R.id.location_fab);
        locationFab.setOnClickListener(v -> {
            if (hasLocationPermission()) {
                enableMyLocation();
                moveToCurrentLocation();
            } else {
                requestLocationPermission();
            }
        });
    }

    private void setupAddMarkerButton() {
        FloatingActionButton addMarkerFab = findViewById(R.id.add_marker_fab);
        addMarkerFab.setOnClickListener(v -> {
            if (!markersAdded) {
                addBuildingMarkers();
                markersAdded = true;
                Toast.makeText(this, "Building markers added", Toast.LENGTH_SHORT).show();
            } else {
                clearAllMarkers();
                markersAdded = false;
                Toast.makeText(this, "Building markers cleared", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onMapReady(@NonNull GoogleMap googleMap) {
        this.googleMap = googleMap;
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        LatLng wluCampus = new LatLng(43.4735, -80.5277);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(wluCampus, 16.5f));
        googleMap.setOnMarkerClickListener(this);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(true);
        googleMap.setOnPoiClickListener(null);

        if (hasLocationPermission()) {
            enableMyLocation();
        }
    }

    private void addBuildingMarkers() {
        if (googleMap == null) return;

        for (Marker marker : buildingMarkers.values()) marker.remove();
        buildingMarkers.clear();

        for (String key : buildingLocations.keySet()) {
            Building building = buildings.get(key);
            LatLng location = buildingLocations.get(key);
            if (building != null && location != null) {
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(location)
                        .title(building.getName())
                        .snippet(building.getDescription())
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET))
                );
                if (marker != null) {
                    buildingMarkers.put(building.getName(), marker);
                }
            }
        }
    }

    private void clearAllMarkers() {
        if (googleMap != null) {
            googleMap.clear();
            buildingMarkers.clear();
            if (hasLocationPermission()) {
                enableMyLocation();
            }
        }
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                LOCATION_PERMISSION_REQUEST_CODE);
    }

    private void enableMyLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        googleMap.setMyLocationEnabled(true);
    }

    private void moveToCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        LatLng currentLatLng = new LatLng(location.getLatitude(), location.getLongitude());
                        googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 18f));
                    } else {
                        Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableMyLocation();
            } else {
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public boolean onMarkerClick(@NonNull Marker marker) {
        for (Building building : buildings.values()) {
            if (building.getName().equals(marker.getTitle())) {
                showBuildingOptions(building);
                return true;
            }
        }
        return false;
    }

    // ==== Updated: "Manage Floor Maps" & "Delete Floor Map" REMOVED ====
    private void showBuildingOptions(Building building) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_building, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(dialogView);

        // IMPORTANT: create the dialog AFTER setting the view
        AlertDialog dialog = builder.create();

        // Optional rounded background fix
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        // Now find views from dialogView
        TextView buildingTitle = dialogView.findViewById(R.id.building_title);
        LinearLayout buildingDetails = dialogView.findViewById(R.id.building_details_btn);
        LinearLayout viewFloorMaps = dialogView.findViewById(R.id.view_floor_maps_btn);
        LinearLayout getDirections = dialogView.findViewById(R.id.get_directions_btn);
        LinearLayout uploadFloorMap = dialogView.findViewById(R.id.upload_floor_map_btn);

        buildingTitle.setText(building.getName());

        buildingDetails.setOnClickListener(v -> {
            openBuildingDetails(building);
            dialog.dismiss();
        });

        viewFloorMaps.setOnClickListener(v -> {
            showFloorMapSelection(building);
            dialog.dismiss();
        });

        getDirections.setOnClickListener(v -> {
            openDirections(building);
            dialog.dismiss();
        });

        uploadFloorMap.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminFloorMapActivity.class);
            intent.putExtra("building_name", building.getName());
            intent.putExtra("building_floors", building.getFloors());
            startActivity(intent);
            dialog.dismiss();
        });

        uploadFloorMap.setVisibility(adminManager.isAdminLoggedIn()
                ? View.VISIBLE
                : View.GONE);

        dialog.show();
    }


    private void openDirections(Building building) {
        String key = getBuildingKeyFor(building);
        LatLng dest = buildingLocations.get(key);
        if (dest != null) {
            if (fusedLocationClient == null) {
                fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
            }
            fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
                if (location != null) {
                    double startLat = location.getLatitude();
                    double startLng = location.getLongitude();
                    double destLat = dest.latitude;
                    double destLng = dest.longitude;
                    String uri = "https://www.google.com/maps/dir/?api=1"
                            + "&origin=" + startLat + "," + startLng
                            + "&destination=" + destLat + "," + destLng
                            + "&travelmode=walking";
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");
                    if (intent.resolveActivity(getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        Toast.makeText(this, "Google Maps app is not installed!", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Unable to get current location", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void openBuildingDetails(Building building) {
        Intent intent = new Intent(this, BuildingDetailActivity.class);
        intent.putExtra("building_name", building.getName());
        intent.putExtra("building_description", building.getDescription());
        intent.putExtra("building_floors", building.getFloors());
        startActivity(intent);
    }

    private void showFloorMapSelection(Building building) {
        Intent intent = new Intent(this, FloorMapActivity.class);
        intent.putExtra("building_name", building.getName());
        intent.putExtra("building_floors", building.getFloors());
        startActivity(intent);
    }
}