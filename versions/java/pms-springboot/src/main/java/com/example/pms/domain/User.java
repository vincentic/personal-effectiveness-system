package com.example.pms.domain;

/** 用户实体，对应共享表 users。 */
public class User {

    private final long id;
    private final String username;
    private final String passwordHash;
    private final String displayName;
    private final boolean staff;
    private final String createdDate;

    public User(long id, String username, String passwordHash, String displayName,
                boolean staff, String createdDate) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.staff = staff;
        this.createdDate = createdDate;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isStaff() {
        return staff;
    }

    public String getCreatedDate() {
        return createdDate;
    }
}
