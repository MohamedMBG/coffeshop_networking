package com.example.loyaltyapp.models;

public class User {
    private String uid;
    private String email;
    private String fullName;
    private String birthday;
    private String gender;
    private int points;
    private int visits;
    // P0 security: isVerified is read-only from the client's perspective.
    // It represents email/account verification and is set only by the
    // backend (after /api/verify succeeds) or by Firestore security rules.
    // The client must not call setVerified() to persist a true value.
    private boolean isVerified;

    // Client-owned flag indicating the user has filled out their profile
    // (name, birthday, gender, phone, address). Drives in-app routing only.
    // Decoupled from isVerified so that filling the profile does not grant
    // the user a "verified" trust claim.
    private boolean profileComplete;

    private String phone;
    private String address;

    // Required empty constructor for Firestore
    public User() {}

    public User(String uid, String email, String fullName, String birthday, String gender,
                int points, int visits, boolean isVerified) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.birthday = birthday;
        this.gender = gender;
        this.points = points;
        this.visits = visits;
        this.isVerified = isVerified;
    }

    public User(String uid, String email, String fullName, String birthday, String gender,
                int points, int visits, boolean isVerified, String phone, String address) {
        this.uid = uid;
        this.email = email;
        this.fullName = fullName;
        this.birthday = birthday;
        this.gender = gender;
        this.points = points;
        this.visits = visits;
        this.isVerified = isVerified;
        this.phone = phone;
        this.address = address;
    }

    // Getters and setters
    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getBirthday() { return birthday; }
    public void setBirthday(String birthday) { this.birthday = birthday; }

    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public int getVisits() { return visits; }
    public void setVisits(int visits) { this.visits = visits; }

    public boolean isVerified() { return isVerified; }
    public void setVerified(boolean verified) { isVerified = verified; }

    public boolean isProfileComplete() { return profileComplete; }
    public void setProfileComplete(boolean profileComplete) { this.profileComplete = profileComplete; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}