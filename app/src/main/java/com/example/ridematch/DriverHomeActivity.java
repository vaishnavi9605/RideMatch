package com.example.ridematch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class DriverHomeActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 2001;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private TextView tvStatus;
    private TextView tvEarnings;
    private Button btnOnline;
    private Button btnFilters;
    private Button btnLogout;

    private boolean isOnline = false;

    private ListenerRegistration pendingRidesListener;
    private final Set<String> rejectedRideIds = new HashSet<>();
    private String driverName = "VeloCity Driver";
    private String driverVehicleDetails = "MH-12 VeloCity Cab (White Dzire)";
    private String driverPhone = "+91 98765 43210";

    // Driver preferences for smart filtering
    private double driverMinFare = 0.0;
    private double driverMaxPickupDistance = 10.0;
    private double driverMaxRideDistance = 50.0;
    private String driverMatchingMode = "strict";
    private double currentDriverLat = 18.5204;
    private double currentDriverLng = 73.8567;

    private AlertDialog currentRideDialog;

    private LocationManager locationManager;
    private LocationListener locationListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_driver_home);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        locationManager =
                (LocationManager) getSystemService(
                        LOCATION_SERVICE
                );

        // --------------------------------------------------------
        // FIND VIEWS
        // --------------------------------------------------------

        tvStatus =
                findViewById(R.id.tvOnlineStatus);

        tvEarnings =
                findViewById(R.id.tvEarnings);

        btnOnline =
                findViewById(R.id.btnOnline);

        btnFilters =
                findViewById(R.id.btnFilters);

        Button btnDriverHistory =
                findViewById(R.id.btnDriverHistory);

        btnLogout =
                findViewById(R.id.btnDriverLogout);

        loadDriverEarnings();

        // --------------------------------------------------------
        // ONLINE / OFFLINE BUTTON
        // --------------------------------------------------------

        btnOnline.setOnClickListener(
                v -> toggleOnlineStatus()
        );

        // --------------------------------------------------------
        // DRIVER FILTERS & HISTORY
        // --------------------------------------------------------

        btnFilters.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DriverHomeActivity.this,
                            DriverFilterActivity.class
                    );

            startActivity(intent);
        });

        btnDriverHistory.setOnClickListener(v -> {

            Intent intent =
                    new Intent(
                            DriverHomeActivity.this,
                            RideHistoryActivity.class
                    );
            intent.putExtra("isDriver", true);

            startActivity(intent);
        });

        // --------------------------------------------------------
        // LOGOUT
        // --------------------------------------------------------

        btnLogout.setOnClickListener(v -> {

            setDriverOffline();

            stopLocationTracking();

            auth.signOut();

            Intent intent =
                    new Intent(
                            DriverHomeActivity.this,
                            LoginActivity.class
                    );
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

            startActivity(intent);

            finish();
        });

        // --------------------------------------------------------
        // LOCATION LISTENER
        // --------------------------------------------------------

        createLocationListener();

        // --------------------------------------------------------
        // LOAD DRIVER STATUS
        // --------------------------------------------------------

        loadDriverStatus();
    }

    // ============================================================
    // TOGGLE ONLINE STATUS
    // ============================================================

    private void toggleOnlineStatus() {

        if (auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Please login again",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (!isOnline) {

            startDriverOnlineProcess();

        } else {

            setDriverOffline();
        }
    }

    // ============================================================
    // START ONLINE PROCESS
    // ============================================================

    private void startDriverOnlineProcess() {

        if (auth == null || auth.getCurrentUser() == null) {
            Toast.makeText(this, "Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        String driverId = auth.getCurrentUser().getUid();

        // 1. Immediately update UI & local state
        isOnline = true;

        if (tvStatus != null) {
            tvStatus.setText("Status: ONLINE 🟢");
        }

        if (btnOnline != null) {
            btnOnline.setText("GO OFFLINE 🔴");
        }

        Toast.makeText(this, "🟢 You are now ONLINE", Toast.LENGTH_SHORT).show();

        // 2. Request location permission if missing (non-blocking)
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }

        // 3. Update Firestore users collection
        Map<String, Object> statusData = new HashMap<>();
        statusData.put("isOnline", true);
        statusData.put("role", "driver");
        statusData.put("updatedAt", Timestamp.now());

        if (db != null) {
            db.collection("users").document(driverId)
                    .set(statusData, com.google.firebase.firestore.SetOptions.merge());

            // 4. Update driverLocations collection
            Map<String, Object> locationData = new HashMap<>();
            locationData.put("driverId", driverId);
            locationData.put("latitude", 18.5204);
            locationData.put("longitude", 73.8567);
            locationData.put("isOnline", true);
            locationData.put("updatedAt", Timestamp.now());

            db.collection("driverLocations").document(driverId)
                    .set(locationData, com.google.firebase.firestore.SetOptions.merge());
        }

        // 5. Start GPS tracking & listen for pending ride requests
        startLocationTracking();
        loadDriverInfo();
        startPendingRidesListener();
    }

    // ============================================================
    // CREATE LOCATION LISTENER
    // ============================================================

    private void createLocationListener() {

        locationListener =
                new LocationListener() {

                    @Override
                    public void onLocationChanged(
                            @NonNull Location location
                    ) {

                        if (!isOnline) {
                            return;
                        }

                        uploadDriverLocation(
                                location
                        );
                    }

                    @Override
                    public void onProviderEnabled(
                            @NonNull String provider
                    ) {

                        Toast.makeText(
                                DriverHomeActivity.this,
                                "Location provider enabled",
                                Toast.LENGTH_SHORT
                        ).show();
                    }

                    @Override
                    public void onProviderDisabled(
                            @NonNull String provider
                    ) {

                        Toast.makeText(
                                DriverHomeActivity.this,
                                "Location provider disabled",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                };
    }

    // ============================================================
    // START LOCATION TRACKING
    // ============================================================

    @SuppressWarnings("MissingPermission")
    private void startLocationTracking() {

        if (!hasLocationPermission()) {
            return;
        }

        if (locationManager == null) {

            Toast.makeText(
                    this,
                    "Location service unavailable",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        try {

            // ----------------------------------------------------
            // GPS PROVIDER
            // ----------------------------------------------------

            if (locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
            )) {

                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        5000,
                        10,
                        locationListener,
                        Looper.getMainLooper()
                );
            }

            // ----------------------------------------------------
            // NETWORK PROVIDER
            // ----------------------------------------------------

            if (locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
            )) {

                locationManager.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        5000,
                        10,
                        locationListener,
                        Looper.getMainLooper()
                );
            }

            // ----------------------------------------------------
            // TRY LAST KNOWN LOCATION IMMEDIATELY
            // ----------------------------------------------------

            Location lastLocation =
                    getLastKnownLocation();

            if (lastLocation != null) {

                uploadDriverLocation(
                        lastLocation
                );

            } else {

                Location fallbackLoc = new Location("fallback");
                fallbackLoc.setLatitude(18.5204);
                fallbackLoc.setLongitude(73.8567);
                uploadDriverLocation(fallbackLoc);
            }

        } catch (SecurityException e) {

            Toast.makeText(
                    this,
                    "Location permission is required:\n"
                            + e.getMessage(),
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    // ============================================================
    // GET LAST KNOWN LOCATION
    // ============================================================

    @SuppressWarnings("MissingPermission")
    private Location getLastKnownLocation() {

        if (!hasLocationPermission()) {
            return null;
        }

        Location bestLocation = null;

        try {

            // ----------------------------------------------------
            // GPS LAST LOCATION
            // ----------------------------------------------------

            if (locationManager.isProviderEnabled(
                    LocationManager.GPS_PROVIDER
            )) {

                Location gpsLocation =
                        locationManager.getLastKnownLocation(
                                LocationManager.GPS_PROVIDER
                        );

                if (gpsLocation != null) {

                    bestLocation =
                            gpsLocation;
                }
            }

            // ----------------------------------------------------
            // NETWORK LAST LOCATION
            // ----------------------------------------------------

            if (locationManager.isProviderEnabled(
                    LocationManager.NETWORK_PROVIDER
            )) {

                Location networkLocation =
                        locationManager.getLastKnownLocation(
                                LocationManager.NETWORK_PROVIDER
                        );

                if (networkLocation != null) {

                    if (bestLocation == null ||
                            networkLocation.getTime()
                                    > bestLocation.getTime()) {

                        bestLocation =
                                networkLocation;
                    }
                }
            }

        } catch (SecurityException e) {

            return null;
        }

        return bestLocation;
    }

    // ============================================================
    // UPLOAD DRIVER LOCATION TO FIRESTORE
    // ============================================================

    private void uploadDriverLocation(
            Location location
    ) {

        if (auth.getCurrentUser() == null) {
            return;
        }

        if (!isOnline) {
            return;
        }

        String driverId =
                auth.getCurrentUser().getUid();

        double latitude =
                location.getLatitude();

        double longitude =
                location.getLongitude();

        currentDriverLat = latitude;
        currentDriverLng = longitude;

        // --------------------------------------------------------
        // GPS DIAGNOSTIC
        // --------------------------------------------------------

        Toast.makeText(
                DriverHomeActivity.this,
                "GPS received:\n"
                        + latitude
                        + ", "
                        + longitude,
                Toast.LENGTH_SHORT
        ).show();

        Map<String, Object> locationData =
                new HashMap<>();

        locationData.put(
                "driverId",
                driverId
        );

        locationData.put(
                "latitude",
                latitude
        );

        locationData.put(
                "longitude",
                longitude
        );

        locationData.put(
                "isOnline",
                true
        );

        locationData.put(
                "updatedAt",
                Timestamp.now()
        );

        locationData.put(
                "accuracy",
                (double) location.getAccuracy()
        );

        if (location.hasSpeed()) {

            locationData.put(
                    "speed",
                    (double) location.getSpeed()
            );
        }

        // --------------------------------------------------------
        // FIRESTORE DRIVER LOCATION
        // --------------------------------------------------------

        db.collection("driverLocations")
                .document(driverId)
                .set(locationData)
                .addOnSuccessListener(unused -> {

                    Toast.makeText(
                            DriverHomeActivity.this,
                            "LOCATION UPLOADED TO FIREBASE ✓",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            DriverHomeActivity.this,
                            "LOCATION FIREBASE ERROR:\n"
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ============================================================
    // STOP LOCATION TRACKING
    // ============================================================

    @SuppressWarnings("MissingPermission")
    private void stopLocationTracking() {

        if (locationManager == null ||
                locationListener == null) {

            return;
        }

        if (!hasLocationPermission()) {
            return;
        }

        try {

            locationManager.removeUpdates(
                    locationListener
            );

        } catch (SecurityException e) {

            // Permission was revoked.
        }
    }

    // ============================================================
    // LISTEN FOR PENDING RIDE REQUESTS
    // ============================================================

    @Override
    protected void onResume() {
        super.onResume();
        loadDriverInfo();
    }

    private void loadDriverInfo() {
        if (auth == null || auth.getCurrentUser() == null || db == null) {
            return;
        }
        String uid = auth.getCurrentUser().getUid();
        db.collection("users").document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        String name = snapshot.getString("name");
                        if (name != null && !name.isEmpty()) {
                            driverName = name;
                        }
                        String phone = snapshot.getString("phone");
                        if (phone != null && !phone.isEmpty()) {
                            driverPhone = phone;
                        }
                        String model = snapshot.getString("vehicleModel");
                        String number = snapshot.getString("vehicleNumber");
                        if (model != null || number != null) {
                            driverVehicleDetails = (model != null ? model : "VeloCity Cab")
                                    + " (" + (number != null ? number : "MH-12") + ")";
                        }

                        // Load driver filter preferences
                        Double minFare = snapshot.getDouble("minFare");
                        Double maxPickup = snapshot.getDouble("maxPickupDistance");
                        Double maxRide = snapshot.getDouble("maxRideDistance");
                        String mode = snapshot.getString("matchingMode");

                        if (minFare != null) driverMinFare = minFare;
                        if (maxPickup != null) driverMaxPickupDistance = maxPickup;
                        if (maxRide != null) driverMaxRideDistance = maxRide;
                        if (mode != null) driverMatchingMode = mode;

                        TextView tvPhone = findViewById(R.id.tvDriverPhoneDisplay);
                        TextView tvLicence = findViewById(R.id.tvDriverLicenceDisplay);
                        TextView tvVehicle = findViewById(R.id.tvDriverVehicleDisplay);
                        TextView tvPrefSummary = findViewById(R.id.tvActivePreferencesSummary);

                        if (tvPhone != null) tvPhone.setText("📞 " + driverPhone);
                        if (tvVehicle != null) tvVehicle.setText("🚕 Vehicle: " + driverVehicleDetails);

                        String licence = snapshot.getString("licenceNumber");
                        String rc = snapshot.getString("rcNumber");
                        if (tvLicence != null) {
                            tvLicence.setText("🪪 Licence: " + (licence != null ? licence : "DL-14201100")
                                    + " • RC: " + (rc != null ? rc : "RC-MH12"));
                        }

                        if (tvPrefSummary != null) {
                            tvPrefSummary.setText(String.format(
                                    Locale.US,
                                    "⚙️ Active Preferences: Min Fare ₹%.0f • Max Pickup %.1fkm • Max Trip %.1fkm (%s)",
                                    driverMinFare, driverMaxPickupDistance, driverMaxRideDistance, driverMatchingMode.toUpperCase()
                            ));
                        }
                    }
                });
    }

    private void startPendingRidesListener() {
        if (db == null) {
            return;
        }
        if (pendingRidesListener != null) {
            pendingRidesListener.remove();
        }

        pendingRidesListener = db.collection("rides")
                .whereEqualTo("status", "searching")
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null || snapshots == null || snapshots.isEmpty() || !isOnline) {
                        return;
                    }

                    long nowMillis = System.currentTimeMillis();
                    long maxAgeMillis = 2 * 60 * 1000; // 2 minutes expiration limit

                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        String rideId = doc.getId();
                        if (rejectedRideIds.contains(rideId)) {
                            continue;
                        }

                        Timestamp createdAt = doc.getTimestamp("createdAt");
                        if (createdAt != null) {
                            long ageMillis = nowMillis - createdAt.toDate().getTime();
                            if (ageMillis > maxAgeMillis) {
                                // Mark older ride request as expired in Firestore
                                Map<String, Object> update = new HashMap<>();
                                update.put("status", "expired");
                                db.collection("rides").document(rideId)
                                        .set(update, com.google.firebase.firestore.SetOptions.merge());
                                continue;
                            }
                        }

                        // ============================================================
                        // DRIVER RIDE PREFERENCE FILTERING ENGINE
                        // ============================================================

                        Long fareLong = doc.getLong("estimatedFare");
                        double estimatedFare = fareLong != null ? fareLong.doubleValue() : 0.0;

                        Double pickupLat = doc.getDouble("pickupLatitude");
                        Double pickupLng = doc.getDouble("pickupLongitude");
                        Double rideDistanceKm = doc.getDouble("distanceKm");

                        double pickupDistanceKm = 0.0;
                        if (pickupLat != null && pickupLng != null) {
                            pickupDistanceKm = calculateDistanceKm(currentDriverLat, currentDriverLng, pickupLat, pickupLng);
                        }

                        // FILTER 1: MINIMUM FARE PREFERENCE
                        if (estimatedFare < driverMinFare) {
                            Toast.makeText(DriverHomeActivity.this,
                                    String.format(Locale.US, "🛡️ Preference Filter: Skipped ride (Fare ₹%.0f < Your Min Fare ₹%.0f)",
                                            estimatedFare, driverMinFare),
                                    Toast.LENGTH_SHORT).show();
                            continue; // SKIP THIS RIDE - DOES NOT MATCH DRIVER'S MIN FARE PREFERENCE
                        }

                        // FILTER 2: MAXIMUM PICKUP DISTANCE PREFERENCE
                        if (pickupDistanceKm > driverMaxPickupDistance) {
                            Toast.makeText(DriverHomeActivity.this,
                                    String.format(Locale.US, "🛡️ Preference Filter: Skipped ride (Pickup %.1f km > Your Max Radius %.1f km)",
                                            pickupDistanceKm, driverMaxPickupDistance),
                                    Toast.LENGTH_SHORT).show();
                            continue; // SKIP THIS RIDE - PICKUP TOO FAR AWAY FOR DRIVER
                        }

                        // FILTER 3: MAXIMUM TRIP DISTANCE PREFERENCE
                        if (rideDistanceKm != null && rideDistanceKm > driverMaxRideDistance) {
                            Toast.makeText(DriverHomeActivity.this,
                                    String.format(Locale.US, "🛡️ Preference Filter: Skipped ride (Trip %.1f km > Your Max Trip %.1f km)",
                                            rideDistanceKm, driverMaxRideDistance),
                                    Toast.LENGTH_SHORT).show();
                            continue; // SKIP THIS RIDE - TRIP TOO LONG FOR DRIVER
                        }

                        // ALL DRIVER PREFERENCES SATISFIED! SHOW POPUP NOTIFICATION
                        showRideRequestNotification(doc);
                        break;
                    }
                });
    }

    private double calculateDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private void showRideRequestNotification(DocumentSnapshot rideDoc) {
        if (currentRideDialog != null && currentRideDialog.isShowing()) {
            return;
        }

        String rideId = rideDoc.getId();

        Timestamp createdAt = rideDoc.getTimestamp("createdAt");
        if (createdAt != null) {
            long ageMillis = System.currentTimeMillis() - createdAt.toDate().getTime();
            if (ageMillis > 2 * 60 * 1000) {
                // Ride request is older than 2 minutes - do not show popup to driver
                return;
            }
        }
        String pickup = rideDoc.getString("pickup");
        String destination = rideDoc.getString("destination");
        Long fare = rideDoc.getLong("estimatedFare");
        Double distance = rideDoc.getDouble("distanceKm");

        String message = String.format(
                Locale.US,
                "📍 Pickup: %s\n🔴 Destination: %s\n💰 Fare: ₹%d\n🛣 Distance: %.1f km",
                pickup != null ? pickup : "Near Pune",
                destination != null ? destination : "Pune",
                fare != null ? fare : 120,
                distance != null ? distance : 5.0
        );

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("🚕 NEW RIDE REQUEST!")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton("ACCEPT RIDE ✓", (dialog, which) -> {
                    acceptRide(rideId, rideDoc);
                })
                .setNegativeButton("REJECT ✕", (dialog, which) -> {
                    rejectedRideIds.add(rideId);
                    dialog.dismiss();
                    Toast.makeText(DriverHomeActivity.this, "Ride request rejected", Toast.LENGTH_SHORT).show();
                });

        currentRideDialog = builder.create();
        currentRideDialog.show();
    }

    private void acceptRide(
            String rideId,
            DocumentSnapshot rideDoc
    ) {

        if (auth == null ||
                auth.getCurrentUser() == null ||
                db == null) {

            Toast.makeText(
                    this,
                    "Please login again",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        String driverId =
                auth.getCurrentUser().getUid();


        /*
         * IMPORTANT:
         *
         * Reload the driver's profile immediately before
         * accepting the ride.
         *
         * This prevents the default driver name/phone/
         * vehicle values from being written if the profile
         * has not finished loading yet.
         */

        db.collection("users")
                .document(driverId)
                .get()
                .addOnSuccessListener(driverDoc -> {

                    String finalDriverName =
                            driverName;

                    String finalDriverPhone =
                            driverPhone;

                    String finalVehicleDetails =
                            driverVehicleDetails;


                    // ------------------------------------------------
                    // DRIVER NAME
                    // ------------------------------------------------

                    if (driverDoc.exists()) {

                        String name =
                                driverDoc.getString("name");

                        if (name != null &&
                                !name.trim().isEmpty()) {

                            finalDriverName =
                                    name;
                        }


                        // --------------------------------------------
                        // DRIVER PHONE
                        // --------------------------------------------

                        String phone =
                                driverDoc.getString("phone");

                        if (phone == null ||
                                phone.trim().isEmpty()) {

                            phone =
                                    driverDoc.getString(
                                            "mobile"
                                    );
                        }

                        if (phone != null &&
                                !phone.trim().isEmpty()) {

                            finalDriverPhone =
                                    phone;
                        }


                        // --------------------------------------------
                        // VEHICLE
                        // --------------------------------------------

                        String model =
                                driverDoc.getString(
                                        "vehicleModel"
                                );

                        String number =
                                driverDoc.getString(
                                        "vehicleNumber"
                                );


                        if ((model != null &&
                                !model.trim().isEmpty())
                                ||
                                (number != null &&
                                        !number.trim().isEmpty())) {

                            String vehicleModel =
                                    model != null &&
                                            !model.trim().isEmpty()
                                            ? model
                                            : "VeloCity Cab";


                            String vehicleNumber =
                                    number != null &&
                                            !number.trim().isEmpty()
                                            ? number
                                            : "N/A";


                            finalVehicleDetails =
                                    vehicleModel
                                            + " ("
                                            + vehicleNumber
                                            + ")";
                        }
                    }


                    // ------------------------------------------------
                    // UPDATE RIDE
                    // ------------------------------------------------

                    Map<String, Object> update =
                            new HashMap<>();


                    update.put(
                            "status",
                            "accepted"
                    );


                    update.put(
                            "driverId",
                            driverId
                    );


                    update.put(
                            "driverName",
                            finalDriverName
                    );


                    update.put(
                            "driverPhone",
                            finalDriverPhone
                    );


                    update.put(
                            "vehicleDetails",
                            finalVehicleDetails
                    );


                    update.put(
                            "acceptedAt",
                            Timestamp.now()
                    );


                    // ------------------------------------------------
                    // PRESERVE / COPY IMPORTANT RIDE INFORMATION
                    // ------------------------------------------------

                    /*
                     * These values are already stored on the ride.
                     * We pass them to RideMapActivity so the driver
                     * navigation screen has the exact locations.
                     */

                    Double pickupLat =
                            rideDoc.getDouble(
                                    "pickupLatitude"
                            );

                    Double pickupLng =
                            rideDoc.getDouble(
                                    "pickupLongitude"
                            );

                    Double destinationLat =
                            rideDoc.getDouble(
                                    "destinationLatitude"
                            );

                    Double destinationLng =
                            rideDoc.getDouble(
                                    "destinationLongitude"
                            );


                    // ------------------------------------------------
                    // WRITE ACCEPTANCE
                    // ------------------------------------------------

                    db.collection("rides")
                            .document(rideId)
                            .set(
                                    update,
                                    com.google.firebase.firestore
                                            .SetOptions
                                            .merge()
                            )
                            .addOnSuccessListener(
                                    unused -> {

                                        Toast.makeText(
                                                DriverHomeActivity.this,
                                                "Ride Accepted ✓",
                                                Toast.LENGTH_LONG
                                        ).show();


                                        if (currentRideDialog != null &&
                                                currentRideDialog.isShowing()) {

                                            currentRideDialog.dismiss();
                                        }

                                        currentRideDialog =
                                                null;


                                        // ============================================
                                        // OPEN DRIVER NAVIGATION
                                        // ============================================

                                        Intent intent =
                                                new Intent(
                                                        DriverHomeActivity.this,
                                                        RideMapActivity.class
                                                );


                                        intent.putExtra(
                                                "rideId",
                                                rideId
                                        );


                                        intent.putExtra(
                                                "isDriverMode",
                                                true
                                        );


                                        // --------------------------------------------
                                        // TEXT
                                        // --------------------------------------------

                                        String pickup =
                                                rideDoc.getString(
                                                        "pickup"
                                                );

                                        String destination =
                                                rideDoc.getString(
                                                        "destination"
                                                );


                                        if (pickup != null) {

                                            intent.putExtra(
                                                    "pickupText",
                                                    pickup
                                            );
                                        }


                                        if (destination != null) {

                                            intent.putExtra(
                                                    "destinationText",
                                                    destination
                                            );
                                        }


                                        // --------------------------------------------
                                        // PICKUP COORDINATES
                                        // --------------------------------------------

                                        if (pickupLat != null) {

                                            intent.putExtra(
                                                    "pickupLat",
                                                    pickupLat
                                            );
                                        }


                                        if (pickupLng != null) {

                                            intent.putExtra(
                                                    "pickupLng",
                                                    pickupLng
                                            );
                                        }


                                        // --------------------------------------------
                                        // DESTINATION COORDINATES
                                        // --------------------------------------------

                                        if (destinationLat != null) {

                                            intent.putExtra(
                                                    "destinationLat",
                                                    destinationLat
                                            );
                                        }


                                        if (destinationLng != null) {

                                            intent.putExtra(
                                                    "destinationLng",
                                                    destinationLng
                                            );
                                        }


                                        // --------------------------------------------
                                        // OPEN NAVIGATION
                                        // --------------------------------------------

                                        startActivity(
                                                intent
                                        );
                                    }
                            )
                            .addOnFailureListener(
                                    e -> {

                                        Toast.makeText(
                                                DriverHomeActivity.this,
                                                "Failed to accept ride:\n"
                                                        + e.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                            );
                })
                .addOnFailureListener(
                        e -> {

                            Toast.makeText(
                                    DriverHomeActivity.this,
                                    "Unable to load driver profile:\n"
                                            + e.getMessage(),
                                    Toast.LENGTH_LONG
                            ).show();
                        }
                );
    }

    private void loadDriverEarnings() {
        if (auth == null || auth.getCurrentUser() == null || db == null) {
            return;
        }
        String driverId = auth.getCurrentUser().getUid();
        db.collection("rides")
                .whereEqualTo("driverId", driverId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    long totalEarnings = 0;
                    int rideCount = 0;
                    if (queryDocumentSnapshots != null) {
                        for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                            Long fare = doc.getLong("estimatedFare");
                            if (fare != null) {
                                totalEarnings += fare;
                            }
                            rideCount++;
                        }
                    }
                    if (tvEarnings != null) {
                        tvEarnings.setText("₹" + totalEarnings);
                    }
                });
    }

    // ============================================================
    // GO OFFLINE
    // ============================================================

    private void setDriverOffline() {

        if (pendingRidesListener != null) {
            pendingRidesListener.remove();
            pendingRidesListener = null;
        }

        if (auth.getCurrentUser() == null) {
            return;
        }

        String driverId =
                auth.getCurrentUser().getUid();

        // Stop GPS immediately
        stopLocationTracking();

        Map<String, Object> statusData =
                new HashMap<>();

        statusData.put(
                "isOnline",
                false
        );

        statusData.put(
                "updatedAt",
                Timestamp.now()
        );

        db.collection("users")
                .document(driverId)
                .set(statusData, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(unused -> {

                    isOnline = false;

                    tvStatus.setText(
                            "Status: OFFLINE 🔴"
                    );

                    btnOnline.setText(
                            "GO ONLINE 🟢"
                    );

                    // ------------------------------------------------
                    // MARK DRIVER LOCATION OFFLINE
                    // ------------------------------------------------

                    Map<String, Object> locationData =
                            new HashMap<>();

                    locationData.put(
                            "isOnline",
                            false
                    );

                    locationData.put(
                            "updatedAt",
                            Timestamp.now()
                    );

                    db.collection("driverLocations")
                            .document(driverId)
                            .set(locationData, com.google.firebase.firestore.SetOptions.merge())
                            .addOnFailureListener(e -> {

                                // Document may not exist yet.
                                // This is not fatal.
                            });

                    Toast.makeText(
                            DriverHomeActivity.this,
                            "You are now OFFLINE",
                            Toast.LENGTH_SHORT
                    ).show();

                })
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            DriverHomeActivity.this,
                            "Unable to go offline:\n"
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ============================================================
    // LOAD DRIVER STATUS
    // ============================================================

    private void loadDriverStatus() {

        if (auth.getCurrentUser() == null) {
            return;
        }

        String driverId =
                auth.getCurrentUser().getUid();

        db.collection("users")
                .document(driverId)
                .get()
                .addOnSuccessListener(
                        documentSnapshot -> {

                            if (!documentSnapshot.exists()) {
                                return;
                            }

                            Boolean online =
                                    documentSnapshot
                                            .getBoolean(
                                                    "isOnline"
                                            );

                            if (online != null &&
                                    online) {

                                isOnline = true;

                                tvStatus.setText(
                                        "Status: ONLINE 🟢"
                                );

                                btnOnline.setText(
                                        "GO OFFLINE 🔴"
                                );

                                // Resume location tracking
                                if (hasLocationPermission() &&
                                        isLocationEnabled()) {

                                    startLocationTracking();
                                }

                            } else {

                                isOnline = false;

                                tvStatus.setText(
                                        "Status: OFFLINE 🔴"
                                );

                                btnOnline.setText(
                                        "GO ONLINE 🟢"
                                );
                            }
                        }
                )
                .addOnFailureListener(e -> {

                    Toast.makeText(
                            DriverHomeActivity.this,
                            "Unable to load driver status:\n"
                                    + e.getMessage(),
                            Toast.LENGTH_LONG
                    ).show();

                });
    }

    // ============================================================
    // CHECK LOCATION PERMISSION
    // ============================================================

    private boolean hasLocationPermission() {

        return ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                ||
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;
    }

    // ============================================================
    // CHECK GPS ENABLED
    // ============================================================

    private boolean isLocationEnabled() {

        if (locationManager == null) {
            return false;
        }

        boolean gpsEnabled =
                locationManager.isProviderEnabled(
                        LocationManager.GPS_PROVIDER
                );

        boolean networkEnabled =
                locationManager.isProviderEnabled(
                        LocationManager.NETWORK_PROVIDER
                );

        return gpsEnabled || networkEnabled;
    }

    // ============================================================
    // PERMISSION RESULT
    // ============================================================

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {

        super.onRequestPermissionsResult(
                requestCode,
                permissions,
                grantResults
        );

        if (requestCode ==
                LOCATION_PERMISSION_REQUEST_CODE) {

            boolean permissionGranted =
                    false;

            for (int result : grantResults) {

                if (result ==
                        PackageManager.PERMISSION_GRANTED) {

                    permissionGranted = true;
                    break;
                }
            }

            if (permissionGranted) {

                Toast.makeText(
                        this,
                        "Location permission granted",
                        Toast.LENGTH_SHORT
                ).show();

                startDriverOnlineProcess();

            } else {

                Toast.makeText(
                        this,
                        "Location permission is required to go online",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ============================================================
    // ACTIVITY PAUSE
    // ============================================================

    @Override
    protected void onPause() {

        super.onPause();

        /*
         * For the current project stage, we intentionally
         * keep location tracking active while the driver
         * remains online.
         *
         * A foreground service can be added later for
         * reliable background tracking.
         */
    }

    // ============================================================
    // ACTIVITY DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        /*
         * Always release the Activity's location callbacks.
         *
         * Background tracking will later be moved into a
         * foreground service.
         */
        stopLocationTracking();

        super.onDestroy();
    }
}