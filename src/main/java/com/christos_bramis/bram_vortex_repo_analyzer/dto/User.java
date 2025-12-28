package com.christos_bramis.bram_vortex_repo_analyzer.dto;

public class User {

    private Long id;              // Το ID στη δική σου βάση
    private String username;      // Το όνομα χρήστη (π.χ. από GitHub)
    private String email;         // Το email του
    private String avatarUrl;     // Η εικόνα προφίλ από το GitHub
    private String role;          // Ο ρόλος του (π.χ. "USER", "ADMIN")

    // 1. Κενός Constructor (Απαραίτητος για JSON/Jackson)
    public User() {
    }

    // 2. Full Constructor (Για εύκολη δημιουργία από εσένα)
    public User(Long id, String username, String email, String avatarUrl, String role) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.avatarUrl = avatarUrl;
        this.role = role;
    }

    // 3. Getters & Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    // Προαιρετικά: toString για debugging
    @Override
    public String toString() {
        return "UserDto{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", email='" + email + '\'' +
                '}';
    }
}