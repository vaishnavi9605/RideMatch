package com.example.ridematch;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RouteService {

    // Public OSRM endpoint.
    // Use only for low-frequency college/project testing.
    // Replace this provider later with a managed/self-hosted backend
    // without changing RideMapActivity's route interface.
    private static final String OSRM_BASE_URL =
            "https://router.project-osrm.org/route/v1/driving/";

    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    public interface RouteCallback {
        void onSuccess(RouteResult routeResult);
        void onError(String message);
    }

    public static class RouteResult {

        private final double distanceMeters;
        private final double durationSeconds;
        private final String geometryJson;

        public RouteResult(
                double distanceMeters,
                double durationSeconds,
                String geometryJson
        ) {
            this.distanceMeters = distanceMeters;
            this.durationSeconds = durationSeconds;
            this.geometryJson = geometryJson;
        }

        public double getDistanceMeters() {
            return distanceMeters;
        }

        public double getDurationSeconds() {
            return durationSeconds;
        }

        public String getGeometryJson() {
            return geometryJson;
        }
    }

    public void getRoute(
            double pickupLatitude,
            double pickupLongitude,
            double destinationLatitude,
            double destinationLongitude,
            RouteCallback callback
    ) {

        executor.execute(() -> {

            HttpURLConnection connection = null;

            try {

                String urlString =
                        OSRM_BASE_URL
                                + pickupLongitude
                                + ","
                                + pickupLatitude
                                + ";"
                                + destinationLongitude
                                + ","
                                + destinationLatitude
                                + "?overview=full&geometries=geojson";

                URL url =
                        new URL(urlString);

                connection =
                        (HttpURLConnection) url.openConnection();

                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty(
                        "Accept",
                        "application/json"
                );
                connection.setRequestProperty(
                        "User-Agent",
                        "VeloCity-College-Project/1.0"
                );

                int responseCode =
                        connection.getResponseCode();

                InputStream inputStream;

                if (responseCode >= 200 &&
                        responseCode < 300) {

                    inputStream =
                            connection.getInputStream();

                } else {

                    inputStream =
                            connection.getErrorStream();

                    String errorBody =
                            readStream(inputStream);

                    throw new Exception(
                            "Routing server HTTP "
                                    + responseCode
                                    + (errorBody.isEmpty()
                                    ? ""
                                    : ": " + errorBody)
                    );
                }

                String response =
                        readStream(inputStream);

                JSONObject root =
                        new JSONObject(response);

                String code =
                        root.optString("code", "");

                if (!"Ok".equalsIgnoreCase(code)) {

                    String message =
                            root.optString(
                                    "message",
                                    "No route found"
                            );

                    throw new Exception(
                            "OSRM: " + code
                                    + " - " + message
                    );
                }

                JSONArray routes =
                        root.optJSONArray("routes");

                if (routes == null ||
                        routes.length() == 0) {

                    throw new Exception(
                            "No route was returned"
                    );
                }

                JSONObject route =
                        routes.getJSONObject(0);

                double distanceMeters =
                        route.getDouble("distance");

                double durationSeconds =
                        route.getDouble("duration");

                String geometryJson =
                        route.optString(
                                "geometry",
                                ""
                        );

                RouteResult result =
                        new RouteResult(
                                distanceMeters,
                                durationSeconds,
                                geometryJson
                        );

                mainHandler.post(
                        () -> callback.onSuccess(result)
                );

            } catch (Exception e) {

                String message =
                        e.getMessage();

                if (message == null ||
                        message.trim().isEmpty()) {

                    message =
                            "Unknown routing error";
                }

                String finalMessage =
                        message;

                mainHandler.post(
                        () -> callback.onError(
                                finalMessage
                        )
                );

            } finally {

                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    private String readStream(
            InputStream inputStream
    ) throws Exception {

        if (inputStream == null) {
            return "";
        }

        StringBuilder builder =
                new StringBuilder();

        try (BufferedReader reader =
                     new BufferedReader(
                             new InputStreamReader(
                                     inputStream
                             )
                     )) {

            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }

        return builder.toString();
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
