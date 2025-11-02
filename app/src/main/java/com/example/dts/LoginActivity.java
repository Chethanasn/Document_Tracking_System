package com.example.dts;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginActivity extends AppCompatActivity {

    private TabLayout roleTabLayout;
    private TextInputEditText etCollegeId, etPassword;
    private MaterialButton btnSignIn;
    private CheckBox cbRemember;
    private TextView tvForgot;
    private ProgressBar progressBar;
    private View rootLayout;

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    private String selectedRole = "Student"; // default tab

    private static final String PREFS_NAME = "DTSLoginPrefs";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_PASSWORD = "password";
    private static final String KEY_REMEMBER = "remember";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_DTS);
        setContentView(R.layout.activity_login);

        rootLayout = findViewById(R.id.loginRoot);

        roleTabLayout = findViewById(R.id.roleTabLayout);
        etCollegeId = findViewById(R.id.etCollegeId);
        etPassword = findViewById(R.id.etPassword);
        btnSignIn = findViewById(R.id.btnSignIn);
        cbRemember = findViewById(R.id.cbRemember);
        tvForgot = findViewById(R.id.tvForgot);
        progressBar = findViewById(R.id.progress);

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference("users");

        if (roleTabLayout.getTabCount() > 0) {
            roleTabLayout.getTabAt(0).select(); // Student by default
        }

        roleTabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                selectedRole = (tab.getPosition() == 0) ? "Student" : "Admin";
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        tvForgot.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, ForgotPasswordActivity.class);
            startActivity(intent);
        });

        loadSavedCredentials();

        autoLoginIfRemembered();

        btnSignIn.setOnClickListener(v -> loginUser());
    }

    private void loginUser() {
        String email = etCollegeId.getText().toString().trim();
        String password = etPassword.getText().toString().trim();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Invalid email or password.");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);
        btnSignIn.setEnabled(false);

        mAuth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    FirebaseUser user = mAuth.getCurrentUser();
                    if (user != null) {
                        String uid = user.getUid();
                        dbRef.child(uid).get().addOnCompleteListener(task -> {
                            progressBar.setVisibility(View.GONE);
                            btnSignIn.setEnabled(true);

                            if (task.isSuccessful() && task.getResult().exists()) {
                                DataSnapshot snapshot = task.getResult();
                                String role = snapshot.child("primaryRole").getValue(String.class);

                                if (role == null) {
                                    showError("Account setup incomplete. Contact admin.");
                                    return;
                                }

                                saveCredentialsIfNeeded(email, password, cbRemember.isChecked());

                                if ("Student".equalsIgnoreCase(role) && "Student".equalsIgnoreCase(selectedRole)) {
                                    startActivity(new Intent(LoginActivity.this, StudentDashboardActivity.class));
                                    finish();
                                } else if (!"Student".equalsIgnoreCase(role) && "Admin".equalsIgnoreCase(selectedRole)) {
                                    Intent i = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                                    i.putExtra("ROLE", role);
                                    startActivity(i);
                                    finish();
                                } else {
                                    showError("Please sign in under the correct section.");
                                }
                            } else {
                                showError("Account setup incomplete. Contact admin.");
                            }
                        });
                    }
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    btnSignIn.setEnabled(true);
                    showError("Invalid email or password.");
                });
    }

    private void saveCredentialsIfNeeded(String email, String password, boolean remember) {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (remember) {
            editor.putString(KEY_EMAIL, email);
            editor.putString(KEY_PASSWORD, password);
            editor.putBoolean(KEY_REMEMBER, true);
        } else {
            editor.clear(); // clear all saved data if unchecked
        }
        editor.apply();
    }

    private void loadSavedCredentials() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);

        if (remember) {
            String email = prefs.getString(KEY_EMAIL, "");
            String password = prefs.getString(KEY_PASSWORD, "");

            etCollegeId.setText(email);
            etPassword.setText(password);
            cbRemember.setChecked(true);
        }
    }

    private void autoLoginIfRemembered() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean remember = prefs.getBoolean(KEY_REMEMBER, false);
        String email = prefs.getString(KEY_EMAIL, "");
        String password = prefs.getString(KEY_PASSWORD, "");

        if (remember && !email.isEmpty() && !password.isEmpty()) {
            progressBar.setVisibility(View.VISIBLE);
            btnSignIn.setEnabled(false);

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener(authResult -> {
                        FirebaseUser user = mAuth.getCurrentUser();
                        if (user != null) {
                            dbRef.child(user.getUid()).get().addOnSuccessListener(snapshot -> {
                                progressBar.setVisibility(View.GONE);
                                btnSignIn.setEnabled(true);

                                if (snapshot.exists()) {
                                    String role = snapshot.child("primaryRole").getValue(String.class);

                                    if ("Student".equalsIgnoreCase(role)) {
                                        startActivity(new Intent(LoginActivity.this, StudentDashboardActivity.class));
                                    } else {
                                        Intent intent = new Intent(LoginActivity.this, AdminDashboardActivity.class);
                                        intent.putExtra("ROLE", role);
                                        startActivity(intent);
                                    }
                                    finish();
                                } else {
                                    showError("User data not found. Please log in again.");
                                }
                            });
                        }
                    })
                    .addOnFailureListener(e -> {
                        progressBar.setVisibility(View.GONE);
                        btnSignIn.setEnabled(true);
                        showError("Auto-login failed. Please sign in manually.");
                    });
        }
    }

    private void showError(String message) {
        Snackbar snackbar = Snackbar.make(rootLayout, message, Snackbar.LENGTH_LONG);
        snackbar.setBackgroundTint(getResources().getColor(android.R.color.holo_red_dark));
        snackbar.setTextColor(getResources().getColor(android.R.color.white));
        snackbar.show();
    }
}