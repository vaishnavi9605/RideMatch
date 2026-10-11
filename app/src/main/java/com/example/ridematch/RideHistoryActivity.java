package com.example.ridematch;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class RideHistoryActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private RecyclerView rvRideHistory;
    private TextView tvEmptyHistory;
    private TextView tvTotalRidesCount;
    private Button btnBackFromHistory;

    private boolean isDriver = false;

    private ListenerRegistration rideHistoryListener;


    // ============================================================
    // ON CREATE
    // ============================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(
                R.layout.activity_ride_history
        );


        auth =
                FirebaseAuth.getInstance();

        db =
                FirebaseFirestore.getInstance();


        isDriver =
                getIntent().getBooleanExtra(
                        "isDriver",
                        false
                );


        rvRideHistory =
                findViewById(
                        R.id.rvRideHistory
                );

        tvEmptyHistory =
                findViewById(
                        R.id.tvEmptyHistory
                );

        tvTotalRidesCount =
                findViewById(
                        R.id.tvTotalRidesCount
                );

        btnBackFromHistory =
                findViewById(
                        R.id.btnBackFromHistory
                );


        rvRideHistory.setLayoutManager(
                new LinearLayoutManager(this)
        );


        btnBackFromHistory.setOnClickListener(
                v -> finish()
        );


        // ========================================================
        // LOAD HISTORY IN REAL TIME
        // ========================================================

        listenToRideHistory();
    }


    // ============================================================
    // REAL-TIME RIDE HISTORY LISTENER
    // ============================================================

    private void listenToRideHistory() {

        if (auth == null ||
                auth.getCurrentUser() == null ||
                db == null) {

            Toast.makeText(
                    this,
                    "Please login to view ride history",
                    Toast.LENGTH_SHORT
            ).show();

            finish();

            return;
        }


        String userId =
                auth.getCurrentUser().getUid();


        String fieldToQuery =
                isDriver
                        ? "driverId"
                        : "customerId";


        // Remove previous listener if one exists

        if (rideHistoryListener != null) {

            rideHistoryListener.remove();

            rideHistoryListener = null;
        }


        /*
         * IMPORTANT:
         *
         * We intentionally do NOT use orderBy("createdAt")
         * here because that can require a Firestore composite
         * index.
         *
         * We sort the results locally instead.
         */

        rideHistoryListener =
                db.collection("rides")
                        .whereEqualTo(
                                fieldToQuery,
                                userId
                        )
                        .addSnapshotListener(
                                (querySnapshot, error) -> {

                                    if (error != null) {

                                        tvEmptyHistory.setVisibility(
                                                View.VISIBLE
                                        );

                                        rvRideHistory.setVisibility(
                                                View.GONE
                                        );

                                        tvEmptyHistory.setText(
                                                "Unable to load ride history:\n"
                                                        +
                                                        error.getMessage()
                                        );

                                        Toast.makeText(
                                                RideHistoryActivity.this,
                                                "History error: "
                                                        +
                                                        error.getMessage(),
                                                Toast.LENGTH_SHORT
                                        ).show();

                                        return;
                                    }


                                    if (querySnapshot == null ||
                                            querySnapshot.isEmpty()) {

                                        showEmptyHistory();

                                        return;
                                    }


                                    List<DocumentSnapshot> rides =
                                            new ArrayList<>(
                                                    querySnapshot
                                                            .getDocuments()
                                            );


                                    // ====================================================
                                    // SORT NEWEST RIDE FIRST
                                    // ====================================================

                                    rides.sort(
                                            (ride1, ride2) -> {

                                                Timestamp time1 =
                                                        ride1.getTimestamp(
                                                                "createdAt"
                                                        );

                                                Timestamp time2 =
                                                        ride2.getTimestamp(
                                                                "createdAt"
                                                        );


                                                if (time1 == null &&
                                                        time2 == null) {

                                                    return 0;
                                                }


                                                if (time1 == null) {

                                                    return 1;
                                                }


                                                if (time2 == null) {

                                                    return -1;
                                                }


                                                return time2.compareTo(
                                                        time1
                                                );
                                            }
                                    );


                                    // ====================================================
                                    // SHOW RESULTS
                                    // ====================================================

                                    tvEmptyHistory.setVisibility(
                                            View.GONE
                                    );

                                    rvRideHistory.setVisibility(
                                            View.VISIBLE
                                    );


                                    tvTotalRidesCount.setText(
                                            rides.size()
                                                    +
                                                    (
                                                            rides.size() == 1
                                                                    ? " ride recorded"
                                                                    : " rides recorded"
                                                    )
                                    );


                                    RideHistoryAdapter adapter =
                                            new RideHistoryAdapter(
                                                    rides,
                                                    isDriver
                                            );


                                    rvRideHistory.setAdapter(
                                            adapter
                                    );
                                }
                        );
    }


    // ============================================================
    // EMPTY HISTORY
    // ============================================================

    private void showEmptyHistory() {

        rvRideHistory.setVisibility(
                View.GONE
        );


        tvEmptyHistory.setVisibility(
                View.VISIBLE
        );


        tvEmptyHistory.setText(
                "No ride records found yet 🚕\n\n"
                        +
                        "Your VeloCity rides will appear here."
        );


        tvTotalRidesCount.setText(
                "0 rides recorded"
        );
    }


    // ============================================================
    // CLEANUP
    // ============================================================

    @Override
    protected void onDestroy() {

        if (rideHistoryListener != null) {

            rideHistoryListener.remove();

            rideHistoryListener = null;
        }


        super.onDestroy();
    }
}