package com.example.ridematch;

public final class FareCalculator {

    private FareCalculator() {
        // Utility class.
    }

    // Pune / Pimpri-Chinchwad revised auto tariff
    // effective 1 September 2026:
    // ₹30 for the first 1.5 km
    // ₹20 for each additional kilometre.
    //
    // This is an ESTIMATED APP FARE, not a guarantee of the
    // final meter fare. Additional tariff rules, such as
    // night surcharges, will be added in a later stage.
    private static final double BASE_FARE = 30.0;
    private static final double BASE_DISTANCE_KM = 1.5;
    private static final double ADDITIONAL_RATE_PER_KM = 20.0;

    public static long calculateEstimatedFare(
            double distanceKm
    ) {

        if (distanceKm <= 0) {
            return 0;
        }

        double fare;

        if (distanceKm <= BASE_DISTANCE_KM) {

            fare = BASE_FARE;

        } else {

            fare =
                    BASE_FARE
                            + (
                            (distanceKm - BASE_DISTANCE_KM)
                                    * ADDITIONAL_RATE_PER_KM
                    );
        }

        return Math.round(fare);
    }
}
