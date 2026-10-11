package com.example.ridematch;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import org.maplibre.android.MapLibre;
import org.maplibre.android.module.http.HttpRequestUtil;
import okhttp3.OkHttpClient;
import org.maplibre.android.annotations.Icon;
import org.maplibre.android.annotations.IconFactory;
import org.maplibre.android.annotations.Marker;
import org.maplibre.android.annotations.MarkerOptions;
import org.maplibre.android.camera.CameraPosition;
import org.maplibre.android.camera.CameraUpdateFactory;
import org.maplibre.android.geometry.LatLng;
import org.maplibre.android.geometry.LatLngBounds;
import org.maplibre.android.location.LocationComponent;
import org.maplibre.android.location.LocationComponentActivationOptions;
import org.maplibre.android.location.modes.CameraMode;
import org.maplibre.android.location.modes.RenderMode;
import org.maplibre.android.maps.MapLibreMap;
import org.maplibre.android.maps.MapView;
import org.maplibre.android.maps.Style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class RideMapActivity extends AppCompatActivity {

    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private static final double PUNE_LAT = 18.5204;
    private static final double PUNE_LNG = 73.8567;

    private static final String OSM_STYLE_JSON = "{"
            + "\"version\": 8,"
            + "\"sources\": {"
            + "  \"osmfr\": {"
            + "    \"type\": \"raster\","
            + "    \"tiles\": ["
            + "      \"https://a.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png\","
            + "      \"https://b.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png\","
            + "      \"https://c.tile.openstreetmap.fr/osmfr/{z}/{x}/{y}.png\""
            + "    ],"
            + "    \"tileSize\": 256,"
            + "    \"attribution\": \"© OpenStreetMap France, © OpenStreetMap contributors\""
            + "  }"
            + "},"
            + "\"layers\": ["
            + "  {"
            + "    \"id\": \"osmfr-layer\","
            + "    \"type\": \"raster\","
            + "    \"source\": \"osmfr\","
            + "    \"minzoom\": 0,"
            + "    \"maxzoom\": 19"
            + "  }"
            + "]"
            + "}";

    // ============================================================
    // MAP
    // ============================================================

    private MapView mapView;
    private MapLibreMap mapLibreMap;
    private Style loadedStyle;

    // ============================================================
    // FIREBASE
    // ============================================================

    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private ListenerRegistration driverLocationListener;
    private RouteService routeService;
    private DriverMatchingService driverMatchingService;

    // Current customer ride
    private String currentRideId = "";

    // ============================================================
    // SEARCH
    // ============================================================

    private EditText etPickupSearch;
    private EditText etDestinationSearch;

    private ListView lvPickupResults;
    private ListView lvDestinationResults;

    private LocationSearchService locationSearchService;

    // ============================================================
    // TEXT
    // ============================================================

    private TextView tvSelectionTitle;
    private TextView tvPickup;
    private TextView tvDestination;
    private TextView tvRouteInfo;

    private View cardDriverDetails;
    private TextView tvDriverName;
    private TextView tvVehicleDetails;
    private TextView tvDriverEta;
    private TextView tvDriverPhone;
    private Button btnCallDriver;

    private View cardCustomerDetails;
    private TextView tvCustomerName;
    private TextView tvCustomerPhone;
    private Button btnCallCustomer;

    private String driverPhone = "+91 98765 43210";
    private String customerPhone = "+91 98765 43210";

    private ListenerRegistration currentRideListener;
    private ListenerRegistration acceptedDriverLocationListener;

    // ============================================================
    // BUTTONS
    // ============================================================

    private Button btnUseCurrentLocation;
    private Button btnSelectDestination;
    private Button btnConfirmLocations;
    private Button btnClearLocations;

    private ImageButton fabMyLocation;

    // ============================================================
    // LOCATIONS
    // ============================================================

    private LatLng pickupLocation;
    private LatLng destinationLocation;

    private String pickupName = "";
    private String pickupAddress = "";

    private String destinationName = "";
    private String destinationAddress = "";

    // ============================================================
    // CUSTOMER MARKERS
    // ============================================================

    private Marker pickupMarker;
    private Marker destinationMarker;

    // ============================================================
    // DRIVER MARKERS & ICONS
    // ============================================================

    private final Map<String, Marker> driverMarkers =
            new HashMap<>();

    private Icon cabIcon;

    private Icon getCabIcon() {
        if (cabIcon == null) {
            Bitmap bitmap = getBitmapFromVectorDrawable(this, R.drawable.ic_cab);
            if (bitmap != null) {
                cabIcon = IconFactory.getInstance(this).fromBitmap(bitmap);
            }
        }
        return cabIcon;
    }

    private Bitmap getBitmapFromVectorDrawable(android.content.Context context, int drawableId) {
        Drawable drawable = ContextCompat.getDrawable(context, drawableId);
        if (drawable == null) {
            return null;
        }
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth() > 0 ? drawable.getIntrinsicWidth() : 96,
                drawable.getIntrinsicHeight() > 0 ? drawable.getIntrinsicHeight() : 96,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // ============================================================
    // STATE
    // ============================================================

    private boolean selectingDestination = false;
    private boolean locationsConfirmed = false;

    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        MapLibre.getInstance(this);

        try {
            OkHttpClient customClient = new OkHttpClient.Builder()
                    .addInterceptor(chain -> chain.proceed(
                            chain.request().newBuilder()
                                    .header("User-Agent", "VeloCityCollegeProject/1.0 (Android; com.example.ridematch)")
                                    .build()
                    ))
                    .build();
            HttpRequestUtil.setOkHttpClient(customClient);
        } catch (Exception ignored) {
        }

        setContentView(R.layout.activity_ride_map);

        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();

        // --------------------------------------------------------
        // FIND VIEWS
        // --------------------------------------------------------

        mapView =
                findViewById(R.id.mapView);

        etPickupSearch =
                findViewById(R.id.etPickupSearch);

        etDestinationSearch =
                findViewById(R.id.etDestinationSearch);

        lvPickupResults =
                findViewById(R.id.lvPickupResults);

        lvDestinationResults =
                findViewById(R.id.lvDestinationResults);

        tvSelectionTitle =
                findViewById(R.id.tvSelectionTitle);

        tvPickup =
                findViewById(R.id.tvPickup);

        tvDestination =
                findViewById(R.id.tvDestination);

        tvRouteInfo =
                findViewById(R.id.tvRouteInfo);

        cardDriverDetails =
                findViewById(R.id.cardDriverDetails);

        tvDriverName =
                findViewById(R.id.tvDriverName);

        tvVehicleDetails =
                findViewById(R.id.tvVehicleDetails);

        tvDriverEta =
                findViewById(R.id.tvDriverEta);

        tvDriverPhone =
                findViewById(R.id.tvDriverPhone);

        btnCallDriver =
                findViewById(R.id.btnCallDriver);

        cardCustomerDetails =
                findViewById(R.id.cardCustomerDetails);

        tvCustomerName =
                findViewById(R.id.tvCustomerName);

        tvCustomerPhone =
                findViewById(R.id.tvCustomerPhone);

        btnCallCustomer =
                findViewById(R.id.btnCallCustomer);

        if (btnCallDriver != null) {
            btnCallDriver.setOnClickListener(v -> {
                if (driverPhone != null && !driverPhone.isEmpty()) {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + driverPhone));
                    startActivity(callIntent);
                } else {
                    Toast.makeText(RideMapActivity.this, "Driver contact unavailable", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnCallCustomer != null) {
            btnCallCustomer.setOnClickListener(v -> {
                if (customerPhone != null && !customerPhone.isEmpty()) {
                    Intent callIntent = new Intent(Intent.ACTION_DIAL, android.net.Uri.parse("tel:" + customerPhone));
                    startActivity(callIntent);
                } else {
                    Toast.makeText(RideMapActivity.this, "Customer contact unavailable", Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnUseCurrentLocation =
                findViewById(R.id.btnUseCurrentLocation);

        btnSelectDestination =
                findViewById(R.id.btnSelectDestination);

        btnConfirmLocations =
                findViewById(R.id.btnConfirmLocations);

        btnClearLocations =
                findViewById(R.id.btnClearLocations);

        fabMyLocation =
                findViewById(R.id.fabMyLocation);

        locationSearchService =
                new LocationSearchService();

        routeService =
                new RouteService();

        driverMatchingService =
                new DriverMatchingService();

        // --------------------------------------------------------
        // MAP LIFECYCLE
        // --------------------------------------------------------

        mapView.onCreate(savedInstanceState);

        mapView.getMapAsync(map -> {

            mapLibreMap = map;

            mapLibreMap.setStyle(
                    new Style.Builder()
                            .fromJson(OSM_STYLE_JSON),
                    style -> {

                        loadedStyle = style;

                        CameraPosition initialPosition =
                                new CameraPosition.Builder()
                                        .target(
                                                new LatLng(
                                                        PUNE_LAT,
                                                        PUNE_LNG
                                                )
                                        )
                                        .zoom(12.0)
                                        .build();

                        mapLibreMap.setCameraPosition(
                                initialPosition
                        );

                        enableLocationComponentIfPermitted();

                        setupMapClickListener();

                        // Start live driver listener
                        startDriverLocationListener();

                        // Process pre-filled locations from Customer Home
                        processIncomingIntentLocations();
                    }
            );
        });

        // --------------------------------------------------------
        // SEARCH
        // --------------------------------------------------------

        setupPickupSearch();

        setupDestinationSearch();

        // --------------------------------------------------------
        // BUTTONS
        // --------------------------------------------------------

        btnUseCurrentLocation.setOnClickListener(
                v -> useCurrentLocationAsPickup()
        );

        btnSelectDestination.setOnClickListener(v -> {

            if (pickupLocation == null) {

                Toast.makeText(
                        RideMapActivity.this,
                        "Select pickup location first",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            selectingDestination = true;

            tvSelectionTitle.setText(
                    "Tap on map to select destination"
            );

            Toast.makeText(
                    RideMapActivity.this,
                    "Tap anywhere on the map for destination",
                    Toast.LENGTH_SHORT
            ).show();
        });

        btnConfirmLocations.setOnClickListener(
                v -> confirmLocations()
        );

        btnClearLocations.setOnClickListener(
                v -> clearLocations()
        );

        fabMyLocation.setOnClickListener(
                v -> moveToCurrentLocationOrFallback()
        );
    }

    // ============================================================
    // LIVE DRIVER LOCATION LISTENER
    // ============================================================

    private void startDriverLocationListener() {

        if (db == null) {
            return;
        }

        if (driverLocationListener != null) {
            driverLocationListener.remove();
        }

        driverLocationListener =
                db.collection("driverLocations")
                        .whereEqualTo("isOnline", true)
                        .addSnapshotListener(
                                (snapshots, error) -> {

                                    if (error != null || snapshots == null) {
                                        return;
                                    }

                                    for (DocumentSnapshot document
                                            : snapshots.getDocuments()) {

                                        updateDriverMarker(
                                                document
                                        );
                                    }

                                    removeDriversNoLongerOnline(
                                            snapshots
                                    );
                                }
                        );
    }

    // ============================================================
    // UPDATE DRIVER MARKER
    // ============================================================

    private void updateDriverMarker(
            DocumentSnapshot document
    ) {

        if (mapLibreMap == null) {
            return;
        }

        String driverId = document.getId();

        Double latitude = document.getDouble("latitude");
        Double longitude = document.getDouble("longitude");
        Boolean isOnline = document.getBoolean("isOnline");

        if (latitude == null || longitude == null) {
            return;
        }

        if (isOnline == null || !isOnline) {
            removeDriverMarker(driverId);
            return;
        }

        LatLng driverLocation =
                new LatLng(
                        latitude,
                        longitude
                );

        Marker existingMarker =
                driverMarkers.get(driverId);

        if (existingMarker == null) {

            MarkerOptions markerOptions =
                    new MarkerOptions()
                            .position(driverLocation)
                            .title("🚕 VeloCity Cab")
                            .snippet("Online Driver Nearby");

            Icon icon = getCabIcon();
            if (icon != null) {
                markerOptions.icon(icon);
            }

            Marker marker =
                    mapLibreMap.addMarker(markerOptions);

            driverMarkers.put(
                    driverId,
                    marker
            );

        } else {

            existingMarker.setPosition(
                    driverLocation
            );
        }
    }

    // ============================================================
    // REMOVE OFFLINE / MISSING DRIVER MARKERS
    // ============================================================

    private void removeDriversNoLongerOnline(
            com.google.firebase.firestore.QuerySnapshot snapshots
    ) {

        List<String> activeDriverIds =
                new ArrayList<>();

        for (DocumentSnapshot document :
                snapshots.getDocuments()) {

            activeDriverIds.add(
                    document.getId()
            );
        }

        List<String> driversToRemove =
                new ArrayList<>();

        for (String driverId :
                driverMarkers.keySet()) {

            if (!activeDriverIds.contains(
                    driverId
            )) {

                driversToRemove.add(
                        driverId
                );
            }
        }

        for (String driverId :
                driversToRemove) {

            removeDriverMarker(
                    driverId
            );
        }
    }

    // ============================================================
    // REMOVE ONE DRIVER MARKER
    // ============================================================

    private void removeDriverMarker(
            String driverId
    ) {

        Marker marker =
                driverMarkers.remove(
                        driverId
                );

        if (marker != null) {
            marker.remove();
        }
    }

    // ============================================================
    // PICKUP SEARCH
    // ============================================================

    private void setupPickupSearch() {

        etPickupSearch.addTextChangedListener(
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
    // DESTINATION SEARCH
    // ============================================================

    private void setupDestinationSearch() {

        etDestinationSearch.addTextChangedListener(
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
    // PICKUP SEARCH RESULTS
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

        lvPickupResults.setAdapter(
                adapter
        );

        lvPickupResults.setVisibility(
                View.VISIBLE
        );

        lvPickupResults.setOnItemClickListener(
                (parent, view, position, id) -> {

                    LocationSearchService.LocationResult selected =
                            results.get(position);

                    selectPickupFromSearch(
                            selected
                    );
                }
        );
    }

    // ============================================================
    // DESTINATION SEARCH RESULTS
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

        lvDestinationResults.setAdapter(
                adapter
        );

        lvDestinationResults.setVisibility(
                View.VISIBLE
        );

        lvDestinationResults.setOnItemClickListener(
                (parent, view, position, id) -> {

                    LocationSearchService.LocationResult selected =
                            results.get(position);

                    selectDestinationFromSearch(
                            selected
                    );
                }
        );
    }

    // ============================================================
    // SELECT PICKUP FROM SEARCH
    // ============================================================

    private void selectPickupFromSearch(
            LocationSearchService.LocationResult result
    ) {

        pickupLocation =
                new LatLng(
                        result.getLatitude(),
                        result.getLongitude()
                );

        pickupName =
                result.getName();

        pickupAddress =
                result.getAddress();

        if (pickupMarker != null) {
            pickupMarker.remove();
        }

        pickupMarker =
                mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(
                                        pickupLocation
                                )
                                .title(
                                        "Pickup: "
                                                + pickupName
                                )
                                .snippet(
                                        pickupAddress
                                )
                );

        etPickupSearch.setText(
                result.getDisplayName()
        );

        lvPickupResults.setVisibility(
                View.GONE
        );

        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        pickupLocation,
                        15.0
                )
        );

        tvPickup.setText(
                "📍 Pickup: "
                        + result.getDisplayName()
        );

        selectingDestination = false;

        locationsConfirmed = false;

        tvSelectionTitle.setText(
                "Pickup selected. Select destination."
        );

        Toast.makeText(
                this,
                "Pickup selected",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ============================================================
    // SELECT DESTINATION FROM SEARCH
    // ============================================================

    private void selectDestinationFromSearch(
            LocationSearchService.LocationResult result
    ) {

        destinationLocation =
                new LatLng(
                        result.getLatitude(),
                        result.getLongitude()
                );

        destinationName =
                result.getName();

        destinationAddress =
                result.getAddress();

        if (destinationMarker != null) {
            destinationMarker.remove();
        }

        destinationMarker =
                mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(
                                        destinationLocation
                                )
                                .title(
                                        "Destination: "
                                                + destinationName
                                )
                                .snippet(
                                        destinationAddress
                                )
                );

        etDestinationSearch.setText(
                result.getDisplayName()
        );

        lvDestinationResults.setVisibility(
                View.GONE
        );

        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        destinationLocation,
                        15.0
                )
        );

        tvDestination.setText(
                "🔴 Destination: "
                        + result.getDisplayName()
        );

        selectingDestination = false;

        locationsConfirmed = false;

        tvSelectionTitle.setText(
                "Pickup and destination selected"
        );

        Toast.makeText(
                this,
                "Destination selected",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ============================================================
    // MAP TAP LISTENER
    // ============================================================

    private void setupMapClickListener() {

        if (mapLibreMap == null) {
            return;
        }

        mapLibreMap.addOnMapClickListener(
                point -> {

                    if (!selectingDestination) {

                        setPickupLocation(
                                point
                        );

                    } else {

                        setDestinationLocation(
                                point
                        );
                    }

                    return true;
                }
        );
    }

    // ============================================================
    // PICKUP FROM MAP
    // ============================================================

    private void setPickupLocation(
            LatLng point
    ) {

        pickupLocation = point;

        pickupName =
                "Selected map location";

        pickupAddress =
                formatCoordinates(point);

        if (pickupMarker != null) {
            pickupMarker.remove();
        }

        pickupMarker =
                mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(point)
                                .title("Pickup Location")
                                .snippet(
                                        formatCoordinates(point)
                                )
                );

        tvPickup.setText(
                "📍 Pickup: "
                        + formatCoordinates(point)
        );

        etPickupSearch.setText(
                formatCoordinates(point)
        );

        selectingDestination = false;

        locationsConfirmed = false;

        tvSelectionTitle.setText(
                "Pickup selected. Select destination."
        );
    }

    // ============================================================
    // DESTINATION FROM MAP
    // ============================================================

    private void setDestinationLocation(
            LatLng point
    ) {

        destinationLocation = point;

        destinationName =
                "Selected map location";

        destinationAddress =
                formatCoordinates(point);

        if (destinationMarker != null) {
            destinationMarker.remove();
        }

        destinationMarker =
                mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(point)
                                .title("Destination")
                                .snippet(
                                        formatCoordinates(point)
                                )
                );

        tvDestination.setText(
                "🔴 Destination: "
                        + formatCoordinates(point)
        );

        etDestinationSearch.setText(
                formatCoordinates(point)
        );

        selectingDestination = false;

        locationsConfirmed = false;

        tvSelectionTitle.setText(
                "Pickup and destination selected"
        );
    }

    // ============================================================
    // CURRENT LOCATION AS PICKUP
    // ============================================================

    private void useCurrentLocationAsPickup() {

        if (mapLibreMap == null) {
            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );

            return;
        }

        LocationComponent locationComponent =
                mapLibreMap.getLocationComponent();

        if (!locationComponent
                .isLocationComponentActivated()) {

            Toast.makeText(
                    this,
                    "Location service is still starting. You can search for pickup instead.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        Location location =
                locationComponent
                        .getLastKnownLocation();

        if (location == null) {

            Toast.makeText(
                    this,
                    "Current location is not available. Search for your pickup instead.",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        LatLng currentLocation =
                new LatLng(
                        location.getLatitude(),
                        location.getLongitude()
                );

        pickupLocation =
                currentLocation;

        pickupName =
                "Current Location";

        pickupAddress =
                formatCoordinates(
                        currentLocation
                );

        if (pickupMarker != null) {
            pickupMarker.remove();
        }

        pickupMarker =
                mapLibreMap.addMarker(
                        new MarkerOptions()
                                .position(
                                        currentLocation
                                )
                                .title(
                                        "Current Location"
                                )
                );

        tvPickup.setText(
                "📍 Pickup: Current Location"
        );

        etPickupSearch.setText(
                "Current Location"
        );

        locationsConfirmed = false;

        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        currentLocation,
                        15.0
                )
        );

        Toast.makeText(
                this,
                "Current location selected as pickup",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ============================================================
    // CONFIRM LOCATIONS
    // ============================================================

    private void confirmLocations() {

        if (pickupLocation == null) {

            Toast.makeText(
                    this,
                    "Please select pickup location",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (destinationLocation == null) {

            Toast.makeText(
                    this,
                    "Please select destination",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        if (auth == null ||
                auth.getCurrentUser() == null) {

            Toast.makeText(
                    this,
                    "Please login again before requesting a ride",
                    Toast.LENGTH_LONG
            ).show();

            return;
        }

        // Prevent accidental duplicate ride creation
        if (!currentRideId.isEmpty()) {

            Toast.makeText(
                    this,
                    "Ride request already created",
                    Toast.LENGTH_SHORT
            ).show();

            return;
        }

        locationsConfirmed = true;

        tvSelectionTitle.setText(
                "⏳ Calculating route..."
        );

        tvRouteInfo.setText(
                "Route: Calculating...\n"
                        + "Estimated Fare: Calculating...\n"
                        + "Estimated Duration: Calculating..."
        );

        Toast.makeText(
                this,
                "Calculating route and fare...",
                Toast.LENGTH_SHORT
        ).show();

        calculateRouteAndCreateRide();
    }

    // ============================================================
    // CALCULATE ROAD ROUTE + FARE
    // ============================================================

    private void calculateRouteAndCreateRide() {

        if (routeService == null) {
            routeService = new RouteService();
        }

        routeService.getRoute(
                pickupLocation.getLatitude(),
                pickupLocation.getLongitude(),
                destinationLocation.getLatitude(),
                destinationLocation.getLongitude(),
                new RouteService.RouteCallback() {

                    @Override
                    public void onSuccess(
                            RouteService.RouteResult routeResult
                    ) {

                        double distanceKm =
                                routeResult.getDistanceMeters() / 1000.0;

                        long estimatedFare =
                                FareCalculator.calculateEstimatedFare(
                                        distanceKm
                                );

                        long estimatedDurationMinutes =
                                Math.max(
                                        1,
                                        Math.round(
                                                routeResult.getDurationSeconds()
                                                        / 60.0
                                        )
                                );

                        tvRouteInfo.setText(
                                String.format(
                                        Locale.US,
                                        "🛣 Distance: %.2f km\n"
                                                + "💰 Estimated Fare: ₹%d\n"
                                                + "⏱ Estimated Duration: %d min",
                                        distanceKm,
                                        estimatedFare,
                                        estimatedDurationMinutes
                                )
                        );

                        tvSelectionTitle.setText(
                                "✓ Route calculated"
                        );

                        showNearbyDriversOnMap();

                        createRideRequest(
                                routeResult,
                                estimatedFare,
                                estimatedDurationMinutes
                        );
                    }

                    @Override
                    public void onError(
                            String message
                    ) {

                        locationsConfirmed = false;

                        tvSelectionTitle.setText(
                                "Pickup and destination selected"
                        );

                        tvRouteInfo.setText(
                                "Route: Unable to calculate"
                        );

                        Toast.makeText(
                                RideMapActivity.this,
                                "ROUTE CALCULATION FAILED:\n"
                                        + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // ============================================================
    // CREATE CUSTOMER RIDE REQUEST
    // ============================================================

    private void createRideRequest(
            RouteService.RouteResult routeResult,
            long estimatedFare,
            long estimatedDurationMinutes
    ) {

        String customerId =
                auth.getCurrentUser().getUid();

        String pickupText =
                pickupName == null ||
                        pickupName.trim().isEmpty()
                        ? formatCoordinates(pickupLocation)
                        : pickupName;

        String destinationText =
                destinationName == null ||
                        destinationName.trim().isEmpty()
                        ? formatCoordinates(destinationLocation)
                        : destinationName;

        Map<String, Object> rideData =
                new HashMap<>();

        // Existing ride fields
        rideData.put(
                "customerId",
                customerId
        );

        rideData.put(
                "pickup",
                pickupText
        );

        rideData.put(
                "destination",
                destinationText
        );

        rideData.put(
                "status",
                "searching"
        );

        rideData.put(
                "driverId",
                ""
        );

        rideData.put(
                "createdAt",
                com.google.firebase.Timestamp.now()
        );

        // Structured pickup coordinates
        rideData.put(
                "pickupLatitude",
                pickupLocation.getLatitude()
        );

        rideData.put(
                "pickupLongitude",
                pickupLocation.getLongitude()
        );

        // Structured destination coordinates
        rideData.put(
                "destinationLatitude",
                destinationLocation.getLatitude()
        );

        rideData.put(
                "destinationLongitude",
                destinationLocation.getLongitude()
        );

        // Keep addresses for later display
        rideData.put(
                "pickupAddress",
                pickupAddress == null
                        ? ""
                        : pickupAddress
        );

        rideData.put(
                "destinationAddress",
                destinationAddress == null
                        ? ""
                        : destinationAddress
        );

        // Route and fare information
        double distanceKm =
                routeResult.getDistanceMeters() / 1000.0;

        rideData.put(
                "distanceKm",
                distanceKm
        );

        rideData.put(
                "estimatedFare",
                estimatedFare
        );

        rideData.put(
                "estimatedDurationMinutes",
                estimatedDurationMinutes
        );

        rideData.put(
                "routeDurationSeconds",
                routeResult.getDurationSeconds()
        );

        // Stored now so route geometry can be drawn later.
        rideData.put(
                "routeGeometry",
                routeResult.getGeometryJson()
        );

        // Matching state foundation
        rideData.put(
                "matchingStage",
                "initial"
        );

        rideData.put(
                "matchedDriverIds",
                new ArrayList<String>()
        );

        db.collection("users").document(customerId).get()
                .addOnSuccessListener(userDoc -> {
                    String customerName = "Customer";
                    String customerPhoneStr = "+91 98765 43210";
                    if (userDoc != null && userDoc.exists()) {
                        String name = userDoc.getString("name");
                        String phone = userDoc.getString("phone");
                        if (name != null && !name.isEmpty()) customerName = name;
                        if (phone != null && !phone.isEmpty()) customerPhoneStr = phone;
                    }

                    rideData.put("customerName", customerName);
                    rideData.put("customerPhone", customerPhoneStr);

                    db.collection("rides")
                            .add(rideData)
                            .addOnSuccessListener(
                                    documentReference -> {

                                        currentRideId =
                                                documentReference.getId();

                                        listenToRideStatusUpdates();

                                        tvSelectionTitle.setText(
                                                "✓ Ride request created"
                                        );

                                        Toast.makeText(
                                                RideMapActivity.this,
                                                String.format(
                                                        Locale.US,
                                                        "RIDE REQUEST CREATED ✓\n"
                                                                + "Distance: %.2f km\n"
                                                                + "Estimated Fare: ₹%d\n"
                                                                + "Duration: %d min\n"
                                                                + "Ride ID: ",
                                                        routeResult.getDistanceMeters() / 1000.0,
                                                        estimatedFare,
                                                        estimatedDurationMinutes
                                                )
                                                        + currentRideId.substring(
                                                        0,
                                                        Math.min(
                                                                8,
                                                                currentRideId.length()
                                                        )
                                                ),
                                                Toast.LENGTH_LONG
                                        ).show();

                                        // Schedule 2-minute expiration if no driver accepts
                                        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                            if (currentRideId != null && !currentRideId.isEmpty() && db != null) {
                                                db.collection("rides").document(currentRideId).get()
                                                        .addOnSuccessListener(snap -> {
                                                            if (snap != null && snap.exists() && "searching".equals(snap.getString("status"))) {
                                                                Map<String, Object> exp = new HashMap<>();
                                                                exp.put("status", "expired");
                                                                db.collection("rides").document(currentRideId)
                                                                        .set(exp, com.google.firebase.firestore.SetOptions.merge());
                                                            }
                                                        });
                                            }
                                        }, 120000);

                                        // Stage 3: discover currently online drivers
                                        // and sort them by distance from pickup.
                                        findOnlineDriversForRide(
                                                currentRideId
                                        );
                                    }
                            )
                            .addOnFailureListener(
                                    e -> {

                                        locationsConfirmed = false;

                                        tvSelectionTitle.setText(
                                                "Pickup and destination selected"
                                        );

                                        Toast.makeText(
                                                RideMapActivity.this,
                                                "RIDE REQUEST FAILED:\n"
                                                        + e.getMessage(),
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                            );
                });
    }

    // ============================================================
    // STAGE 3 - FIND ONLINE DRIVERS
    // ============================================================

    private void findOnlineDriversForRide(String rideId) {

        if (driverMatchingService == null) {
            driverMatchingService =
                    new DriverMatchingService();
        }

        if (pickupLocation == null) {
            return;
        }

        tvSelectionTitle.setText(
                "🔎 Finding nearby matching drivers..."
        );

        double estFare = 150.0;
        double tripDist = 5.0;
        if (pickupLocation != null && destinationLocation != null) {
            tripDist = calculateDistanceKm(pickupLocation.getLatitude(), pickupLocation.getLongitude(), destinationLocation.getLatitude(), destinationLocation.getLongitude());
            estFare = FareCalculator.calculateEstimatedFare(tripDist);
        }

        driverMatchingService.findOnlineDriversNearPickup(
                pickupLocation.getLatitude(),
                pickupLocation.getLongitude(),
                estFare,
                tripDist,
                new DriverMatchingService.MatchingCallback() {

                    @Override
                    public void onSuccess(
                            List<DriverMatchingService.DriverCandidate> candidates
                    ) {

                        if (candidates.isEmpty()) {

                            tvSelectionTitle.setText(
                                    "⏳ No online drivers found"
                            );

                            Toast.makeText(
                                    RideMapActivity.this,
                                    "No online drivers found",
                                    Toast.LENGTH_LONG
                            ).show();

                            return;
                        }

                        DriverMatchingService.DriverCandidate nearest =
                                candidates.get(0);

                        List<String> candidateDriverIds =
                                new ArrayList<>();

                        for (DriverMatchingService.DriverCandidate candidate
                                : candidates) {

                            candidateDriverIds.add(
                                    candidate.getDriverId()
                            );
                        }

                        Map<String, Object> matchingUpdate =
                                new HashMap<>();

                        matchingUpdate.put(
                                "matchingStage",
                                "nearby_candidates"
                        );

                        matchingUpdate.put(
                                "candidateDriverIds",
                                candidateDriverIds
                        );

                        matchingUpdate.put(
                                "nearestDriverId",
                                nearest.getDriverId()
                        );

                        matchingUpdate.put(
                                "nearestPickupDistanceKm",
                                nearest.getPickupDistanceKm()
                        );

                        db.collection("rides")
                                .document(rideId)
                                .update(matchingUpdate)
                                .addOnSuccessListener(ignored -> {

                                    tvSelectionTitle.setText(
                                            "✓ Online driver found"
                                    );

                                    Toast.makeText(
                                            RideMapActivity.this,
                                            String.format(
                                                    Locale.US,
                                                    "ONLINE DRIVERS FOUND ✓\n"
                                                            + "Count: %d\n"
                                                            + "Nearest pickup distance: %.2f km",
                                                    candidates.size(),
                                                    nearest.getPickupDistanceKm()
                                            ),
                                            Toast.LENGTH_LONG
                                    ).show();
                                })
                                .addOnFailureListener(e -> {

                                    Toast.makeText(
                                            RideMapActivity.this,
                                            "Driver found, but ride update failed:\n"
                                                    + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });
                    }

                    @Override
                    public void onError(String message) {

                        tvSelectionTitle.setText(
                                "✓ Ride request created"
                        );

                        Toast.makeText(
                                RideMapActivity.this,
                                "DRIVER MATCHING FAILED:\n"
                                        + message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                }
        );
    }

    // ============================================================
    // CLEAR LOCATIONS
    // ============================================================

    private void clearLocations() {

        if (pickupMarker != null) {

            pickupMarker.remove();

            pickupMarker = null;
        }

        if (destinationMarker != null) {

            destinationMarker.remove();

            destinationMarker = null;
        }

        pickupLocation = null;

        destinationLocation = null;

        currentRideId = "";

        pickupName = "";

        pickupAddress = "";

        destinationName = "";

        destinationAddress = "";

        selectingDestination = false;

        locationsConfirmed = false;

        etPickupSearch.setText("");

        etDestinationSearch.setText("");

        lvPickupResults.setVisibility(
                View.GONE
        );

        lvDestinationResults.setVisibility(
                View.GONE
        );

        tvPickup.setText(
                "📍 Pickup: Not selected"
        );

        tvDestination.setText(
                "🔴 Destination: Not selected"
        );

        tvRouteInfo.setText(
                "Route: Not calculated"
        );

        tvSelectionTitle.setText(
                "Select pickup and destination"
        );

        Toast.makeText(
                this,
                "Locations cleared",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ============================================================
    // ENABLE LOCATION COMPONENT
    // ============================================================

    @SuppressWarnings("MissingPermission")
    private void enableLocationComponentIfPermitted() {

        if (mapLibreMap == null ||
                loadedStyle == null) {

            return;
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED) {

            try {

                LocationComponent locationComponent =
                        mapLibreMap
                                .getLocationComponent();

                LocationComponentActivationOptions
                        activationOptions =
                        LocationComponentActivationOptions
                                .builder(
                                        this,
                                        loadedStyle
                                )
                                .useDefaultLocationEngine(true)
                                .build();

                locationComponent
                        .activateLocationComponent(
                                activationOptions
                        );

                locationComponent
                        .setLocationComponentEnabled(
                                true
                        );

                locationComponent.setCameraMode(
                        CameraMode.NONE
                );

                locationComponent.setRenderMode(
                        RenderMode.COMPASS
                );

            } catch (Exception e) {

                Toast.makeText(
                        this,
                        "Could not initialize location service",
                        Toast.LENGTH_SHORT
                ).show();
            }

        } else {

            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    // ============================================================
    // MOVE TO CURRENT LOCATION / FALLBACK
    // ============================================================

    private void moveToCurrentLocationOrFallback() {

        if (mapLibreMap == null) {
            return;
        }

        boolean hasPermission =
                ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED;

        if (hasPermission &&
                mapLibreMap
                        .getLocationComponent()
                        .isLocationComponentActivated()) {

            Location location =
                    mapLibreMap
                            .getLocationComponent()
                            .getLastKnownLocation();

            if (location != null) {

                LatLng currentLatLng =
                        new LatLng(
                                location.getLatitude(),
                                location.getLongitude()
                        );

                mapLibreMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                currentLatLng,
                                15.0
                        )
                );

                Toast.makeText(
                        this,
                        "Showing your current location",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }
        }

        LatLng fallback =
                new LatLng(
                        PUNE_LAT,
                        PUNE_LNG
                );

        mapLibreMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(
                        fallback,
                        12.0
                )
        );

        Toast.makeText(
                this,
                "GPS unavailable. Showing Pune.",
                Toast.LENGTH_SHORT
        ).show();
    }

    // ============================================================
    // FORMAT COORDINATES
    // ============================================================

    private String formatCoordinates(
            LatLng point
    ) {

        return String.format(
                Locale.US,
                "%.5f, %.5f",
                point.getLatitude(),
                point.getLongitude()
        );
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

            if (grantResults.length > 0 &&
                    grantResults[0] ==
                            PackageManager.PERMISSION_GRANTED) {

                enableLocationComponentIfPermitted();

            } else {

                Toast.makeText(
                        this,
                        "GPS permission denied. You can still search locations manually.",
                        Toast.LENGTH_LONG
                ).show();
            }
        }
    }

    // ============================================================
    // MAP LIFECYCLE
    // ============================================================

    @Override
    protected void onStart() {

        super.onStart();

        if (mapView != null) {
            mapView.onStart();
        }
    }

    @Override
    protected void onResume() {

        super.onResume();

        if (mapView != null) {
            mapView.onResume();
        }
    }

    // ============================================================
    // DISPLAY NEARBY CABS ON MAP AROUND PICKUP
    // ============================================================

    private void showNearbyDriversOnMap() {

        if (pickupLocation == null || mapLibreMap == null) {
            return;
        }

        // Render nearby cab markers if no online driver markers are present
        if (driverMarkers.isEmpty()) {

            double pLat = pickupLocation.getLatitude();
            double pLng = pickupLocation.getLongitude();

            double[][] offsets = {
                    {0.0035, 0.0042},
                    {-0.0048, 0.0031},
                    {0.0022, -0.0055}
            };

            for (int i = 0; i < offsets.length; i++) {

                String simId = "sim_cab_" + i;
                LatLng cabPos = new LatLng(pLat + offsets[i][0], pLng + offsets[i][1]);

                MarkerOptions markerOptions =
                        new MarkerOptions()
                                .position(cabPos)
                                .title("🚕 VeloCity Cab #" + (i + 1))
                                .snippet("Available nearby");

                Icon icon = getCabIcon();
                if (icon != null) {
                    markerOptions.icon(icon);
                }

                Marker marker = mapLibreMap.addMarker(markerOptions);
                driverMarkers.put(simId, marker);
            }
        }

        // Adjust camera bounds to enclose pickup, destination, and nearby cabs
        try {
            LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

            if (pickupLocation != null) {
                boundsBuilder.include(pickupLocation);
            }

            if (destinationLocation != null) {
                boundsBuilder.include(destinationLocation);
            }

            for (Marker m : driverMarkers.values()) {
                if (m != null) {
                    boundsBuilder.include(m.getPosition());
                }
            }

            mapLibreMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 120)
            );

        } catch (Exception ignored) {
        }
    }

    // ============================================================
    // LISTEN TO RIDE STATUS & ACCEPTED DRIVER
    // ============================================================

    private boolean ratingDialogShown = false;

    private void listenToRideStatusUpdates() {

        if (currentRideId == null || currentRideId.isEmpty() || db == null) {
            return;
        }

        if (currentRideListener != null) {
            currentRideListener.remove();
        }

        currentRideListener = db.collection("rides").document(currentRideId)
                .addSnapshotListener((snapshot, error) -> {

                    if (error != null || snapshot == null || !snapshot.exists()) {
                        return;
                    }

                    String status = snapshot.getString("status");
                    String dName = snapshot.getString("driverName");
                    String vDetails = snapshot.getString("vehicleDetails");
                    String dId = snapshot.getString("driverId");
                    String dPhone = snapshot.getString("driverPhone");

                    if (dPhone != null && !dPhone.isEmpty()) {
                        driverPhone = dPhone;
                    }

                    if (tvDriverPhone != null) {
                        tvDriverPhone.setText("📞 Phone: " + driverPhone);
                    }

                    if ("accepted".equals(status)) {

                        if (cardDriverDetails != null) {
                            cardDriverDetails.setVisibility(View.VISIBLE);
                        }

                        if (tvDriverName != null && dName != null) {
                            tvDriverName.setText("👤 Driver: " + dName + " (4.9 ★)");
                        }

                        if (tvVehicleDetails != null && vDetails != null) {
                            tvVehicleDetails.setText("🚕 Vehicle: " + vDetails);
                        }

                        if (tvSelectionTitle != null) {
                            tvSelectionTitle.setText("🚕 Driver En Route to Pickup Point...");
                        }

                        if (dId != null && !dId.isEmpty()) {
                            trackAcceptedDriverLiveLocation(dId, "pickup");
                        }

                    } else if ("arrived".equals(status)) {

                        if (cardDriverDetails != null) {
                            cardDriverDetails.setVisibility(View.VISIBLE);
                        }

                        if (tvSelectionTitle != null) {
                            tvSelectionTitle.setText("📍 Driver Has Arrived at Pickup Point!");
                        }

                        if (tvDriverEta != null) {
                            tvDriverEta.setText("📍 Driver is waiting at your pickup location");
                        }

                        Toast.makeText(RideMapActivity.this, "📍 Driver has arrived at your pickup location!", Toast.LENGTH_SHORT).show();

                        if (dId != null && !dId.isEmpty()) {
                            trackAcceptedDriverLiveLocation(dId, "pickup");
                        }

                    } else if ("in_progress".equals(status)) {

                        if (cardDriverDetails != null) {
                            cardDriverDetails.setVisibility(View.VISIBLE);
                        }

                        if (tvSelectionTitle != null) {
                            tvSelectionTitle.setText("🛣️ Trip in Progress - Heading to Destination!");
                        }

                        if (tvDriverEta != null) {
                            tvDriverEta.setText("⏱ On the way to destination...");
                        }

                        Toast.makeText(RideMapActivity.this, "🛣️ Trip started! Heading to destination.", Toast.LENGTH_SHORT).show();

                        if (dId != null && !dId.isEmpty()) {
                            trackAcceptedDriverLiveLocation(dId, "destination");
                        }

                    } else if ("completed".equals(status)) {

                        if (cardDriverDetails != null) {
                            cardDriverDetails.setVisibility(View.GONE);
                        }

                        if (tvSelectionTitle != null) {
                            tvSelectionTitle.setText("🎉 Trip Completed! Thank you for riding with VeloCity!");
                        }

                        if (!isDriverMode && !ratingDialogShown) {
                            ratingDialogShown = true;
                            showCustomerRatingDialog(dId);
                        }

                    } else if ("expired".equals(status)) {

                        if (cardDriverDetails != null) {
                            cardDriverDetails.setVisibility(View.GONE);
                        }

                        if (tvSelectionTitle != null) {
                            tvSelectionTitle.setText("⏰ Ride request expired (No drivers accepted)");
                        }

                        Toast.makeText(RideMapActivity.this, "⏰ Ride request timed out after 2 minutes. Please request again.", Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void trackAcceptedDriverLiveLocation(String driverId, String targetType) {

        if (db == null || driverId == null) {
            return;
        }

        if (acceptedDriverLocationListener != null) {
            acceptedDriverLocationListener.remove();
        }

        acceptedDriverLocationListener = db.collection("driverLocations").document(driverId)
                .addSnapshotListener((document, error) -> {

                    if (error != null || document == null || !document.exists()) {
                        return;
                    }

                    Double lat = document.getDouble("latitude");
                    Double lng = document.getDouble("longitude");

                    if (lat != null && lng != null && mapLibreMap != null) {

                        LatLng driverPos = new LatLng(lat, lng);
                        Marker existingMarker = driverMarkers.get(driverId);

                        if (existingMarker == null) {

                            MarkerOptions markerOptions =
                                    new MarkerOptions()
                                            .position(driverPos)
                                            .title("🚕 Live VeloCity Driver")
                                            .snippet("Live Tracking");

                            Icon icon = getCabIcon();
                            if (icon != null) {
                                markerOptions.icon(icon);
                            }

                            Marker newMarker = mapLibreMap.addMarker(markerOptions);
                            driverMarkers.put(driverId, newMarker);

                        } else {
                            existingMarker.setPosition(driverPos);
                        }

                        LatLng targetPoint = "destination".equals(targetType) && destinationLocation != null
                                ? destinationLocation
                                : pickupLocation;

                        if (targetPoint != null) {

                            double distanceKm = calculateDistanceKm(
                                    lat, lng,
                                    targetPoint.getLatitude(), targetPoint.getLongitude()
                            );

                            int etaMins = Math.max(1, (int) Math.round((distanceKm / 30.0) * 60.0));

                            if (tvDriverEta != null) {
                                String label = "destination".equals(targetType) ? "Reaching Destination" : "Driver Arrival";
                                tvDriverEta.setText(
                                        String.format(
                                                Locale.US,
                                                "⏱ %s: ~%d mins (%.1f km away)",
                                                label,
                                                etaMins,
                                                distanceKm
                                        )
                                );
                            }
                        }
                    }
                });
    }

    private void showCustomerRatingDialog(String driverId) {
        if (isFinishing() || isDestroyed()) return;

        android.widget.RatingBar ratingBar = new android.widget.RatingBar(this);
        ratingBar.setNumStars(5);
        ratingBar.setRating(5.0f);
        ratingBar.setStepSize(1.0f);

        android.widget.LinearLayout container = new android.widget.LinearLayout(this);
        container.setOrientation(android.widget.LinearLayout.VERTICAL);
        container.setPadding(60, 40, 60, 20);
        container.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        container.addView(ratingBar);

        EditText etReview = new EditText(this);
        etReview.setHint("Leave a review for your driver (optional)...");
        etReview.setPadding(20, 20, 20, 20);
        container.addView(etReview);

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("⭐ Rate Your VeloCity Trip")
                .setMessage("How was your trip with your driver?")
                .setView(container)
                .setCancelable(false)
                .setPositiveButton("SUBMIT RATING ✓", (dialog, which) -> {
                    float rating = ratingBar.getRating();
                    String review = etReview.getText().toString().trim();

                    if (currentRideId != null && !currentRideId.isEmpty()) {
                        Map<String, Object> update = new HashMap<>();
                        update.put("rating", (double) rating);
                        update.put("review", review);
                        update.put("ratedAt", com.google.firebase.Timestamp.now());

                        db.collection("rides").document(currentRideId)
                                .set(update, com.google.firebase.firestore.SetOptions.merge());
                    }

                    Toast.makeText(RideMapActivity.this, "Thank you for rating your driver! ⭐", Toast.LENGTH_LONG).show();
                })
                .show();
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

    @Override
    protected void onPause() {

        if (mapView != null) {
            mapView.onPause();
        }

        super.onPause();
    }

    @Override
    protected void onStop() {

        if (mapView != null) {
            mapView.onStop();
        }

        super.onStop();
    }

    private boolean isDriverMode = false;
    private String currentRideStatus = "accepted";
    private android.os.Handler simulationHandler;
    private Runnable simulationRunnable;

    private void setupDriverNavigationUi() {
        if (etPickupSearch != null) etPickupSearch.setVisibility(View.GONE);
        if (etDestinationSearch != null) etDestinationSearch.setVisibility(View.GONE);
        if (btnUseCurrentLocation != null) btnUseCurrentLocation.setVisibility(View.GONE);
        if (btnSelectDestination != null) btnSelectDestination.setVisibility(View.GONE);
        if (btnClearLocations != null) btnClearLocations.setVisibility(View.GONE);

        if (cardCustomerDetails != null) {
            cardCustomerDetails.setVisibility(View.VISIBLE);
        }

        if (currentRideId != null && !currentRideId.isEmpty() && db != null) {
            db.collection("rides").document(currentRideId)
                    .addSnapshotListener((doc, err) -> {
                        if (doc != null && doc.exists()) {
                            String status = doc.getString("status");
                            String cName = doc.getString("customerName");
                            String cPhone = doc.getString("customerPhone");

                            if (cName != null && !cName.isEmpty() && tvCustomerName != null) {
                                tvCustomerName.setText("👤 Customer: " + cName);
                            }
                            if (cPhone != null && !cPhone.isEmpty()) {
                                customerPhone = cPhone;
                            }
                            if (tvCustomerPhone != null) {
                                tvCustomerPhone.setText("📞 Phone: " + customerPhone);
                            }

                            if (status != null) {
                                currentRideStatus = status;
                                updateDriverStageButton(status);
                            }
                        }
                    });
        } else {
            updateDriverStageButton("accepted");
        }

        startDriverLiveLocationSimulation();
    }

    private void updateDriverStageButton(String status) {
        if (btnConfirmLocations == null) return;

        if ("accepted".equals(status)) {
            btnConfirmLocations.setText("📍 MARK ARRIVED AT PICKUP");
            btnConfirmLocations.setOnClickListener(v -> updateRideStatusByDriver("arrived"));
            if (tvSelectionTitle != null) tvSelectionTitle.setText("🚕 Navigating to Customer Pickup Point...");
        } else if ("arrived".equals(status)) {
            btnConfirmLocations.setText("▶️ START TRIP TO DESTINATION");
            btnConfirmLocations.setOnClickListener(v -> updateRideStatusByDriver("in_progress"));
            if (tvSelectionTitle != null) tvSelectionTitle.setText("📍 Arrived at Pickup! Waiting for Customer...");
        } else if ("in_progress".equals(status)) {
            btnConfirmLocations.setText("🏁 COMPLETE RIDE & COLLECT FARE");
            btnConfirmLocations.setOnClickListener(v -> updateRideStatusByDriver("completed"));
            if (tvSelectionTitle != null) tvSelectionTitle.setText("🛣️ Trip in Progress - Driving to Destination...");
        } else if ("completed".equals(status)) {
            Toast.makeText(this, "🎉 Trip Completed! Fare collected successfully ✓", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void updateRideStatusByDriver(String newStatus) {
        if (currentRideId == null || currentRideId.isEmpty() || db == null) {
            Toast.makeText(this, "Ride ID missing", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> update = new HashMap<>();
        update.put("status", newStatus);
        if ("completed".equals(newStatus)) {
            update.put("completedAt", com.google.firebase.Timestamp.now());
        }

        db.collection("rides").document(currentRideId)
                .set(update, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Status updated to: " + newStatus, Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to update status: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void startDriverLiveLocationSimulation() {
        if (auth == null || auth.getCurrentUser() == null || db == null) return;
        String driverId = auth.getCurrentUser().getUid();

        simulationHandler = new android.os.Handler(android.os.Looper.getMainLooper());
        final double[] currentLat = {pickupLocation != null ? pickupLocation.getLatitude() - 0.008 : PUNE_LAT};
        final double[] currentLng = {pickupLocation != null ? pickupLocation.getLongitude() - 0.008 : PUNE_LNG};

        simulationRunnable = new Runnable() {
            @Override
            public void run() {
                LatLng target = "in_progress".equals(currentRideStatus) && destinationLocation != null
                        ? destinationLocation
                        : pickupLocation;

                if (target != null) {
                    double dLat = (target.getLatitude() - currentLat[0]) * 0.15;
                    double dLng = (target.getLongitude() - currentLng[0]) * 0.15;

                    currentLat[0] += dLat;
                    currentLng[0] += dLng;

                    Map<String, Object> loc = new HashMap<>();
                    loc.put("driverId", driverId);
                    loc.put("latitude", currentLat[0]);
                    loc.put("longitude", currentLng[0]);
                    loc.put("isOnline", true);
                    loc.put("updatedAt", com.google.firebase.Timestamp.now());

                    db.collection("driverLocations").document(driverId)
                            .set(loc, com.google.firebase.firestore.SetOptions.merge());
                }

                if (simulationHandler != null) {
                    simulationHandler.postDelayed(this, 3000);
                }
            }
        };

        simulationHandler.post(simulationRunnable);
    }

    // ============================================================
    // PROCESS INCOMING INTENT LOCATIONS FROM CUSTOMER HOME
    // ============================================================

    private void processIncomingIntentLocations() {

        Intent intent = getIntent();

        if (intent == null) {
            return;
        }

        isDriverMode = intent.getBooleanExtra("isDriverMode", false);
        if (isDriverMode) {
            setupDriverNavigationUi();
        }

        if (intent.hasExtra("rideId")) {
            currentRideId = intent.getStringExtra("rideId");
            listenToRideStatusUpdates();
        }

        String pickupText = intent.getStringExtra("pickupText");
        String destinationText = intent.getStringExtra("destinationText");

        if (pickupText == null && destinationText == null) {
            return;
        }

        double pickupLat = intent.getDoubleExtra("pickupLat", 0.0);
        double pickupLng = intent.getDoubleExtra("pickupLng", 0.0);

        if (pickupLat != 0.0 && pickupLng != 0.0) {

            String name = intent.getStringExtra("pickupName");
            String address = intent.getStringExtra("pickupAddress");

            LocationSearchService.LocationResult pickupResult =
                    new LocationSearchService.LocationResult(
                            name != null ? name : pickupText,
                            address != null ? address : "",
                            pickupLat,
                            pickupLng
                    );

            selectPickupFromSearch(pickupResult);

        } else if (pickupText != null && !pickupText.isEmpty()) {

            etPickupSearch.setText(pickupText);

            if (locationSearchService != null) {

                locationSearchService.search(
                        pickupText,
                        new LocationSearchService.SearchCallback() {

                            @Override
                            public void onResults(
                                    List<LocationSearchService.LocationResult> results
                            ) {

                                if (results != null && !results.isEmpty()) {

                                    selectPickupFromSearch(results.get(0));

                                    if (destinationLocation != null && !locationsConfirmed) {
                                        confirmLocations();
                                    }
                                }
                            }

                            @Override
                            public void onError(String message) {
                            }
                        }
                );
            }
        }

        double destLat = intent.getDoubleExtra("destinationLat", 0.0);
        double destLng = intent.getDoubleExtra("destinationLng", 0.0);

        if (destLat != 0.0 && destLng != 0.0) {

            String name = intent.getStringExtra("destinationName");
            String address = intent.getStringExtra("destinationAddress");

            LocationSearchService.LocationResult destResult =
                    new LocationSearchService.LocationResult(
                            name != null ? name : destinationText,
                            address != null ? address : "",
                            destLat,
                            destLng
                    );

            selectDestinationFromSearch(destResult);

        } else if (destinationText != null && !destinationText.isEmpty()) {

            etDestinationSearch.setText(destinationText);

            if (locationSearchService != null) {

                locationSearchService.search(
                        destinationText,
                        new LocationSearchService.SearchCallback() {

                            @Override
                            public void onResults(
                                    List<LocationSearchService.LocationResult> results
                            ) {

                                if (results != null && !results.isEmpty()) {

                                    selectDestinationFromSearch(results.get(0));

                                    if (pickupLocation != null && !locationsConfirmed) {
                                        confirmLocations();
                                    }
                                }
                            }

                            @Override
                            public void onError(String message) {
                            }
                        }
                );
            }
        }

        if (pickupLocation != null && destinationLocation != null && !locationsConfirmed) {
            confirmLocations();
        }
    }

    @Override
    protected void onDestroy() {

        if (simulationHandler != null && simulationRunnable != null) {
            simulationHandler.removeCallbacks(simulationRunnable);
            simulationHandler = null;
        }

        if (currentRideListener != null) {
            currentRideListener.remove();
            currentRideListener = null;
        }

        if (acceptedDriverLocationListener != null) {
            acceptedDriverLocationListener.remove();
            acceptedDriverLocationListener = null;
        }

        // Stop Firestore listener
        if (driverLocationListener != null) {
            driverLocationListener.remove();
            driverLocationListener = null;
        }

        // Remove all driver markers
        for (Marker marker :
                driverMarkers.values()) {

            if (marker != null) {
                marker.remove();
            }
        }

        driverMarkers.clear();

        if (locationSearchService != null) {
            locationSearchService.shutdown();
        }

        if (routeService != null) {
            routeService.shutdown();
        }

        driverMatchingService = null;

        if (mapView != null) {
            mapView.onDestroy();
        }

        super.onDestroy();
    }

    @Override
    public void onLowMemory() {

        super.onLowMemory();

        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    protected void onSaveInstanceState(
            @NonNull Bundle outState
    ) {

        if (mapView != null) {

            mapView.onSaveInstanceState(
                    outState
            );
        }

        super.onSaveInstanceState(
                outState
        );
    }
}