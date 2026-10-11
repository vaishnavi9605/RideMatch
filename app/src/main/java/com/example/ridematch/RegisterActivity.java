package com.example.ridematch;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class RegisterActivity extends AppCompatActivity {

    private EditText etName, etPhone, etEmail, etPassword;
    private LinearLayout layoutDriverVerification;
    private EditText etIdProof, etLicenceNumber, etVehicleModel, etVehicleNumber, etRcNumber, etPhotoUrl;
    private Button btnRegister;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    private String role;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        etName = findViewById(R.id.etName);
        etPhone = findViewById(R.id.etPhone);
        etEmail = findViewById(R.id.etEmail);
        etPassword = findViewById(R.id.etPassword);

        layoutDriverVerification = findViewById(R.id.layoutDriverVerification);
        etIdProof = findViewById(R.id.etIdProof);
        etLicenceNumber = findViewById(R.id.etLicenceNumber);
        etVehicleModel = findViewById(R.id.etVehicleModel);
        etVehicleNumber = findViewById(R.id.etVehicleNumber);
        etRcNumber = findViewById(R.id.etRcNumber);
        etPhotoUrl = findViewById(R.id.etPhotoUrl);

        btnRegister = findViewById(R.id.btnRegister);
        TextView tvRole = findViewById(R.id.tvRole);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        role = getIntent().getStringExtra("role");

        if (role == null) {
            role = "customer";
        }

        if ("driver".equals(role)) {
            tvRole.setText("Driver Registration & Verification");
            if (layoutDriverVerification != null) {
                layoutDriverVerification.setVisibility(View.VISIBLE);
            }
        } else {
            tvRole.setText("Customer Registration");
            if (layoutDriverVerification != null) {
                layoutDriverVerification.setVisibility(View.GONE);
            }
        }

        btnRegister.setOnClickListener(v -> registerUser());
    }

    private void registerUser() {

        String name = etName.getText().toString().trim();
        String phone = etPhone.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (name.isEmpty()) {
            etName.setError("Enter your full name");
            return;
        }

        if (phone.isEmpty() || phone.length() < 10) {
            etPhone.setError("Enter a valid mobile phone number");
            return;
        }

        if (email.isEmpty()) {
            etEmail.setError("Enter your email address");
            return;
        }

        if (password.length() < 6) {
            etPassword.setError("Password must be at least 6 characters");
            return;
        }

        String idProof = "", licenceNumber = "", vehicleModel = "", vehicleNumber = "", rcNumber = "", photoUrl = "";

        if ("driver".equals(role)) {
            idProof = etIdProof.getText().toString().trim();
            licenceNumber = etLicenceNumber.getText().toString().trim();
            vehicleModel = etVehicleModel.getText().toString().trim();
            vehicleNumber = etVehicleNumber.getText().toString().trim();
            rcNumber = etRcNumber.getText().toString().trim();
            photoUrl = etPhotoUrl.getText().toString().trim();

            if (idProof.isEmpty()) {
                etIdProof.setError("Enter Govt ID Proof / Aadhaar Number");
                return;
            }

            if (licenceNumber.isEmpty()) {
                etLicenceNumber.setError("Enter Driving Licence Number");
                return;
            }

            if (vehicleModel.isEmpty()) {
                etVehicleModel.setError("Enter Vehicle Model & Color");
                return;
            }

            if (vehicleNumber.isEmpty()) {
                etVehicleNumber.setError("Enter Vehicle Registration Plate Number");
                return;
            }

            if (rcNumber.isEmpty()) {
                etRcNumber.setError("Enter Vehicle RC Card Registration Number");
                return;
            }
        }

        btnRegister.setEnabled(false);

        final String finalPhone = phone;
        final String finalIdProof = idProof;
        final String finalLicenceNumber = licenceNumber;
        final String finalVehicleModel = vehicleModel;
        final String finalVehicleNumber = vehicleNumber;
        final String finalRcNumber = rcNumber;
        final String finalPhotoUrl = photoUrl;

        auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        FirebaseUser currentUser = auth.getCurrentUser();
                        if (currentUser == null) {
                            btnRegister.setEnabled(true);
                            Toast.makeText(
                                    RegisterActivity.this,
                                    "Registration failed: User session not found",
                                    Toast.LENGTH_SHORT
                            ).show();
                            return;
                        }

                        String userId = currentUser.getUid();

                        Map<String, Object> user = new HashMap<>();
                        user.put("name", name);
                        user.put("phone", finalPhone);
                        user.put("email", email);
                        user.put("role", role);
                        user.put("createdAt", com.google.firebase.Timestamp.now());

                        if ("driver".equals(role)) {
                            user.put("idProof", finalIdProof);
                            user.put("licenceNumber", finalLicenceNumber);
                            user.put("vehicleModel", finalVehicleModel);
                            user.put("vehicleNumber", finalVehicleNumber);
                            user.put("rcNumber", finalRcNumber);
                            user.put("photoUrl", finalPhotoUrl);
                            user.put("verificationStatus", "verified");
                            user.put("isOnline", false);
                        }

                        db.collection("users")
                                .document(userId)
                                .set(user)
                                .addOnSuccessListener(unused -> {

                                    Toast.makeText(
                                            RegisterActivity.this,
                                            "Registration successful! Welcome to VeloCity",
                                            Toast.LENGTH_SHORT
                                    ).show();

                                    Intent intent;
                                    if ("driver".equals(role)) {
                                        intent = new Intent(
                                                RegisterActivity.this,
                                                DriverHomeActivity.class
                                        );
                                    } else {
                                        intent = new Intent(
                                                RegisterActivity.this,
                                                CustomerHomeActivity.class
                                        );
                                    }
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();

                                })
                                .addOnFailureListener(e -> {

                                    btnRegister.setEnabled(true);

                                    Toast.makeText(
                                            RegisterActivity.this,
                                            "Database error: " + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });

                    } else {

                        btnRegister.setEnabled(true);

                        Exception exception = task.getException();
                        String errorMessage = (exception != null && exception.getMessage() != null)
                                ? exception.getMessage()
                                : "Unknown error";

                        Toast.makeText(
                                RegisterActivity.this,
                                "Registration failed: " + errorMessage,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}