package com.example.ridematch;

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationSearchService {

    public interface SearchCallback {
        void onResults(List<LocationResult> results);
        void onError(String message);
    }

    public static class LocationResult {

        private final String name;
        private final String address;
        private final double latitude;
        private final double longitude;

        public LocationResult(
                String name,
                String address,
                double latitude,
                double longitude
        ) {
            this.name = name;
            this.address = address;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getName() {
            return name;
        }

        public String getAddress() {
            return address;
        }

        public double getLatitude() {
            return latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public String getDisplayName() {

            if (address == null || address.trim().isEmpty()) {
                return name;
            }

            return name + ", " + address;
        }
    }

    private final ExecutorService executorService =
            Executors.newSingleThreadExecutor();

    private final Handler mainHandler =
            new Handler(Looper.getMainLooper());

    private Runnable pendingSearch;

    public void search(
            String query,
            SearchCallback callback
    ) {

        if (query == null || query.trim().length() < 2) {
            mainHandler.post(() ->
                    callback.onResults(new ArrayList<>())
            );
            return;
        }

        if (pendingSearch != null) {
            mainHandler.removeCallbacks(pendingSearch);
        }

        final String searchText = query.trim();

        pendingSearch = () -> {

            executorService.execute(() -> {

                try {

                    Uri.Builder uriBuilder =
                            Uri.parse("https://photon.komoot.io/api/")
                                    .buildUpon();

                    uriBuilder.appendQueryParameter(
                            "q",
                            searchText
                    );

                    uriBuilder.appendQueryParameter(
                            "limit",
                            "5"
                    );

                    uriBuilder.appendQueryParameter(
                            "lang",
                            "en"
                    );

                    // Pune bias.
                    uriBuilder.appendQueryParameter(
                            "lat",
                            "18.5204"
                    );

                    uriBuilder.appendQueryParameter(
                            "lon",
                            "73.8567"
                    );

                    URL url =
                            new URL(uriBuilder.build().toString());

                    HttpURLConnection connection =
                            (HttpURLConnection) url.openConnection();

                    connection.setRequestMethod("GET");

                    connection.setConnectTimeout(8000);
                    connection.setReadTimeout(8000);

                    connection.setRequestProperty(
                            "Accept",
                            "application/json"
                    );

                    connection.setRequestProperty(
                            "User-Agent",
                            "VeloCityCollegeProject/1.0"
                    );

                    int responseCode =
                            connection.getResponseCode();

                    if (responseCode != 200) {

                        throw new Exception(
                                "Search service returned HTTP "
                                        + responseCode
                        );
                    }

                    InputStream inputStream =
                            connection.getInputStream();

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            inputStream,
                                            StandardCharsets.UTF_8
                                    )
                            );

                    StringBuilder response =
                            new StringBuilder();

                    String line;

                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }

                    reader.close();
                    inputStream.close();
                    connection.disconnect();

                    List<LocationResult> results =
                            parseResults(response.toString());

                    mainHandler.post(() ->
                            callback.onResults(results)
                    );

                } catch (Exception e) {

                    mainHandler.post(() ->
                            callback.onError(
                                    e.getMessage() != null
                                            ? e.getMessage()
                                            : "Unable to search location"
                            )
                    );
                }
            });
        };

        // Wait 500 ms after the user stops typing.
        mainHandler.postDelayed(
                pendingSearch,
                500
        );
    }

    private List<LocationResult> parseResults(
            String json
    ) throws Exception {

        List<LocationResult> results =
                new ArrayList<>();

        JSONObject root =
                new JSONObject(json);

        JSONArray features =
                root.optJSONArray("features");

        if (features == null) {
            return results;
        }

        for (int i = 0; i < features.length(); i++) {

            JSONObject feature =
                    features.getJSONObject(i);

            JSONObject geometry =
                    feature.optJSONObject("geometry");

            JSONObject properties =
                    feature.optJSONObject("properties");

            if (geometry == null || properties == null) {
                continue;
            }

            JSONArray coordinates =
                    geometry.optJSONArray("coordinates");

            if (coordinates == null ||
                    coordinates.length() < 2) {
                continue;
            }

            double longitude =
                    coordinates.optDouble(0);

            double latitude =
                    coordinates.optDouble(1);

            if (Double.isNaN(latitude) ||
                    Double.isNaN(longitude)) {
                continue;
            }

            String name =
                    properties.optString(
                            "name",
                            ""
                    );

            String city =
                    properties.optString(
                            "city",
                            ""
                    );

            String state =
                    properties.optString(
                            "state",
                            ""
                    );

            String country =
                    properties.optString(
                            "country",
                            ""
                    );

            String address =
                    buildAddress(
                            city,
                            state,
                            country
                    );

            if (name.isEmpty()) {
                name = address;
            }

            results.add(
                    new LocationResult(
                            name,
                            address,
                            latitude,
                            longitude
                    )
            );
        }

        return results;
    }

    private String buildAddress(
            String city,
            String state,
            String country
    ) {

        List<String> parts =
                new ArrayList<>();

        if (!city.isEmpty()) {
            parts.add(city);
        }

        if (!state.isEmpty()) {
            parts.add(state);
        }

        if (!country.isEmpty()) {
            parts.add(country);
        }

        return String.join(
                ", ",
                parts
        );
    }

    public void shutdown() {

        executorService.shutdownNow();

        if (pendingSearch != null) {
            mainHandler.removeCallbacks(
                    pendingSearch
            );
        }
    }
}