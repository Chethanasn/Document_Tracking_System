package com.example.dts;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminActionDialog extends DialogFragment {

    private final String docId;
    private final String docName;
    private final String docStatus;

    private DatabaseReference dbRef;
    private FirebaseAuth mAuth;

    public AdminActionDialog(String docId, String docName, String docStatus) {
        this.docId = docId;
        this.docName = docName;
        this.docStatus = docStatus;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        dbRef = FirebaseDatabase.getInstance().getReference();
        mAuth = FirebaseAuth.getInstance();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_admin_action, null);

        TextView tvDialogTitle = view.findViewById(R.id.tvDialogTitle);
        TextView tvDocInfo = view.findViewById(R.id.tvDocInfo);
        EditText etComment = view.findViewById(R.id.etComment);
        MaterialButton btnApprove = view.findViewById(R.id.btnApprove);
        MaterialButton btnReject = view.findViewById(R.id.btnReject);
        MaterialButton btnComment = view.findViewById(R.id.btnComment);
        MaterialButton btnCancel = view.findViewById(R.id.btnCancel);

        ProgressBar progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        progressBar.setIndeterminate(true);
        ((ViewGroup) view).addView(progressBar);
        progressBar.setTranslationZ(100f);
        progressBar.setScaleX(1.3f);
        progressBar.setScaleY(1.3f);

        tvDialogTitle.setText("Action for " + docName);
        tvDocInfo.setText("Review this document and take necessary action.");

        btnApprove.setOnClickListener(v -> {
            toggleLoading(true, progressBar, btnApprove, btnReject, btnComment, btnCancel);
            handleAction("Approved", etComment.getText().toString().trim(), progressBar, btnApprove, btnReject, btnComment, btnCancel);
        });

        btnReject.setOnClickListener(v -> {
            toggleLoading(true, progressBar, btnApprove, btnReject, btnComment, btnCancel);
            handleAction("Rejected", etComment.getText().toString().trim(), progressBar, btnApprove, btnReject, btnComment, btnCancel);
        });

        btnComment.setOnClickListener(v -> {
            String comment = etComment.getText().toString().trim();
            if (comment.isEmpty()) {
                Context safeContext = getActivity();
                if (safeContext != null)
                    Toast.makeText(safeContext, "Enter a comment to add.", Toast.LENGTH_SHORT).show();
                return;
            }
            toggleLoading(true, progressBar, btnApprove, btnReject, btnComment, btnCancel);
            handleAction("Commented", comment, progressBar, btnApprove, btnReject, btnComment, btnCancel);
        });

        btnCancel.setOnClickListener(v -> dismiss());

        builder.setView(view);
        return builder.create();
    }

    private void handleAction(String action, String remark, ProgressBar progressBar,
                              MaterialButton... buttons) {
        String adminEmail = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getEmail() : null;
        Context safeContext = getActivity();
        if (adminEmail == null) {
            if (safeContext != null)
                Toast.makeText(safeContext, "Not logged in", Toast.LENGTH_SHORT).show();
            toggleLoading(false, progressBar, buttons);
            return;
        }

        String adminKey = adminEmail.replace("@", "_").replace(".", "_").toLowerCase();

        dbRef.child("documents").child(docId).get().addOnSuccessListener(snapshot -> {
            if (!snapshot.exists()) {
                toggleLoading(false, progressBar, buttons);
                return;
            }

            Long index = snapshot.child("currentApproverIndex").getValue(Long.class);
            if (index == null) index = 0L;

            List<String> approverChain = new ArrayList<>();
            for (DataSnapshot a : snapshot.child("approverChain").getChildren()) {
                String value = a.getValue(String.class);
                if (value != null) approverChain.add(value);
            }

            if (approverChain.isEmpty()) {
                if (safeContext != null)
                    Toast.makeText(safeContext, "Invalid document data", Toast.LENGTH_SHORT).show();
                toggleLoading(false, progressBar, buttons);
                return;
            }

            long timestamp = System.currentTimeMillis();
            String formattedTime = android.text.format.DateFormat.format("dd MMM yyyy, hh:mm a", timestamp).toString();

            Map<String, Object> updates = new HashMap<>();

            String remarksPath = "documents/" + docId + "/remarks/" + adminKey + "/" + timestamp;
            Map<String, Object> remarkData = new HashMap<>();
            remarkData.put("status", action);
            remarkData.put("comment", remark);
            remarkData.put("time", timestamp);
            updates.put(remarksPath, remarkData);

            if (!"Commented".equals(action)) {
                updates.put("documents/" + docId + "/approvalStatus/" + adminKey, action);
            }

            if ("Approved".equals(action)) {
                if (index + 1 < approverChain.size()) {
                    updates.put("documents/" + docId + "/currentApproverIndex", index + 1);
                    updates.put("documents/" + docId + "/status", "Pending");
                    updates.put("documents/" + docId + "/overallStatus", "Pending");
                } else {
                    updates.put("documents/" + docId + "/status", "Approved");
                    updates.put("documents/" + docId + "/overallStatus", "Approved");
                }
            } else if ("Rejected".equals(action)) {
                updates.put("documents/" + docId + "/status", "Rejected");
                updates.put("documents/" + docId + "/overallStatus", "Rejected");
            } else if ("Commented".equals(action)) {
                updates.put("documents/" + docId + "/status", "Pending");
                updates.put("documents/" + docId + "/overallStatus", "Pending");
            }

            updates.put("documents/" + docId + "/updatedAt", timestamp);

            dbRef.updateChildren(updates)
                    .addOnSuccessListener(unused -> {
                        toggleLoading(false, progressBar, buttons);
                        if (safeContext != null)
                            Toast.makeText(
                                    safeContext,
                                    "Action saved: " + action + " at " + formattedTime,
                                    Toast.LENGTH_SHORT
                            ).show();

                        if (getActivity() instanceof AdminDashboardActivity) {
                            ((AdminDashboardActivity) getActivity()).refreshDocuments();
                        }

                        dismissAllowingStateLoss();
                    })
                    .addOnFailureListener(e -> {
                        toggleLoading(false, progressBar, buttons);
                        if (safeContext != null)
                            Toast.makeText(
                                    safeContext,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT
                            ).show();
                    });

        }).addOnFailureListener(e -> {
            toggleLoading(false, progressBar, buttons);
            if (safeContext != null)
                Toast.makeText(safeContext, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void toggleLoading(boolean show, ProgressBar progressBar, MaterialButton... buttons) {
        if (progressBar != null)
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        for (MaterialButton b : buttons)
            b.setEnabled(!show);
    }
}