package com.example.dts;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuInflater;
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

public class AdminDashboardActivity extends AppCompatActivity {

    private ImageView btnProfileMenu;
    private RecyclerView recyclerRequests;
    private TextInputEditText etSearch;
    private ChipGroup chipGroupFilter;

    private RequestAdapter adapter;
    private final List<RequestModel> docList = new ArrayList<>();
    private final List<RequestModel> filteredList = new ArrayList<>();

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_DTS);
        setContentView(R.layout.activity_admin_dashboard);

        btnProfileMenu = findViewById(R.id.btnProfileMenu);
        recyclerRequests = findViewById(R.id.recyclerRequests);
        etSearch = findViewById(R.id.etSearch);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);

        recyclerRequests.setLayoutManager(new LinearLayoutManager(this));
        adapter = new RequestAdapter(filteredList);
        recyclerRequests.setAdapter(adapter);

        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        listenDocumentsForMe();

        btnProfileMenu.setOnClickListener(this::showPopupMenu);
        chipGroupFilter.setOnCheckedChangeListener((group, checkedId) -> applyFilters());
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilters(); }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void showPopupMenu(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenuInflater().inflate(R.menu.nav_menu, popup.getMenu());

        String userId = FirebaseAuth.getInstance().getUid();
        if (userId != null) {
            DatabaseReference userRef = FirebaseDatabase.getInstance()
                    .getReference("users")
                    .child(userId);

            userRef.get().addOnSuccessListener(snapshot -> {
                if (snapshot.exists()) {
                    String name = snapshot.child("name").getValue(String.class);
                    if (name != null && !name.isEmpty()) {

                        SpannableString styledTitle = new SpannableString(name);
                        styledTitle.setSpan(new StyleSpan(Typeface.BOLD), 0, name.length(), 0);
                        styledTitle.setSpan(new ForegroundColorSpan(Color.BLACK), 0, name.length(), 0);

                        popup.getMenu().findItem(R.id.nav_header).setTitle(styledTitle);
                    }
                }
            });
        }

        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            } else if (id == R.id.nav_logout) {
                FirebaseAuth.getInstance().signOut();

                getSharedPreferences("DTSLoginPrefs", MODE_PRIVATE)
                        .edit()
                        .clear()
                        .apply();

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

    private void listenDocumentsForMe() {
        String currentEmail = (mAuth.getCurrentUser() != null)
                ? mAuth.getCurrentUser().getEmail().trim().toLowerCase()
                : null;
        if (currentEmail == null) return;

        dbRef.child("documents").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                docList.clear();

                for (DataSnapshot docSnap : snapshot.getChildren()) {
                    String id = docSnap.getKey();
                    String name = docSnap.child("documentType").getValue(String.class);
                    String globalStatus = docSnap.child("status").getValue(String.class);
                    Long indexLong = docSnap.child("currentApproverIndex").getValue(Long.class);
                    int index = indexLong != null ? indexLong.intValue() : 0;

                    if (name == null || globalStatus == null) continue;

                    List<String> chain = new ArrayList<>();
                    for (DataSnapshot c : docSnap.child("approverChain").getChildren()) {
                        String val = c.getValue(String.class);
                        if (val != null) chain.add(val.trim().toLowerCase());
                    }
                    if (chain.isEmpty()) continue;

                    boolean isMyTurn = (index < chain.size()) && currentEmail.equals(chain.get(index));

                    long lastResubmissionTime = 0L;
                    DataSnapshot remarksAll = docSnap.child("remarks");
                    if (remarksAll.exists()) {
                        for (DataSnapshot remark : remarksAll.getChildren()) {
                            String key = remark.getKey();
                            if (key != null && key.startsWith("system_")) {
                                String status = remark.child("status").getValue(String.class);
                                if ("Resubmitted".equalsIgnoreCase(status)) {
                                    Long time = remark.child("time").getValue(Long.class);
                                    if (time != null && time > lastResubmissionTime)
                                        lastResubmissionTime = time;
                                }
                            }
                        }
                    }

                    boolean iHaveActed = false;
                    String myStatus = globalStatus;

                    DataSnapshot remarksSnap = docSnap.child("remarks");
                    if (remarksSnap.exists()) {
                        String normalizedEmailKey = currentEmail.replace("@", "_")
                                .replace(".", "_").toLowerCase();
                        for (DataSnapshot roleSnap : remarksSnap.getChildren()) {
                            String roleKey = roleSnap.getKey();
                            if (roleKey == null) continue;
                            if (roleKey.contains(normalizedEmailKey)) {
                                for (DataSnapshot timeSnap : roleSnap.getChildren()) {
                                    String remarkStatus = timeSnap.child("status").getValue(String.class);
                                    Long remarkTime = timeSnap.child("time").getValue(Long.class);
                                    if (remarkStatus == null) continue;

                                    if (remarkTime == null || remarkTime <= lastResubmissionTime) continue;

                                    if (remarkStatus.equalsIgnoreCase("Approved") ||
                                            remarkStatus.equalsIgnoreCase("Rejected")) {
                                        iHaveActed = true;
                                        myStatus = remarkStatus;
                                    }
                                }
                            }
                        }
                    }

                    int myPosition = chain.indexOf(currentEmail);

                    if (myPosition > index && !iHaveActed) continue;
                    if (!(isMyTurn || iHaveActed)) continue;
                    if (!iHaveActed && myPosition != index) continue;

                    boolean isGloballyRejected = "Rejected".equalsIgnoreCase(globalStatus);
                    if (isGloballyRejected && !iHaveActed)
                        continue;

                    String displayStatus;
                    if (iHaveActed) {
                        displayStatus = myStatus + " (You)";
                    } else if (isMyTurn && "Pending".equalsIgnoreCase(globalStatus)) {
                        displayStatus = "Pending (Your Turn)";
                    } else {
                        displayStatus = globalStatus;
                    }

                    docList.add(new RequestModel(id, name, displayStatus));
                }

                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void applyFilters() {
        if (docList.isEmpty()) return;

        filteredList.clear();
        String searchText = etSearch.getText() != null
                ? etSearch.getText().toString().trim().toLowerCase()
                : "";
        String statusFilter = getSelectedStatus(chipGroupFilter.getCheckedChipId());

        for (RequestModel doc : docList) {
            boolean matchesStatus = statusFilter.equals("All")
                    || doc.getStatus().equalsIgnoreCase(statusFilter)
                    || doc.getStatus().toLowerCase().contains(statusFilter.toLowerCase());
            boolean matchesSearch = searchText.isEmpty()
                    || doc.getName().toLowerCase().contains(searchText);

            if (matchesStatus && matchesSearch) filteredList.add(doc);
        }

        adapter.notifyDataSetChanged();
    }

    private String getSelectedStatus(int chipId) {
        if (chipId == R.id.chipPending) return "Pending";
        if (chipId == R.id.chipApproved) return "Approved";
        if (chipId == R.id.chipRejected) return "Rejected";
        return "All";
    }

    public void refreshDocuments() {
        docList.clear();
        adapter.notifyDataSetChanged();
        listenDocumentsForMe();
    }
}