package com.example.ridematch;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CustomerHomeActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private ListenerRegistration activeRideListener;
    private ListenerRegistration activeDriverLocationListener;

    private EditText etPickup;
    private EditText etDestination;

    private ListView lvPickupResults;
    private ListView lvDestinationResults;

    private View cardOngoingRide;

    private TextView tvOngoingRideStatus;
    private TextView tvOngoingRideFare;
    private TextView tvOngoingRideRoute;
    private TextView tvOngoingDriverInfo;
    private TextView tvOngoingDriverPhone;
    private TextView tvOngoingDriverEta;

    private Button btnCallOngoingDriver;
    private Button btnTrackOngoingRide;

    private String activeRideId = "";
    private DocumentSnapshot activeRideDoc;

    private LocationSearchService locationSearchService;

    private LocationSearchService.LocationResult selectedPickupResult;
    private LocationSearchService.LocationResult selectedDestinationResult;


    // ============================================================
    // ACTIVITY CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_customer_home);

        auth = FirebaseAuth.getInstance();

        db = FirebaseFirestore.getInstance();

        locationSearchService =
                new LocationSearchService();


        // ========================================================
        // FIND VIEWS
        // ========================================================

        etPickup =
                findViewById(R.id.etPickup);

        etDestination =
                findViewById(R.id.etDestination);

        lvPickupResults =
                findViewById(R.id.lvPickupResults);

        lvDestinationResults =
                findViewById(R.id.lvDestinationResults);


        cardOngoingRide =
                findViewById(R.id.cardOngoingRide);

        tvOngoingRideStatus =
                findViewById(R.id.tvOngoingRideStatus);

        tvOngoingRideFare =
                findViewById(R.id.tvOngoingRideFare);

        tvOngoingRideRoute =
                findViewById(R.id.tvOngoingRideRoute);

        tvOngoingDriverInfo =
                findViewById(R.id.tvOngoingDriverInfo);

        tvOngoingDriverPhone =
                findViewById(R.id.tvOngoingDriverPhone);

        tvOngoingDriverEta =
                findViewById(R.id.tvOngoingDriverEta);

        btnCallOngoingDriver =
                findViewById(R.id.btnCallOngoingDriver);

        btnTrackOngoingRide =
                findViewById(R.id.btnTrackOngoingRide);


        // ========================================================
        // CALL DRIVER
        // ========================================================

        if (btnCallOngoingDriver != null) {

            btnCallOngoingDriver.setOnClickListener(v -> {

                if (activeRideDoc == null) {

                    Toast.makeText(
                            CustomerHomeActivity.this,
                            "No active ride",
                            Toast.LENGTH_SHORT
                    ).show();

                    return;
                }

                String phone =
                        activeRideDoc.getString(
                                "driverPhone"
                        );

                if (phone != null &&
                        !phone.trim().isEmpty()) {

                    Intent callIntent =
                            new Intent(
                                    Intent.ACTION_DIAL,
                                    Uri.parse("tel:" + phone)
                            );

                    startActivity(callIntent);

                } else {

                    Toast.makeText(
                            CustomerHomeActivity.this,
                            "Driver contact is not available",
                            Toast.LENGTH_SHORT
                    ).show();
                }
            });
        }


        // ========================================================
        // TRACK RIDE
        // ========================================================

        if (btnTrackOngoingRide != null) {

            btnTrackOngoingRide.setOnClickListener(
                    v -> trackActiveRideOnMap()
            );
        }


        // ========================================================
        // MAIN BUTTONS
        // ========================================================

        Button btnFindRide =
                findViewById(R.id.btnFindRide);

        Button btnOpenMap =
                findViewById(R.id.btnOpenMap);

        Button btnRideHistory =
                findViewById(R.id.btnRideHistory);

        Button btnLogout =
                findViewById(R.id.btnCustomerLogout);


        // ========================================================
        // SEARCH
        // ========================================================

        setupPickupSearch();

        setupDestinationSearch();


        // ========================================================
        // IMPORTANT:
        // START LISTENING FOR ACTIVE RIDE
        // ========================================================

        checkActiveOngoingRide();


        // ========================================================
        // FIND RIDE
        // ========================================================

        if (btnFindRide != null) {

            btnFindRide.setOnClickListener(
                    v -> openMapWithLocations()
            );
        }


        // ========================================================
        // OPEN MAP
        // ========================================================

        if (btnOpenMap != null) {

            btnOpenMap.setOnClickListener(
                    v -> openMapWithLocations()
            );
        }


        // ========================================================
        // RIDE HISTORY
        // ========================================================

        if (btnRideHistory != null) {

            btnRideHistory.setOnClickListener(v -> {

                Intent intent =
                        new Intent(
                                CustomerHomeActivity.this,
                                RideHistoryActivity.class
                        );

                intent.putExtra(
                        "isDriver",
                        false
                );

                startActivity(intent);
            });
        }


        // ========================================================
        // LOGOUT
        // ========================================================

        if (btnLogout != null) {

            btnLogout.setOnClickListener(v -> {

                if (activeRideListener != null) {

                    activeRideListener.remove();

                    activeRideListener = null;
                }

                if (activeDriverLocationListener != null) {

                    activeDriverLocationListener.remove();

                    activeDriverLocationListener = null;
                }

                if (locationSearchService != null) {

                    locationSearchService.shutdown();
                }

                auth.signOut();

                Intent intent =
                        new Intent(
                                CustomerHomeActivity.this,
                                LoginActivity.class
                        );

                intent.setFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK
                                |
                                Intent.FLAG_ACTIVITY_CLEAR_TASK
                );

                startActivity(intent);

                finish();
            });
        }
    }


    // ============================================================
    // PICKUP SEARCH
    // ============================================================

    private void setupPickupSearch() {

        etPickup.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }


                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        selectedPickupResult = null;

                        String query =
                                s.toString().trim();

                        if (query.length() < 2) {

                            lvPickupResults.setVisibility(
                                    View.GONE
                            );

                            return;
                        }

                        locationSearchService.search(
                                query,
                                new LocationSearchService.SearchCallback() {

                                    @Override
                                    public void onResults(
                                            List<LocationSearchService.LocationResult> results
                                    ) {

                                        showPickupResults(
                                                results
                                        );
                                    }


                                    @Override
                                    public void onError(
                                            String message
                                    ) {

                                        lvPickupResults.setVisibility(
                                                View.GONE
                                        );
                                    }
                                }
                        );
                    }


                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {
                    }
                }
        );
    }


    // ============================================================
    // SHOW PICKUP RESULTS
    // ============================================================

    private void showPickupResults(
            List<LocationSearchService.LocationResult> results
    ) {

        if (results == null ||
                results.isEmpty()) {

            lvPickupResults.setVisibility(
                    View.GONE
            );

            return;
        }

        List<String> names =
                new ArrayList<>();

        for (
                LocationSearchService.LocationResult result
                : results
        ) {

            names.add(
                    result.getDisplayName()
            );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        names
                );

        lvPickupResults.setAdapter(adapter);

        lvPickupResults.setVisibility(
                View.VISIBLE
        );

        lvPickupResults.setOnItemClickListener(
                (parent, view, position, id) -> {

                    selectedPickupResult =
                            results.get(position);

                    etPickup.setText(
                            selectedPickupResult
                                    .getDisplayName()
                    );

                    lvPickupResults.setVisibility(
                            View.GONE
                    );
                }
        );
    }


    // ============================================================
    // DESTINATION SEARCH
    // ============================================================

    private void setupDestinationSearch() {

        etDestination.addTextChangedListener(
                new TextWatcher() {

                    @Override
                    public void beforeTextChanged(
                            CharSequence s,
                            int start,
                            int count,
                            int after
                    ) {
                    }


                    @Override
                    public void onTextChanged(
                            CharSequence s,
                            int start,
                            int before,
                            int count
                    ) {

                        selectedDestinationResult = null;

                        String query =
                                s.toString().trim();

                        if (query.length() < 2) {

                            lvDestinationResults.setVisibility(
                                    View.GONE
                            );

                            return;
                        }

                        locationSearchService.search(
                                query,
                                new LocationSearchService.SearchCallback() {

                                    @Override
                                    public void onResults(
                                            List<LocationSearchService.LocationResult> results
                                    ) {

                                        showDestinationResults(
                                                results
                                        );
                                    }


                                    @Override
                                    public void onError(
                                            String message
                                    ) {

                                        lvDestinationResults.setVisibility(
                                                View.GONE
                                        );
                                    }
                                }
                        );
                    }


                    @Override
                    public void afterTextChanged(
                            Editable s
                    ) {
                    }
                }
        );
    }


    // ============================================================
    // SHOW DESTINATION RESULTS
    // ============================================================

    private void showDestinationResults(
            List<LocationSearchService.LocationResult> results
    ) {

        if (results == null ||
                results.isEmpty()) {

            lvDestinationResults.setVisibility(
                    View.GONE
            );

            return;
        }

        List<String> names =
                new ArrayList<>();

        for (
                LocationSearchService.LocationResult result
                : results
        ) {

            names.add(
                    result.getDisplayName()
            );
        }

        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(
                        this,
                        android.R.layout.simple_list_item_1,
                        names
                );

        lvDestinationResults.setAdapter(adapter);

        lvDestinationResults.setVisibility(
                View.VISIBLE
        );

        lvDestinationResults.setOnItemClickListener(
                (parent, view, position, id) -> {

                    selectedDestinationResult =
                            results.get(position);

                    etDestination.setText(
                            selectedDestinationResult
                                    .getDisplayName()
                    );

                    lvDestinationResults.setVisibility(
                            View.GONE
                    );
                }
        );
    }


    // ============================================================
    // OPEN RIDE MAP
    // ============================================================

    private void openMapWithLocations() {

        String pickupText =
                etPickup.getText()
                        .toString()
                        .trim();

        String destinationText =
                etDestination.getText()
                        .toString()
                        .trim();


        Intent intent =
                new Intent(
                        CustomerHomeActivity.this,
                        RideMapActivity.class
                );


        if (!pickupText.isEmpty()) {

            intent.putExtra(
                    "pickupText",
                    pickupText
            );

            if (selectedPickupResult != null) {

                intent.putExtra(
                        "pickupName",
                        selectedPickupResult.getName()
                );

                intent.putExtra(
                        "pickupAddress",
                        selectedPickupResult.getAddress()
                );

                intent.putExtra(
                        "pickupLat",
                        selectedPickupResult.getLatitude()
                );

                intent.putExtra(
                        "pickupLng",
                        selectedPickupResult.getLongitude()
                );
            }
        }


        if (!destinationText.isEmpty()) {

            intent.putExtra(
                    "destinationText",
                    destinationText
            );

            if (selectedDestinationResult != null) {

                intent.putExtra(
                        "destinationName",
                        selectedDestinationResult.getName()
                );

                intent.putExtra(
                        "destinationAddress",
                        selectedDestinationResult.getAddress()
                );

                intent.putExtra(
                        "destinationLat",
                        selectedDestinationResult.getLatitude()
                );

                intent.putExtra(
                        "destinationLng",
                        selectedDestinationResult.getLongitude()
                );
            }
        }


        startActivity(intent);
    }


    // ============================================================
    // CHECK ACTIVE RIDE
    // ============================================================

    private void checkActiveOngoingRide() {

        if (auth == null ||
                auth.getCurrentUser() == null ||
                db == null) {

            return;
        }

        String customerId =
                auth.getCurrentUser().getUid();


        if (activeRideListener != null) {

            activeRideListener.remove();
        }


        activeRideListener =
                db.collection("rides")
                        .whereEqualTo(
                                "customerId",
                                customerId
                        )
                        .addSnapshotListener(
                                (snapshots, error) -> {

                                    if (error != null) {

                                        hideOngoingRide();

                                        return;
                                    }


                                    if (snapshots == null ||
                                            snapshots.isEmpty()) {

                                        hideOngoingRide();

                                        return;
                                    }


                                    DocumentSnapshot foundActive =
                                            null;


                                    /*
                                     * Look for the latest active ride.
                                     *
                                     * We intentionally allow:
                                     *
                                     * searching
                                     * accepted
                                     * arrived
                                     * in_progress
                                     */

                                    for (
                                            DocumentSnapshot doc
                                            : snapshots.getDocuments()
                                    ) {

                                        String status =
                                                doc.getString(
                                                        "status"
                                                );

                                        if (
                                                "searching".equals(status)
                                                        ||
                                                        "accepted".equals(status)
                                                        ||
                                                        "arrived".equals(status)
                                                        ||
                                                        "in_progress".equals(status)
                                        ) {

                                            foundActive = doc;
                                        }
                                    }


                                    if (foundActive != null) {

                                        activeRideDoc =
                                                foundActive;

                                        activeRideId =
                                                foundActive.getId();

                                        showOngoingRideBanner(
                                                foundActive
                                        );

                                    } else {

                                        hideOngoingRide();
                                    }
                                }
                        );
    }


    // ============================================================
    // HIDE ACTIVE RIDE CARD
    // ============================================================

    private void hideOngoingRide() {

        activeRideId = "";

        activeRideDoc = null;


        if (activeDriverLocationListener != null) {

            activeDriverLocationListener.remove();

            activeDriverLocationListener = null;
        }


        if (cardOngoingRide != null) {

            cardOngoingRide.setVisibility(
                    View.GONE
            );
        }
    }


    // ============================================================
    // SHOW ACTIVE RIDE
    // ============================================================

    private void showOngoingRideBanner(
            DocumentSnapshot doc
    ) {

        if (cardOngoingRide == null) {

            return;
        }


        cardOngoingRide.setVisibility(
                View.VISIBLE
        );


        String status =
                doc.getString("status");

        String pickup =
                doc.getString("pickup");

        String destination =
                doc.getString("destination");

        Long fare =
                doc.getLong("estimatedFare");

        String driverName =
                doc.getString("driverName");

        String driverPhone =
                doc.getString("driverPhone");

        String vehicle =
                doc.getString("vehicleDetails");


        // ========================================================
        // STATUS
        // ========================================================

        if (tvOngoingRideStatus != null) {

            switch (status == null ? "" : status) {

                case "searching":

                    tvOngoingRideStatus.setText(
                            "🔍 SEARCHING FOR NEARBY DRIVERS..."
                    );

                    break;


                case "accepted":

                    tvOngoingRideStatus.setText(
                            "🟢 DRIVER CONFIRMED • EN ROUTE TO PICKUP"
                    );

                    break;


                case "arrived":

                    tvOngoingRideStatus.setText(
                            "📍 DRIVER ARRIVED AT PICKUP POINT"
                    );

                    break;


                case "in_progress":

                    tvOngoingRideStatus.setText(
                            "🛣️ TRIP IN PROGRESS • HEADING TO DESTINATION"
                    );

                    break;


                default:

                    tvOngoingRideStatus.setText(
                            "🚕 RIDE IN PROGRESS"
                    );

                    break;
            }
        }


        // ========================================================
        // FARE
        // ========================================================

        if (tvOngoingRideFare != null) {

            tvOngoingRideFare.setText(
                    "💰 ₹" +
                            (fare != null
                                    ? fare
                                    : 0)
            );
        }


        // ========================================================
        // ROUTE
        // ========================================================

        if (tvOngoingRideRoute != null) {

            tvOngoingRideRoute.setText(
                    "📍 " +
                            (
                                    pickup != null
                                            ? pickup
                                            : "Pickup"
                            )
                            +
                            " ➔ "
                            +
                            (
                                    destination != null
                                            ? destination
                                            : "Destination"
                            )
            );
        }


        // ========================================================
        // DRIVER INFORMATION
        // ========================================================

        if (tvOngoingDriverInfo != null) {

            if (driverName != null &&
                    !driverName.trim().isEmpty()) {

                String vehicleText =
                        vehicle != null &&
                                !vehicle.trim().isEmpty()
                                ? vehicle
                                : "VeloCity Cab";


                tvOngoingDriverInfo.setText(
                        "👤 Driver: " +
                                driverName +
                                "\n🚕 Vehicle: " +
                                vehicleText
                );

            } else {

                tvOngoingDriverInfo.setText(
                        "🚕 Finding your VeloCity driver..."
                );
            }
        }


        // ========================================================
        // PHONE
        // ========================================================

        if (tvOngoingDriverPhone != null) {

            if (driverPhone != null &&
                    !driverPhone.trim().isEmpty()) {

                tvOngoingDriverPhone.setText(
                        "📞 Contact: " +
                                driverPhone
                );

            } else {

                tvOngoingDriverPhone.setText(
                        "📞 Contact: Not available"
                );
            }
        }


        // ========================================================
        // CALL BUTTON
        // ========================================================

        if (btnCallOngoingDriver != null) {

            boolean hasPhone =
                    driverPhone != null &&
                            !driverPhone.trim().isEmpty();

            btnCallOngoingDriver.setVisibility(
                    hasPhone
                            ? View.VISIBLE
                            : View.GONE
            );
        }


        // ========================================================
        // ETA
        // ========================================================

        if (tvOngoingDriverEta != null) {

            if ("searching".equals(status)) {

                tvOngoingDriverEta.setText(
                        "⏱ Waiting for a driver to accept..."
                );

            } else if ("accepted".equals(status)) {

                tvOngoingDriverEta.setText(
                        "⏱ Calculating driver arrival..."
                );

            } else if ("arrived".equals(status)) {

                tvOngoingDriverEta.setText(
                        "📍 Driver is waiting at pickup"
                );

            } else if ("in_progress".equals(status)) {

                tvOngoingDriverEta.setText(
                        "⏱ Trip in progress to destination"
                );
            }
        }


        // ========================================================
        // LIVE DRIVER LOCATION
        // ========================================================

        attachActiveDriverLocationListener(
                doc
        );
    }


    // ============================================================
    // LIVE DRIVER LOCATION LISTENER
    // ============================================================

    private void attachActiveDriverLocationListener(
            DocumentSnapshot ride
    ) {

        if (activeDriverLocationListener != null) {

            activeDriverLocationListener.remove();

            activeDriverLocationListener = null;
        }


        String driverId =
                ride.getString("driverId");


        if (driverId == null ||
                driverId.trim().isEmpty()) {

            return;
        }


        Double pickupLat =
                ride.getDouble(
                        "pickupLatitude"
                );

        Double pickupLng =
                ride.getDouble(
                        "pickupLongitude"
                );


        if (pickupLat == null ||
                pickupLng == null) {

            return;
        }


        activeDriverLocationListener =
                db.collection("driverLocations")
                        .document(driverId)
                        .addSnapshotListener(
                                (location, error) -> {

                                    if (error != null ||
                                            location == null ||
                                            !location.exists()) {

                                        return;
                                    }


                                    Double driverLat =
                                            location.getDouble(
                                                    "latitude"
                                            );

                                    Double driverLng =
                                            location.getDouble(
                                                    "longitude"
                                            );


                                    if (driverLat == null ||
                                            driverLng == null) {

                                        return;
                                    }


                                    double distanceKm =
                                            calculateDistanceKm(
                                                    driverLat,
                                                    driverLng,
                                                    pickupLat,
                                                    pickupLng
                                            );


                                    /*
                                     * Approximate ETA.
                                     *
                                     * 30 km/h is used as a simple
                                     * college-project estimate.
                                     *
                                     * This is NOT a live traffic ETA.
                                     */

                                    int etaMinutes =
                                            Math.max(
                                                    1,
                                                    (int) Math.ceil(
                                                            (distanceKm / 30.0)
                                                                    * 60.0
                                                    )
                                            );


                                    String status =
                                            ride.getString(
                                                    "status"
                                            );


                                    if (tvOngoingDriverEta != null) {

                                        if (
                                                "accepted".equals(status)
                                        ) {

                                            tvOngoingDriverEta.setText(
                                                    String.format(
                                                            Locale.US,
                                                            "🚕 Driver is %.1f km away • ETA ~%d min",
                                                            distanceKm,
                                                            etaMinutes
                                                    )
                                            );

                                        } else if (
                                                "arrived".equals(status)
                                        ) {

                                            tvOngoingDriverEta.setText(
                                                    "📍 Driver has arrived at pickup"
                                            );
                                        }
                                    }
                                }
                        );
    }


    // ============================================================
    // CALCULATE DISTANCE
    // ============================================================

    private double calculateDistanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double earthRadiusKm =
                6371.0;

        double dLat =
                Math.toRadians(
                        lat2 - lat1
                );

        double dLon =
                Math.toRadians(
                        lon2 - lon1
                );

        double a =
                Math.sin(dLat / 2)
                        *
                        Math.sin(dLat / 2)
                        +
                        Math.cos(
                                Math.toRadians(lat1)
                        )
                                *
                                Math.cos(
                                        Math.toRadians(lat2)
                                )
                                *
                                Math.sin(dLon / 2)
                                *
                                Math.sin(dLon / 2);

        double c =
                2 *
                        Math.atan2(
                                Math.sqrt(a),
                                Math.sqrt(1 - a)
                        );

        return earthRadiusKm * c;
    }


    // ============================================================
    // OPEN ACTIVE RIDE ON MAP
    // ============================================================

    private void trackActiveRideOnMap() {

        if (activeRideId == null ||
                activeRideId.isEmpty()) {

            Toast.makeText(
                    this,
                    "No active ride available",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }


        Intent intent =
                new Intent(
                        CustomerHomeActivity.this,
                        RideMapActivity.class
                );


        intent.putExtra(
                "rideId",
                activeRideId
        );


        if (activeRideDoc != null) {

            String pickup =
                    activeRideDoc.getString(
                            "pickup"
                    );

            String destination =
                    activeRideDoc.getString(
                            "destination"
                    );


            Double pickupLat =
                    activeRideDoc.getDouble(
                            "pickupLatitude"
                    );

            Double pickupLng =
                    activeRideDoc.getDouble(
                            "pickupLongitude"
                    );

            Double destinationLat =
                    activeRideDoc.getDouble(
                            "destinationLatitude"
                    );

            Double destinationLng =
                    activeRideDoc.getDouble(
                            "destinationLongitude"
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
        }


        startActivity(intent);
    }


    // ============================================================
    // ACTIVITY DESTROY
    // ============================================================

    @Override
    protected void onDestroy() {

        if (activeRideListener != null) {

            activeRideListener.remove();

            activeRideListener = null;
        }


        if (activeDriverLocationListener != null) {

            activeDriverLocationListener.remove();

            activeDriverLocationListener = null;
        }


        if (locationSearchService != null) {

            locationSearchService.shutdown();
        }


        super.onDestroy();
    }
}