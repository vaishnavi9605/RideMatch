package com.example.ridematch;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class DriverFilterActivity extends AppCompatActivity {

    private EditText etMinFare;
    private EditText etMaxPickup;
    private EditText etMaxRideDistance;

    private RadioGroup radioMatchingMode;
    private RadioButton rbStrict;
    private RadioButton rbSmart;

    private Button btnSavePreferences;
    private Button btnBack;

    private FirebaseFirestore db;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_filter);

        etMinFare = findViewById(R.id.etMinFare);
        etMaxPickup = findViewById(R.id.etMaxPickup);
        etMaxRideDistance = findViewById(R.id.etMaxRideDistance);

        radioMatchingMode = findViewById(R.id.radioMatchingMode);
        rbStrict = findViewById(R.id.rbStrict);
        rbSmart = findViewById(R.id.rbSmart);

        btnSavePreferences = findViewById(R.id.btnSavePreferences);
        btnBack = findViewById(R.id.btnBack);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        loadPreferences();

        btnSavePreferences.setOnClickListener(v -> savePreferences());

        btnBack.setOnClickListener(v -> finish());
    }

    private void savePreferences() {

        String minFareText =
                etMinFare.getText().toString().trim();

        String maxPickupText =
                etMaxPickup.getText().toString().trim();

        String maxRideDistanceText =
                etMaxRideDistance.getText().toString().trim();

        if (minFareText.isEmpty()) {
            etMinFare.setError("Enter minimum fare");
            etMinFare.requestFocus();
            return;
        }

        if (maxPickupText.isEmpty()) {
            etMaxPickup.setError("Enter maximum pickup distance");
            etMaxPickup.requestFocus();
            return;
        }

        if (maxRideDistanceText.isEmpty()) {
            etMaxRideDistance.setError("Enter maximum ride distance");
            etMaxRideDistance.requestFocus();
            return;
        }

        if (auth.getCurrentUser() == null) {
            Toast.makeText(
                    this,
                    "Please login again",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }

        double minFare = Double.parseDouble(minFareText);
        double maxPickup = Double.parseDouble(maxPickupText);
        double maxRideDistance =
                Double.parseDouble(maxRideDistanceText);

        String matchingMode;

        if (rbSmart.isChecked()) {
            matchingMode = "smart";
        } else {
            matchingMode = "strict";
        }

        String driverId = auth.getCurrentUser().getUid();

        Map<String, Object> preferences = new HashMap<>();

        preferences.put("minFare", minFare);
        preferences.put("maxPickupDistance", maxPickup);
        preferences.put("maxRideDistance", maxRideDistance);
        preferences.put("matchingMode", matchingMode);

        db.collection("users")
                .document(driverId)
                .update(preferences)
                .addOnSuccessListener(unused -> {

                    Toast.makeText(
                            DriverFilterActivity.this,
                            "Preferences saved successfully!",
                            Toast.LENGTH_SHORT
                    ).show();

                    finish();

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            DriverFilterActivity.this,
                            "Error: " + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    private void loadPreferences() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        String driverId = auth.getCurrentUser().getUid();

        db.collection("users")
                .document(driverId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {

                    if (!documentSnapshot.exists()) {
                        return;
                    }

                    Double minFare =
                            documentSnapshot.getDouble("minFare");

                    Double maxPickup =
                            documentSnapshot.getDouble("maxPickupDistance");

                    Double maxRide =
                            documentSnapshot.getDouble("maxRideDistance");

                    String mode =
                            documentSnapshot.getString("matchingMode");

                    if (minFare != null) {
                        etMinFare.setText(
                                String.valueOf(minFare)
                        );
                    }

                    if (maxPickup != null) {
                        etMaxPickup.setText(
                                String.valueOf(maxPickup)
                        );
                    }

                    if (maxRide != null) {
                        etMaxRideDistance.setText(
                                String.valueOf(maxRide)
                        );
                    }

                    if ("smart".equals(mode)) {
                        rbSmart.setChecked(true);
                    } else {
                        rbStrict.setChecked(true);
                    }

                });
    }
}