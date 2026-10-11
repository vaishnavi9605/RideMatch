package com.example.ridematch;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class LoginActivity extends AppCompatActivity {

    private EditText etLoginEmail, etLoginPassword;
    private Button btnLogin;
    private TextView tvRegister;

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etLoginEmail = findViewById(R.id.etLoginEmail);
        etLoginPassword = findViewById(R.id.etLoginPassword);
        btnLogin = findViewById(R.id.btnLogin);
        tvRegister = findViewById(R.id.tvRegister);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        btnLogin.setOnClickListener(v -> loginUser());

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this,
                    MainActivity.class);
            startActivity(intent);
        });
    }

    private void loginUser() {

        String email = etLoginEmail.getText().toString().trim();
        String password = etLoginPassword.getText().toString().trim();

        if (email.isEmpty()) {
            etLoginEmail.setError("Enter your email");
            etLoginEmail.requestFocus();
            return;
        }

        if (password.isEmpty()) {
            etLoginPassword.setError("Enter your password");
            etLoginPassword.requestFocus();
            return;
        }

        btnLogin.setEnabled(false);

        auth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(task -> {

                    if (task.isSuccessful()) {

                        String userId =
                                auth.getCurrentUser().getUid();

                        // Get user's role from Firestore
                        db.collection("users")
                                .document(userId)
                                .get()
                                .addOnSuccessListener(documentSnapshot -> {

                                    btnLogin.setEnabled(true);

                                    if (documentSnapshot.exists()) {

                                        String role =
                                                documentSnapshot.getString("role");

                                        Intent intent;
                                        if ("driver".equals(role)) {
                                            intent = new Intent(
                                                    LoginActivity.this,
                                                    DriverHomeActivity.class
                                            );
                                        } else {
                                            intent = new Intent(
                                                    LoginActivity.this,
                                                    CustomerHomeActivity.class
                                            );
                                        }
                                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();

                                    } else {

                                        Toast.makeText(
                                                LoginActivity.this,
                                                "User profile not found",
                                                Toast.LENGTH_LONG
                                        ).show();
                                    }
                                })
                                .addOnFailureListener(e -> {

                                    btnLogin.setEnabled(true);

                                    Toast.makeText(
                                            LoginActivity.this,
                                            "Database error: "
                                                    + e.getMessage(),
                                            Toast.LENGTH_LONG
                                    ).show();
                                });

                    } else {

                        btnLogin.setEnabled(true);

                        String message = "Login failed";

                        if (task.getException() != null) {
                            message =
                                    task.getException().getMessage();
                        }

                        Toast.makeText(
                                LoginActivity.this,
                                message,
                                Toast.LENGTH_LONG
                        ).show();
                    }
                });
    }
}