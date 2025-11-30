package com.example.wlucampusmap;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class BuildingDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_building_detail);

        // Get building data from intent
        Intent intent = getIntent();
        String buildingName = intent.getStringExtra("building_name");
        String buildingDescription = intent.getStringExtra("building_description");
        double latitude = intent.getDoubleExtra("building_latitude", 0);
        double longitude = intent.getDoubleExtra("building_longitude", 0);
        int floors = intent.getIntExtra("building_floors", 1);

        // Set up views
        TextView nameTextView = findViewById(R.id.building_name);
        TextView descriptionTextView = findViewById(R.id.building_description);
        TextView coordinatesTextView = findViewById(R.id.building_coordinates);
        TextView floorsTextView = findViewById(R.id.building_floors);
        Button floorMapButton = findViewById(R.id.floor_map_button);

        // Populate data
        nameTextView.setText(buildingName);
        descriptionTextView.setText(buildingDescription);
        coordinatesTextView.setText(String.format("Coordinates: %.4f, %.4f", latitude, longitude));
        floorsTextView.setText(String.format("Floors: %d", floors));

        // Set up floor map button
        floorMapButton.setOnClickListener(v -> {
            Intent floorMapIntent = new Intent(this, FloorMapActivity.class);
            floorMapIntent.putExtra("building_name", buildingName);
            floorMapIntent.putExtra("building_floors", floors);
            startActivity(floorMapIntent);
        });
    }
}