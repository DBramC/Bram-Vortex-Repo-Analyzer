package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RepoResponse {

    private long id;

    private String name; // Αυτό είναι ΟΚ (ίδιο όνομα)

    @JsonProperty("html_url") // Το GitHub στέλνει "html_url"
    private String htmlUrl;

    private String language;

    @JsonProperty("private") // Το GitHub στέλνει "private", αλλά "private" είναι δεσμευμένη λέξη στη Java
    private boolean isPrivate;

    // Constructors
    public RepoResponse() {
    }

    public RepoResponse(long id, String name, String htmlUrl, String language, boolean isPrivate) {
        this.id = id;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.language = language;
        this.isPrivate = isPrivate;
    }

    // Getters & Setters
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getHtmlUrl() { return htmlUrl; }
    public void setHtmlUrl(String htmlUrl) { this.htmlUrl = htmlUrl; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }

    // Προσοχή: Στα boolean οι getters είναι συνήθως "is..."
    public boolean isPrivate() { return isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}