package com.example.dts;

import android.content.Context;
import android.content.Intent;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.List;

public class RequestAdapter extends RecyclerView.Adapter<RequestAdapter.ViewHolder> {

    private final List<RequestModel> requests;
    private final boolean isStudentDashboard;

    private final DatabaseReference dbRef = FirebaseDatabase.getInstance().getReference();
    private final FirebaseAuth mAuth = FirebaseAuth.getInstance();

    // Constructor for Admin
    public RequestAdapter(List<RequestModel> requests) {
        this.requests = requests;
        this.isStudentDashboard = false;
    }

    // Constructor for Student
    public RequestAdapter(List<RequestModel> requests, boolean isStudentDashboard) {
        this.requests = requests;
        this.isStudentDashboard = isStudentDashboard;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RequestModel req = requests.get(position);
        Context ctx = holder.itemView.getContext();

        holder.tvName.setText(req.getName());
        holder.tvStatus.setText("Status: " + req.getStatus());

        // Hide comment tag permanently
        holder.tvCommentTag.setVisibility(View.GONE);

        // 🎨 Color-code by status
        switch (req.getStatus()) {
            case "Approved":
                holder.tvStatus.setTextColor(ctx.getColor(R.color.green_500));
                break;
            case "Rejected":
                holder.tvStatus.setTextColor(ctx.getColor(R.color.red_500));
                break;
            case "Pending":
                holder.tvStatus.setTextColor(ctx.getColor(R.color.orange_500));
                break;
            default:
                holder.tvStatus.setTextColor(ctx.getColor(R.color.gray_600));
                break;
        }

        // ---------- ADMIN DASHBOARD ----------
        if (!isStudentDashboard) {
            holder.tvStudent.setVisibility(View.VISIBLE);
            holder.tvSubmitted.setVisibility(View.VISIBLE);

            dbRef.child("documents").child(req.getId()).get().addOnSuccessListener(snap -> {
                if (!snap.exists()) return;

                String studentId = snap.child("studentId").getValue(String.class);
                Long createdAt = snap.child("createdAt").getValue(Long.class);

                // Submission time
                if (createdAt != null) {
                    String formattedTime = DateFormat.format("dd MMM yyyy, hh:mm a", createdAt).toString();
                    holder.tvSubmitted.setText("Submitted: " + formattedTime);
                } else {
                    holder.tvSubmitted.setText("");
                }

                // Fetch student name + userId
                if (studentId != null) {
                    dbRef.child("users").child(studentId).get()
                            .addOnSuccessListener(userSnap -> {
                                if (userSnap.exists()) {
                                    String studentName = userSnap.child("name").getValue(String.class);
                                    String studentUserId = userSnap.child("userId").getValue(String.class);

                                    if (studentName == null || studentName.isEmpty())
                                        studentName = "Unknown Student";

                                    if (studentUserId != null && !studentUserId.isEmpty()) {
                                        holder.tvStudent.setText(Html.fromHtml("From: <b>" + studentName + "</b> <font color='#6B7280'>(" + studentUserId + ")</font>"));
                                    } else {
                                        holder.tvStudent.setText("From: " + studentName);
                                    }
                                } else {
                                    holder.tvStudent.setText("From: Unknown Student");
                                }
                            });
                } else {
                    holder.tvStudent.setText("From: Unknown Student");
                }

                // ✅ Get per-approver status (ignore comments entirely)
                String currentEmail = (mAuth.getCurrentUser() != null)
                        ? mAuth.getCurrentUser().getEmail()
                        : null;

                if (currentEmail != null) {
                    String emailKey = currentEmail.replace("@", "_").replace(".", "_").toLowerCase();
                    String approverStatus = snap.child("approvalStatus").child(emailKey).getValue(String.class);

                    if (approverStatus != null) {
                        holder.tvStatus.setText("Status: " + approverStatus);
                        if (approverStatus.equalsIgnoreCase("Approved")) {
                            holder.tvStatus.setTextColor(ctx.getColor(R.color.green_500));
                        } else if (approverStatus.equalsIgnoreCase("Rejected")) {
                            holder.tvStatus.setTextColor(ctx.getColor(R.color.red_500));
                        } else if (approverStatus.equalsIgnoreCase("Pending")) {
                            holder.tvStatus.setTextColor(ctx.getColor(R.color.orange_500));
                        }
                    }
                }
            });

            // ---------- Handle admin item click ----------
            holder.itemView.setOnClickListener(v -> {
                String currentUserEmail = (mAuth.getCurrentUser() != null)
                        ? mAuth.getCurrentUser().getEmail()
                        : null;
                if (currentUserEmail == null) return;

                dbRef.child("documents").child(req.getId()).get().addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) return;

                    List<String> chain = new ArrayList<>();
                    for (DataSnapshot c : snapshot.child("approverChain").getChildren()) {
                        String val = c.getValue(String.class);
                        if (val != null) chain.add(val.trim().toLowerCase());
                    }

                    Long indexLong = snapshot.child("currentApproverIndex").getValue(Long.class);
                    int index = (indexLong != null) ? indexLong.intValue() : 0;

                    // ✅ Check if this admin is the current approver
                    if (index < chain.size() &&
                            currentUserEmail.trim().toLowerCase().equals(chain.get(index))) {

                        String currentEmailKey = currentUserEmail.replace("@", "_").replace(".", "_").toLowerCase();
                        String approverStatus = snapshot.child("approvalStatus").child(currentEmailKey).getValue(String.class);

                        // Allow action only if pending
                        if (approverStatus != null && approverStatus.equalsIgnoreCase("Pending")) {
                            AdminActionDialog dialog = new AdminActionDialog(
                                    req.getId(),
                                    req.getName(),
                                    req.getStatus()
                            );
                            dialog.show(((AppCompatActivity) v.getContext()).getSupportFragmentManager(),
                                    "AdminActionDialog");
                        } else {
                            Toast.makeText(v.getContext(),
                                    "You’ve already taken action on this request.",
                                    Toast.LENGTH_SHORT).show();
                        }

                    } else {
                        Toast.makeText(v.getContext(),
                                "You’re not the current approver for this request.",
                                Toast.LENGTH_SHORT).show();
                    }
                });
            });

        }

        // ---------- STUDENT DASHBOARD ----------
        else {
            holder.tvStudent.setVisibility(View.GONE);
            holder.tvSubmitted.setVisibility(View.GONE);

            holder.itemView.setOnClickListener(v -> {
                Context context = v.getContext();
                Intent intent = new Intent(context, StudentDetailActivity.class);
                intent.putExtra("docId", req.getId());
                intent.putExtra("docName", req.getName());
                intent.putExtra("docStatus", req.getStatus());
                context.startActivity(intent);
            });
        }
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvStudent, tvSubmitted, tvCommentTag;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvName);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvStudent = itemView.findViewById(R.id.tvStudent);
            tvSubmitted = itemView.findViewById(R.id.tvSubmitted);
            tvCommentTag = itemView.findViewById(R.id.tvCommentTag);

            // 💬 Hide comment tag by default (no comment display anywhere)
            if (tvCommentTag != null) tvCommentTag.setVisibility(View.GONE);
        }
    }
}