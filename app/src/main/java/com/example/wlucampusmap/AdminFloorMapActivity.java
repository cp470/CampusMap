package com.example.wlucampusmap;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.github.dhaval2404.imagepicker.ImagePicker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class AdminFloorMapActivity extends AppCompatActivity {

    private static final String TAG = "AdminFloorMapActivity";

    private ImageView floorMapImageView;
    private Button btnPickImage, btnUploadToServer;
    private Spinner spinnerFloor;
    private File localSavedFile;
    private ProgressDialog progressDialog;
    private SupabaseManager supabaseManager;
    private TextView textBuildingHeader;

    private String buildingName;
    private int buildingFloors;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_floor_map);

        buildingName = getIntent().getStringExtra("building_name");
        buildingFloors = getIntent().getIntExtra("building_floors", 1);

        supabaseManager = new SupabaseManager();

        textBuildingHeader = findViewById(R.id.text_building_name);
        floorMapImageView = findViewById(R.id.floorMapImageView);
        btnPickImage = findViewById(R.id.btnUploadFloorMap);
        btnUploadToServer = findViewById(R.id.btnUploadToServer);
        spinnerFloor = findViewById(R.id.spinner_floor_select);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Working...");
        progressDialog.setCancelable(false);

        textBuildingHeader.setText("Building: " + buildingName);
        setupFloorSpinner();
        setupListeners();
        loadExistingMap();
    }

    private void setupFloorSpinner() {
        String[] floors = new String[buildingFloors];
        for (int i = 0; i < buildingFloors; i++) floors[i] = "Floor " + (i + 1);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, floors);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFloor.setAdapter(adapter);

        spinnerFloor.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                loadExistingMap();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupListeners() {
        btnPickImage.setOnClickListener(v -> pickImage());

        btnUploadToServer.setOnClickListener(v -> {
            if (localSavedFile == null) {
                Toast.makeText(this, "Pick an image first", Toast.LENGTH_SHORT).show();
                return;
            }
            int floorIndex = spinnerFloor.getSelectedItemPosition() + 1;
            uploadAndSave(localSavedFile, buildingName, String.valueOf(floorIndex));
        });
    }

    private void pickImage() {
        ImagePicker.with(this)
                .crop()
                .compress(1024)
                .maxResultSize(2000, 2000)
                .start();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri selected = data.getData();
            try {
                File saved = copyUriToInternalFile(selected);
                if (saved != null) {
                    localSavedFile = saved;
                    floorMapImageView.setImageURI(Uri.fromFile(saved));
                    floorMapImageView.setVisibility(View.VISIBLE);
                    Toast.makeText(this, "Saved locally: " + saved.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    Log.i(TAG, "Saved floor map: " + saved.getAbsolutePath());
                } else {
                    Toast.makeText(this, "Failed to save image", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "Error copying image", e);
                Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == ImagePicker.RESULT_ERROR) {
            Toast.makeText(this, ImagePicker.getError(data), Toast.LENGTH_SHORT).show();
        }
    }

    private File copyUriToInternalFile(Uri sourceUri) throws Exception {
        ContentResolver resolver = getContentResolver();
        String safeBuilding = (buildingName == null || buildingName.isEmpty()) ? "building" : buildingName.replaceAll("\\s+", "_");
        String filename = "floormap_" + safeBuilding + "_" + System.currentTimeMillis() + ".jpg";
        File dir = new File(getFilesDir(), "floormaps");
        if (!dir.exists()) dir.mkdirs();
        File outFile = new File(dir, filename);

        try (InputStream in = resolver.openInputStream(sourceUri);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            out.flush();
        }
        return outFile;
    }

    private void loadExistingMap() {
        // Show floor's current map if available
        int floorIndex = spinnerFloor.getSelectedItemPosition() + 1;
        progressDialog.show();
        supabaseManager.fetchFloorMaps(buildingName, String.valueOf(floorIndex), new SupabaseManager.FetchCallback() {
            @Override
            public void onResult(java.util.List<FloorMap> floorMaps) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (floorMaps.size() > 0 && floorMaps.get(0).getFloorMapUri() != null && !floorMaps.get(0).getFloorMapUri().isEmpty()) {
                        // === Disable cache in Glide to reflect instant updates/deletions ===
                        Glide.with(AdminFloorMapActivity.this)
                                .load(floorMaps.get(0).getFloorMapUri())
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(floorMapImageView);
                        floorMapImageView.setVisibility(View.VISIBLE);
                    } else {
                        // Clear Glide cache so deleted images disappear right away!
                        Glide.with(AdminFloorMapActivity.this).clear(floorMapImageView);
                        floorMapImageView.setImageDrawable(null);
                        floorMapImageView.setVisibility(View.GONE);
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Glide.with(AdminFloorMapActivity.this).clear(floorMapImageView);
                    floorMapImageView.setImageDrawable(null);
                    floorMapImageView.setVisibility(View.GONE);
                });
            }
        });
    }

    private void uploadAndSave(File file, String buildingName, String floorNumber) {
        progressDialog.show();
        String destPath = buildingName.replaceAll("\\s+", "_").toLowerCase() + "/floor_" + floorNumber + "/" + file.getName();

        supabaseManager.uploadFile(file, destPath, new SupabaseManager.UploadCallback() {
            @Override
            public void onUploaded(String publicUrl) {
                FloorMap fm = new FloorMap();
                fm.setBuildingName(buildingName);
                fm.setFloorNumber(floorNumber);
                fm.setFloorMapUri(publicUrl);
                fm.setRoomPins(null);

                supabaseManager.saveFloorMapRecord(fm, new SupabaseManager.SupabaseCallback() {
                    @Override
                    public void onSuccess(String message) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(AdminFloorMapActivity.this, "Uploaded and saved!", Toast.LENGTH_LONG).show();
                            loadExistingMap();
                        });
                    }
                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            progressDialog.dismiss();
                            Toast.makeText(AdminFloorMapActivity.this, "DB error: " + error, Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(AdminFloorMapActivity.this, "Upload error: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}