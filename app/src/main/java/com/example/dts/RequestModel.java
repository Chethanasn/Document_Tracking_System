package com.example.dts;

public class RequestModel {
    private String id;
    private String name;          // documentType
    private String status;
    private String studentName;   // NEW
    private long createdAt;       // NEW (epoch millis)
    private boolean canAct;       // NEW (admin is current approver & status Pending)

    public RequestModel() { }

    // For student list (no extra fields)
    public RequestModel(String id, String name, String status) {
        this(id, name, status, "", 0L, false);
    }

    // For admin list (full)
    public RequestModel(String id, String name, String status,
                        String studentName, long createdAt, boolean canAct) {
        this.id = id;
        this.name = name;
        this.status = status;
        this.studentName = studentName;
        this.createdAt = createdAt;
        this.canAct = canAct;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getStatus() { return status; }
    public String getStudentName() { return studentName; }
    public long getCreatedAt() { return createdAt; }
    public boolean isCanAct() { return canAct; }

    public void setStatus(String status) { this.status = status; }
    public void setStudentName(String studentName) { this.studentName = studentName; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    public void setCanAct(boolean canAct) { this.canAct = canAct; }
}
