package com.example.dts;

import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StudentDetailActivity extends AppCompatActivity {

    private TextView tvDocName, tvDocStatus, tvSubmittedOn, btnBack;
    private MaterialButton btnSubmit, btnLogout;
    private LinearLayout timelineContainer;

    private String docId, docName, docStatus;

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;
    private TextView tvApprovalFlow, tvCurrentApprover;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_student_detail);

        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        tvDocName = findViewById(R.id.tvDocName);
        tvDocStatus = findViewById(R.id.tvDocStatus);
        tvSubmittedOn = findViewById(R.id.tvSubmittedOn);
        btnSubmit = findViewById(R.id.btnSubmit);
        timelineContainer = findViewById(R.id.timelineContainer);
        btnBack = findViewById(R.id.btnBack);
        btnLogout = findViewById(R.id.btnLogout);
        tvApprovalFlow = findViewById(R.id.tvApprovalFlow);
        tvCurrentApprover = findViewById(R.id.tvCurrentApprover);

        docId = getIntent().getStringExtra("docId");
        docName = getIntent().getStringExtra("docName");
        docStatus = getIntent().getStringExtra("docStatus");

        tvDocName.setText(docName);
        updateStatusUI(docStatus);

        btnBack.setOnClickListener(v -> onBackPressed());

        btnLogout.setOnClickListener(v -> {
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
        });

        btnSubmit.setOnClickListener(v -> {
            if ("Not Submitted".equals(docStatus)) handleSubmitOrResubmit("Submit");
            else if ("Rejected".equals(docStatus)) handleSubmitOrResubmit("Resubmit");
        });

        listenForUpdates();
    }

    /** ✅ Update visual status badge + button */
    private void updateStatusUI(String status) {
        switch (status) {
            case "Not Submitted":
                tvDocStatus.setBackgroundResource(R.drawable.bg_status_not_submitted);
                tvDocStatus.setText("Status: Not Submitted");
                btnSubmit.setText("Submit Now");
                btnSubmit.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dts_blue));
                btnSubmit.setVisibility(View.VISIBLE);
                break;
            case "Pending":
                tvDocStatus.setBackgroundResource(R.drawable.bg_status_pending);
                tvDocStatus.setText("Status: Pending");
                btnSubmit.setVisibility(View.GONE);
                break;
            case "Rejected":
                tvDocStatus.setBackgroundResource(R.drawable.bg_status_rejected);
                tvDocStatus.setText("Status: Rejected");
                btnSubmit.setText("Resubmit Document");
                btnSubmit.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.red_500));
                btnSubmit.setVisibility(View.VISIBLE);
                break;
            case "Approved":
                tvDocStatus.setBackgroundResource(R.drawable.bg_status_approved);
                tvDocStatus.setText("Status: Approved");
                btnSubmit.setVisibility(View.GONE);
                break;
        }
    }

    /** ✅ Handles new submission and resubmission (creates NEW doc on resubmit) */
    /** ✅ Handles new submission and resubmission */
    private void handleSubmitOrResubmit(String action) {
        String studentId = mAuth.getUid();
        if (studentId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            return;
        }

        dbRef.child("flows").child(docName).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                Toast.makeText(this, "No approver chain found for " + docName, Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> approverChain = new ArrayList<>();
            for (DataSnapshot child : snapshot.getChildren())
                approverChain.add(child.getValue(String.class));

            long now = System.currentTimeMillis();
            String formattedTime = android.text.format.DateFormat.format("hh:mm a, dd MMM yyyy", now).toString();

            // ✅ Check if document already exists
            dbRef.child("documents").orderByChild("studentId").equalTo(studentId)
                    .get().addOnSuccessListener(docsSnap -> {
                        String existingDocId = null;
                        for (DataSnapshot d : docsSnap.getChildren()) {
                            String type = d.child("documentType").getValue(String.class);
                            if (type != null && type.equals(docName)) {
                                existingDocId = d.getKey();
                                break;
                            }
                        }

                        if (existingDocId != null) {
                            // ✅ Update existing document (Resubmit)
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("documents/" + existingDocId + "/status", "Pending");
                            updates.put("documents/" + existingDocId + "/overallStatus", "Pending");
                            updates.put("documents/" + existingDocId + "/currentApproverIndex", 0);
                            updates.put("documents/" + existingDocId + "/updatedAt", now);

                            // 🔹 Reset approval statuses for all approvers
                            for (String email : approverChain) {
                                String approverKey = email.replace("@", "_").replace(".", "_").toLowerCase();
                                updates.put("documents/" + existingDocId + "/approvalStatus/" + approverKey, "Pending");
                            }

                            // ✅ Add resubmission remark (keep all previous remarks)
                            String remarkKey = "system_" + now;
                            Map<String, Object> remark = new HashMap<>();
                            remark.put("status", "Resubmitted");
                            remark.put("comment", "Resubmitted on " + formattedTime);
                            remark.put("time", now);
                            updates.put("documents/" + existingDocId + "/remarks/" + remarkKey, remark);

                            dbRef.updateChildren(updates)
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(this, "Resubmitted successfully", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());

                        } else {
                            // ✅ Create new document for first submission
                            String newDocId = dbRef.child("documents").push().getKey();

                            Map<String, Object> docData = new HashMap<>();
                            docData.put("studentId", studentId);
                            docData.put("documentType", docName);
                            docData.put("status", "Pending");
                            docData.put("overallStatus", "Pending");
                            docData.put("approverChain", approverChain);
                            docData.put("currentApproverIndex", 0);
                            docData.put("createdAt", now);
                            docData.put("updatedAt", now);

                            Map<String, Object> approvalStatus = new HashMap<>();
                            for (String email : approverChain) {
                                approvalStatus.put(email.replace("@", "_").replace(".", "_").toLowerCase(), "Pending");
                            }
                            docData.put("approvalStatus", approvalStatus);

                            Map<String, Object> remarks = new HashMap<>();
                            Map<String, Object> initial = new HashMap<>();
                            initial.put("status", "Submitted");
                            initial.put("comment", "Submitted on " + formattedTime);
                            initial.put("time", now);
                            remarks.put("system_" + now, initial);
                            docData.put("remarks", remarks);

                            dbRef.child("documents").child(newDocId).setValue(docData)
                                    .addOnSuccessListener(unused -> {
                                        Toast.makeText(this, "Submitted successfully", Toast.LENGTH_SHORT).show();
                                        finish();
                                    })
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
        });
    }

    /** ✅ Realtime listener for live updates + remarks timeline */
    private void listenForUpdates() {
        dbRef.child("documents").orderByChild("studentId").equalTo(mAuth.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        for (DataSnapshot docSnap : snapshot.getChildren()) {
                            String type = docSnap.child("documentType").getValue(String.class);
                            if (type == null || !type.equals(docName)) continue;

                            String status = docSnap.child("status").getValue(String.class);
                            if (status != null) {
                                docStatus = status;
                                updateStatusUI(docStatus);
                            }

                            Long createdAt = docSnap.child("createdAt").getValue(Long.class);
                            if (createdAt != null) {
                                String formattedCreated = android.text.format.DateFormat.format("hh:mm a, dd MMM yyyy", createdAt).toString();
                                tvSubmittedOn.setText("Submitted On: " + formattedCreated);
                            }

                            // 🧩 Fetch and display approval chain
                            List<String> chain = new ArrayList<>();
                            for (DataSnapshot approver : docSnap.child("approverChain").getChildren()) {
                                String email = approver.getValue(String.class);
                                if (email != null)
                                    chain.add(getReadableRole(email.replace("@", "_").replace(".", "_")));
                            }

                            if (!chain.isEmpty()) {
                                StringBuilder chainDisplay = new StringBuilder("Approval Flow: ");
                                for (int i = 0; i < chain.size(); i++) {
                                    chainDisplay.append(chain.get(i));
                                    if (i < chain.size() - 1) chainDisplay.append(" → ");
                                }
                                tvApprovalFlow.setText(chainDisplay.toString());
                                tvApprovalFlow.setVisibility(View.VISIBLE);
                            }

                            // 🧩 Show pending/approved/rejected stage
                            Long currentIndex = docSnap.child("currentApproverIndex").getValue(Long.class);
                            String overallStatus = docSnap.child("status").getValue(String.class);

                            if ("Approved".equalsIgnoreCase(overallStatus)) {
                                tvCurrentApprover.setText("Approved ✅");
                                tvCurrentApprover.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.green_500));
                                tvCurrentApprover.setVisibility(View.VISIBLE);
                            } else if ("Rejected".equalsIgnoreCase(overallStatus)) {
                                tvCurrentApprover.setText("Rejected ❌");
                                tvCurrentApprover.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.red_500));
                                tvCurrentApprover.setVisibility(View.VISIBLE);
                            } else if (currentIndex != null && currentIndex < chain.size()) {
                                tvCurrentApprover.setText("(Pending with: " + chain.get(currentIndex.intValue()) + ")");
                                tvCurrentApprover.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.orange_500));
                                tvCurrentApprover.setVisibility(View.VISIBLE);

                                // 🔸 Update status label also to show where it’s pending
                                tvDocStatus.setText("Status: Pending with " + chain.get(currentIndex.intValue()));
                            }

                            // 🧾 Remarks Section
                            timelineContainer.removeAllViews();
                            DataSnapshot remarksSnap = docSnap.child("remarks");
                            if (!remarksSnap.exists()) {
                                TextView tv = new TextView(StudentDetailActivity.this);
                                tv.setText("No remarks yet. Submit the document to start the process.");
                                tv.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.gray_500));
                                tv.setPadding(16, 12, 16, 12);
                                timelineContainer.addView(tv);
                                continue;
                            }

                            List<DataSnapshot> remarks = new ArrayList<>();

                            for (DataSnapshot roleSnap : remarksSnap.getChildren()) {
                                String roleKey = roleSnap.getKey();

                                // ✅ System remarks (Submitted / Resubmitted)
                                if (roleKey != null && roleKey.startsWith("system_")) {
                                    remarks.add(roleSnap);
                                    continue;
                                }

                                // ✅ Role-based remarks (Faculty, HOD, etc.)
                                for (DataSnapshot timeSnap : roleSnap.getChildren()) {
                                    timeSnap.getRef().getKey(); // no DB write
                                    remarks.add(timeSnap);
                                }
                            }

                            Collections.sort(remarks, (a, b) -> {
                                long t1 = a.child("time").getValue(Long.class) != null
                                        ? a.child("time").getValue(Long.class) : 0L;
                                long t2 = b.child("time").getValue(Long.class) != null
                                        ? b.child("time").getValue(Long.class) : 0L;
                                return Long.compare(t1, t2);
                            });

                            for (DataSnapshot remarkSnap : remarks) {
                                String roleKey = null;
                                try {
                                    roleKey = remarkSnap.getRef().getParent().getKey();
                                } catch (Exception ignored) {}

                                if (roleKey == null)
                                    roleKey = remarkSnap.getKey(); // fallback for system remarks

                                String remarkStatus = remarkSnap.child("status").getValue(String.class);
                                String comment = remarkSnap.child("comment").getValue(String.class);
                                Long time = remarkSnap.child("time").getValue(Long.class);
                                String formattedTime = (time != null)
                                        ? android.text.format.DateFormat.format("hh:mm a, dd MMM yyyy", time).toString()
                                        : "-";

                                boolean isSystemRemark = roleKey != null && roleKey.startsWith("system_");

                                LinearLayout bubble = new LinearLayout(StudentDetailActivity.this);
                                bubble.setOrientation(LinearLayout.VERTICAL);
                                bubble.setBackground(ContextCompat.getDrawable(StudentDetailActivity.this, R.drawable.bg_timeline_bubble));
                                bubble.setPadding(24, 20, 24, 20);
                                LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(
                                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                                bubbleLp.setMargins(0, 12, 0, 0);
                                bubble.setLayoutParams(bubbleLp);

                                if (isSystemRemark) {
                                    TextView tvSys = new TextView(StudentDetailActivity.this);
                                    tvSys.setText(comment);
                                    tvSys.setTextSize(13);
                                    tvSys.setTypeface(null, Typeface.ITALIC);
                                    tvSys.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.gray_500));
                                    bubble.addView(tvSys);
                                } else {
                                    String role = getReadableRole(roleKey);
                                    TextView tvHeader = new TextView(StudentDetailActivity.this);

                                    String actionLabel = "";
                                    if (remarkStatus != null) {
                                        if (remarkStatus.equalsIgnoreCase("Approved"))
                                            actionLabel = " (Approved)";
                                        else if (remarkStatus.equalsIgnoreCase("Rejected"))
                                            actionLabel = " (Rejected)";
                                        else if (remarkStatus.equalsIgnoreCase("Commented"))
                                            actionLabel = " (Commented)";
                                    }

                                    tvHeader.setText(role + actionLabel + "    " + formattedTime);
                                    tvHeader.setTextSize(14);
                                    tvHeader.setTypeface(null, Typeface.BOLD);

                                    if ("Approved".equalsIgnoreCase(remarkStatus))
                                        tvHeader.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.green_500));
                                    else if ("Rejected".equalsIgnoreCase(remarkStatus))
                                        tvHeader.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.red_500));
                                    else
                                        tvHeader.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.gray_700));

                                    bubble.addView(tvHeader);

                                    if (comment != null && !comment.isEmpty()) {
                                        TextView tvComment = new TextView(StudentDetailActivity.this);
                                        tvComment.setText("\"" + comment + "\"");
                                        tvComment.setTextColor(ContextCompat.getColor(StudentDetailActivity.this, R.color.gray_600));
                                        tvComment.setTextSize(14);
                                        tvComment.setPadding(0, 8, 0, 0);
                                        bubble.addView(tvComment);
                                    }
                                }

                                timelineContainer.addView(bubble);
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private String getReadableRole(String roleKey) {
        if (roleKey == null) return "-";
        switch (roleKey.toLowerCase()) {
            case "faculty_dts_com": return "Faculty";
            case "dtshod_dts_com": return "HOD";
            case "accounts_dts_com": return "Accounts";
            case "hostelwarden_dts_com": return "Hostel Warden";
            case "messsupervisor_dts_com": return "Mess Supervisor";
            case "librarian_dts_com": return "Librarian";
            case "placement_dts_com": return "Placement Cell";
            case "academic_dts_com": return "Academic Section";
            case "scholarship_dts_com": return "Scholarship Officer";
            default:
                String[] parts = roleKey.split("_");
                StringBuilder sb = new StringBuilder();
                for (String p : parts) {
                    if (!p.equals("dts") && !p.equals("com") && !p.isEmpty()) {
                        sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(" ");
                    }
                }
                return sb.toString().trim();
        }
    }
}