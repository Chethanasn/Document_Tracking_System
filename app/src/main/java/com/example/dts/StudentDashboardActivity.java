package com.example.dts;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.*;

import java.util.ArrayList;
import java.util.List;

import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.graphics.Color;
import android.view.MenuInflater;

public class StudentDashboardActivity extends AppCompatActivity {

    private ImageView btnProfileMenuStudent;
    private RecyclerView recyclerDocuments;
    private TextInputEditText etSearchStudent;
    private ChipGroup chipGroupStudentFilter;

    private RequestAdapter adapter;
    private List<RequestModel> docList = new ArrayList<>();
    private List<RequestModel> filteredList = new ArrayList<>();

    private FirebaseAuth mAuth;
    private DatabaseReference dbRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_DTS);
        setContentView(R.layout.activity_student_dashboard);

        btnProfileMenuStudent = findViewById(R.id.btnProfileMenuStudent);
        recyclerDocuments = findViewById(R.id.recyclerDocuments);
        etSearchStudent = findViewById(R.id.etSearchStudent);
        chipGroupStudentFilter = findViewById(R.id.chipGroupStudentFilter);

        recyclerDocuments.setLayoutManager(new LinearLayoutManager(this));

        mAuth = FirebaseAuth.getInstance();
        dbRef = FirebaseDatabase.getInstance().getReference();

        loadDocumentsFromFirebase();

        btnProfileMenuStudent.setOnClickListener(this::showPopupMenu);

        chipGroupStudentFilter.setOnCheckedChangeListener((group, checkedId) -> {
            String statusFilter = getSelectedStatus(checkedId);
            applyFilters(statusFilter, etSearchStudent.getText().toString());
        });

        etSearchStudent.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String statusFilter = getSelectedStatus(chipGroupStudentFilter.getCheckedChipId());
                applyFilters(statusFilter, s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /** Top-right popup menu (Profile + Logout) */
    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        MenuInflater inflater = popup.getMenuInflater();
        inflater.inflate(R.menu.nav_menu, popup.getMenu());

        // 🔹 Fetch student's name from Firebase
        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId);

            userRef.get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {
                        // ✅ Make the name bold and black
                        SpannableString styledTitle = new SpannableString(name);
                        styledTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), 0);
                        styledTitle.setSpan(new ForegroundColorSpan(Color.BLACK), 0, name.length(), 0);

                        popup.getMenu().findItem(R.id.nav_header).setTitle(styledTitle);
                    }
                }
            });
        }

        // 🔹 Handle menu item clicks
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class)); // ✅ common for both
                return true;
            } else if (id == R.id.nav_logout) {
                // ✅ Sign out user from Firebase
                FirebaseAuth.getInstance().signOut();

                // ✅ Clear Remember Me data so auto-login doesn’t trigger next time
                getSharedPreferences("DTSLoginPrefs", MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply();

                // ✅ Redirect to login screen
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
                return true;
            }
            return false;
        });

        popup.show();
    }

    private String getSelectedStatus(int chipId) {
        if (chipId == R.id.chipNotSubmitted) return "Not Submitted";
        if (chipId == R.id.chipPendingStudent) return "Pending";
        if (chipId == R.id.chipApprovedStudent) return "Approved";
        if (chipId == R.id.chipRejectedStudent) return "Rejected";
        return "All";
    }

    private void applyFilters(String statusFilter, String searchText) {
        filteredList.clear();
        for (RequestModel doc : docList) {
            boolean matchesStatus = statusFilter.equals("All") ||
                    doc.getStatus().equalsIgnoreCase(statusFilter);
            boolean matchesSearch = searchText.isEmpty() ||
                    doc.getName().toLowerCase().contains(searchText.toLowerCase());

            if (matchesStatus && matchesSearch) filteredList.add(doc);
        }
        adapter.notifyDataSetChanged();
    }

    /** Load documents live */
    private void loadDocumentsFromFirebase() {
        dbRef.child("flows").get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                docList.clear();
                for (DataSnapshot flowSnap : snapshot.getChildren()) {
                    String docName = flowSnap.getKey();
                    docList.add(new RequestModel("", docName, "Not Submitted"));
                }

                String studentId = mAuth.getUid();
                dbRef.child("documents").orderByChild("studentId").equalTo(studentId)
                        .addValueEventListener(new ValueEventListener() {
                            @Override
                            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                                for (RequestModel doc : docList) {
                                    doc.setStatus("Not Submitted");
                                }

                                for (DataSnapshot docSnap : dataSnapshot.getChildren()) {
                                    String type = docSnap.child("documentType").getValue(String.class);
                                    String status = docSnap.child("status").getValue(String.class);

                                    for (RequestModel doc : docList) {
                                        if (doc.getName().equals(type)) {
                                            doc.setStatus(status);
                                        }
                                    }
                                }

                                filteredList.clear();
                                filteredList.addAll(docList);

                                if (adapter == null) {
                                    adapter = new RequestAdapter(filteredList, true);
                                    recyclerDocuments.setAdapter(adapter);
                                } else {
                                    adapter.notifyDataSetChanged();
                                }
                            }

                            @Override public void onCancelled(@NonNull DatabaseError error) {}
                        });
            }
        });
    }
}