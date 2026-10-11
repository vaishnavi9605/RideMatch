package com.example.ridematch;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Smart Driver Ride Preference Matching Service.
 *
 * Discovers online drivers and evaluates each ride against the driver's
 * saved preferences (minFare, maxPickupDistance, maxRideDistance).
 */
public class DriverMatchingService {

    private final FirebaseFirestore db;

    public DriverMatchingService() {
        db = FirebaseFirestore.getInstance();
    }

    public interface MatchingCallback {
        void onSuccess(List<DriverCandidate> candidates);
        void onError(String message);
    }

    public static class DriverCandidate {

        private final String driverId;
        private final double latitude;
        private final double longitude;
        private final double pickupDistanceKm;

        public DriverCandidate(
                String driverId,
                double latitude,
                double longitude,
                double pickupDistanceKm
        ) {
            this.driverId = driverId;
            this.latitude = latitude;
            this.longitude = longitude;
            this.pickupDistanceKm = pickupDistanceKm;
        }

        public String getDriverId() {
            return driverId;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public double getPickupDistanceKm() {
            return pickupDistanceKm;
        }
    }

    public void findOnlineDriversNearPickup(
            double pickupLatitude,
            double pickupLongitude,
            MatchingCallback callback
    ) {
        findOnlineDriversNearPickup(pickupLatitude, pickupLongitude, 0.0, 0.0, callback);
    }

    /**
     * Finds online drivers near pickup and filters out drivers whose preferences
     * (minFare, maxPickupDistance, maxRideDistance) exclude this ride.
     */
    public void findOnlineDriversNearPickup(
            double pickupLatitude,
            double pickupLongitude,
            double estimatedFare,
            double tripDistanceKm,
            MatchingCallback callback
    ) {

        db.collection("driverLocations")
                .whereEqualTo("isOnline", true)
                .get()
                .addOnSuccessListener(snapshot -> {

                    List<DriverCandidate> candidates = new ArrayList<>();

                    if (snapshot == null || snapshot.isEmpty()) {
                        callback.onSuccess(candidates);
                        return;
                    }

                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    int totalDocs = docs.size();
                    int[] processed = {0};

                    for (DocumentSnapshot document : docs) {

                        String driverId = document.getId();
                        Double latitude = document.getDouble("latitude");
                        Double longitude = document.getDouble("longitude");

                        if (latitude == null || longitude == null) {
                            processed[0]++;
                            if (processed[0] == totalDocs) {
                                candidates.sort(Comparator.comparingDouble(DriverCandidate::getPickupDistanceKm));
                                callback.onSuccess(candidates);
                            }
                            continue;
                        }

                        double distanceKm = calculateDistanceKm(
                                pickupLatitude,
                                pickupLongitude,
                                latitude,
                                longitude
                        );

                        // Check driver preferences in users/{driverId}
                        db.collection("users").document(driverId).get()
                                .addOnSuccessListener(userDoc -> {
                                    processed[0]++;
                                    if (userDoc != null && userDoc.exists()) {
                                        Double minFare = userDoc.getDouble("minFare");
                                        Double maxPickup = userDoc.getDouble("maxPickupDistance");
                                        Double maxRide = userDoc.getDouble("maxRideDistance");

                                        boolean matchFare = minFare == null || estimatedFare == 0.0 || estimatedFare >= minFare;
                                        boolean matchPickup = maxPickup == null || distanceKm <= maxPickup;
                                        boolean matchTrip = maxRide == null || tripDistanceKm == 0.0 || tripDistanceKm <= maxRide;

                                        if (matchFare && matchPickup && matchTrip) {
                                            candidates.add(new DriverCandidate(driverId, latitude, longitude, distanceKm));
                                        }
                                    } else {
                                        candidates.add(new DriverCandidate(driverId, latitude, longitude, distanceKm));
                                    }

                                    if (processed[0] == totalDocs) {
                                        candidates.sort(Comparator.comparingDouble(DriverCandidate::getPickupDistanceKm));
                                        callback.onSuccess(candidates);
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    processed[0]++;
                                    candidates.add(new DriverCandidate(driverId, latitude, longitude, distanceKm));
                                    if (processed[0] == totalDocs) {
                                        candidates.sort(Comparator.comparingDouble(DriverCandidate::getPickupDistanceKm));
                                        callback.onSuccess(candidates);
                                    }
                                });
                    }
                })
                .addOnFailureListener(
                        e -> callback.onError(
                                e.getMessage() == null
                                        ? "Unable to read driver locations"
                                        : e.getMessage()
                        )
                );
    }

    private double calculateDistanceKm(
            double lat1,
            double lon1,
            double lat2,
            double lon2
    ) {

        double earthRadiusKm = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c =
                2 * Math.atan2(
                        Math.sqrt(a),
                        Math.sqrt(1 - a)
                );

        return earthRadiusKm * c;
    }
}