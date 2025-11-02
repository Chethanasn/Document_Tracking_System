package com.example.dts;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvProfileName, tvProfileRole, tvProfileEmail, tvProfileId, btnBack, tvTopTitle;
    private EditText etCurrentPassword, etNewPassword, etConfirmPassword;
    private MaterialButton btnSavePassword, btnLogout;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;
    private String userRole = "Student"; // default fallback

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_DTS);
        setContentView(R.layout.activity_profile);

        // 🔹 UI initialization
        tvProfileName = findViewById(R.id.tvProfileName);
        tvProfileRole = findViewById(R.id.tvProfileRole);
        tvProfileEmail = findViewById(R.id.tvProfileEmail);
        tvProfileId = findViewById(R.id.tvProfileId);
        tvTopTitle = findViewById(R.id.tvTopTitle);
        etCurrentPassword = findViewById(R.id.etCurrentPassword);
        etNewPassword = findViewById(R.id.etNewPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);
        btnSavePassword = findViewById(R.id.btnSavePassword);
        btnLogout = findViewById(R.id.btnLogout);
        btnBack = findViewById(R.id.btnBack);

        // 🔹 Firebase setup
        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            loadProfileFromFirebase(user.getUid());
        }

        btnSavePassword.setOnClickListener(v -> updatePassword());
        btnLogout.setOnClickListener(v -> logoutUser());
        btnBack.setOnClickListener(v -> navigateBack());
    }

    /** 🔹 Load profile info */
    private void loadProfileFromFirebase(String uid) {
        dbRef.child("users").child(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    Toast.makeText(ProfileActivity.this, "Profile not found", Toast.LENGTH_SHORT).show();
                    return;
                }

                String name = snapshot.child("name").getValue(String.class);
                String role = snapshot.child("primaryRole").getValue(String.class);
                String email = snapshot.child("email").getValue(String.class);
                String userId = snapshot.child("userId").getValue(String.class);

                if (role != null) userRole = role;

                tvProfileName.setText(name != null ? name : "N/A");
                tvProfileRole.setText(role != null ? role : "N/A");
                tvProfileEmail.setText(email != null ? "Email ID: " + email : "Email ID: N/A");

                // 🔸 Dynamic Top Bar Title
                if ("Student".equalsIgnoreCase(role)) {
                    tvTopTitle.setText("DTS Student");
                    tvProfileId.setText(userId != null ? "Student ID: " + userId : "Student ID: N/A");
                } else {
                    tvTopTitle.setText("DTS Admin");
                    tvProfileId.setText(userId != null ? "College ID: " + userId : "College ID: N/A");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 🔹 Password Update */
    private void updatePassword() {
        String current = etCurrentPassword.getText().toString().trim();
        String newPass = etNewPassword.getText().toString().trim();
        String confirm = etConfirmPassword.getText().toString().trim();

        if (current.isEmpty() || newPass.isEmpty() || confirm.isEmpty()) {
            Toast.makeText(this, "All fields are required", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!newPass.equals(confirm)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseUser user = mAuth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        mAuth.signInWithEmailAndPassword(user.getEmail(), current)
                .addOnSuccessListener(authResult -> user.updatePassword(newPass)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(ProfileActivity.this, "Password updated successfully!", Toast.LENGTH_SHORT).show();
                            etCurrentPassword.setText("");
                            etNewPassword.setText("");
                            etConfirmPassword.setText("");
                        })
                        .addOnFailureListener(e ->
                                Toast.makeText(ProfileActivity.this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .addOnFailureListener(e ->
                        Toast.makeText(ProfileActivity.this, "Current password is incorrect", Toast.LENGTH_SHORT).show());
    }

    /** 🔹 Logout */
    private void logoutUser() {
        FirebaseAuth.getInstance().signOut();
        getSharedPreferences("DTSLoginPrefs", MODE_PRIVATE).edit().clear().apply();

        Intent i = new Intent(ProfileActivity.this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
        finish();
    }

    /** 🔹 Back Navigation */
    private void navigateBack() {
        Intent intent;
        if ("Student".equalsIgnoreCase(userRole)) {
            intent = new Intent(ProfileActivity.this, StudentDashboardActivity.class);
        } else {
            intent = new Intent(ProfileActivity.this, AdminDashboardActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
}