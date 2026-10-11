package com.example.ridematch;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {

    private FirebaseAuth auth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        // Check if user is already logged in
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser != null) {
            checkUserSessionAndNavigate(currentUser.getUid());
            return;
        }

        setupMainUi();
    }

    private void checkUserSessionAndNavigate(String userId) {
        setContentView(R.layout.activity_main);

        Button btnCustomer = findViewById(R.id.btnCustomer);
        Button btnDriver = findViewById(R.id.btnDriver);
        Button btnLogin = findViewById(R.id.btnLogin);

        if (btnCustomer != null) btnCustomer.setVisibility(View.GONE);
        if (btnDriver != null) btnDriver.setVisibility(View.GONE);
        if (btnLogin != null) btnLogin.setVisibility(View.GONE);

        Context context = this;

        db.collection("users").document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String role = documentSnapshot.getString("role");
                        Intent intent;
                        if ("driver".equals(role)) {
                            intent = new Intent(context, DriverHomeActivity.class);
                        } else {
                            intent = new Intent(context, CustomerHomeActivity.class);
                        }
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    } else {
                        setupMainUi();
                    }
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(context, "Session error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    setupMainUi();
                });
    }

    private void setupMainUi() {
        setContentView(R.layout.activity_main);

        Button btnCustomer = findViewById(R.id.btnCustomer);
        Button btnDriver = findViewById(R.id.btnDriver);
        Button btnLogin = findViewById(R.id.btnLogin);

        if (btnCustomer != null) {
            btnCustomer.setVisibility(View.VISIBLE);
            btnCustomer.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                intent.putExtra("role", "customer");
                startActivity(intent);
            });
        }

        if (btnDriver != null) {
            btnDriver.setVisibility(View.VISIBLE);
            btnDriver.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, RegisterActivity.class);
                intent.putExtra("role", "driver");
                startActivity(intent);
            });
        }

        if (btnLogin != null) {
            btnLogin.setVisibility(View.VISIBLE);
            btnLogin.setOnClickListener(v -> {
                Intent intent = new Intent(MainActivity.this, LoginActivity.class);
                startActivity(intent);
            });
        }
    }
}