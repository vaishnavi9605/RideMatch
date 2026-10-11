package com.example.ridematch;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RideHistoryAdapter extends RecyclerView.Adapter<RideHistoryAdapter.ViewHolder> {

    private final List<DocumentSnapshot> rideList;
    private final boolean isDriver;

    public RideHistoryAdapter(List<DocumentSnapshot> rideList, boolean isDriver) {
        this.rideList = rideList;
        this.isDriver = isDriver;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ride_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DocumentSnapshot doc = rideList.get(position);

        // Date
        Timestamp createdAt = doc.getTimestamp("createdAt");
        if (createdAt != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault());
            holder.tvRideDate.setText("📅 " + sdf.format(createdAt.toDate()));
        } else {
            holder.tvRideDate.setText("📅 Trip Record");
        }

        // Status Badge
        String status = doc.getString("status");
        if ("completed".equalsIgnoreCase(status)) {
            holder.tvRideStatusBadge.setText("Completed 🟢");
        } else if ("searching".equalsIgnoreCase(status)) {
            holder.tvRideStatusBadge.setText("Searching 🟡");
        } else if ("accepted".equalsIgnoreCase(status)) {
            holder.tvRideStatusBadge.setText("Accepted 🔵");
        } else if ("in_progress".equalsIgnoreCase(status) || "arrived".equalsIgnoreCase(status)) {
            holder.tvRideStatusBadge.setText("On The Way 🚕");
        } else {
            holder.tvRideStatusBadge.setText("Cancelled 🔴");
        }

        // Pickup & Destination
        String pickup = doc.getString("pickup");
        String destination = doc.getString("destination");
        holder.tvHistoryPickup.setText("📍 Pickup: " + (pickup != null ? pickup : "N/A"));
        holder.tvHistoryDestination.setText("🔴 Destination: " + (destination != null ? destination : "N/A"));

        // Fare & Distance
        Long fare = doc.getLong("estimatedFare");
        Double distance = doc.getDouble("distanceKm");
        holder.tvHistoryFare.setText("💰 ₹" + (fare != null ? fare : 0));
        holder.tvHistoryDistance.setText(String.format(Locale.US, "🛣 %.1f km", distance != null ? distance : 0.0));

        // Person Name (Driver name if viewing as Customer, or Customer name if viewing as Driver)
        if (isDriver) {
            String customerName = doc.getString("customerName");
            holder.tvHistoryPerson.setText("👤 Passenger: " + (customerName != null && !customerName.isEmpty() ? customerName : "Customer"));
        } else {
            String driverName = doc.getString("driverName");
            holder.tvHistoryPerson.setText("👤 Driver: " + (driverName != null && !driverName.isEmpty() ? driverName : "VeloCity Driver"));
        }

        // Rating
        Double rating = doc.getDouble("rating");
        if (rating != null && rating > 0) {
            holder.tvHistoryRating.setText(String.format(Locale.US, "⭐ %.1f", rating));
        } else {
            holder.tvHistoryRating.setText("⭐ Unrated");
        }
    }

    @Override
    public int getItemCount() {
        return rideList != null ? rideList.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvRideDate;
        TextView tvRideStatusBadge;
        TextView tvHistoryPickup;
        TextView tvHistoryDestination;
        TextView tvHistoryFare;
        TextView tvHistoryDistance;
        TextView tvHistoryPerson;
        TextView tvHistoryRating;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvRideDate = itemView.findViewById(R.id.tvRideDate);
            tvRideStatusBadge = itemView.findViewById(R.id.tvRideStatusBadge);
            tvHistoryPickup = itemView.findViewById(R.id.tvHistoryPickup);
            tvHistoryDestination = itemView.findViewById(R.id.tvHistoryDestination);
            tvHistoryFare = itemView.findViewById(R.id.tvHistoryFare);
            tvHistoryDistance = itemView.findViewById(R.id.tvHistoryDistance);
            tvHistoryPerson = itemView.findViewById(R.id.tvHistoryPerson);
            tvHistoryRating = itemView.findViewById(R.id.tvHistoryRating);
        }
    }
}