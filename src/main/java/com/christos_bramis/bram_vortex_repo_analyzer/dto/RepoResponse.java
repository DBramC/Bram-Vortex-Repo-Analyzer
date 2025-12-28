package com.christos_bramis.bram_vortex_repo_analyzer.dto;

public class RepoResponse {

    private long id;
    private String name;
    private String htmlUrl;
    private String language;
    private boolean isPrivate;

    // 1. Απαραίτητος Κενός Constructor (για το JSON deserialization)
    public RepoResponse() {
    }

    // 2. Constructor με όλα τα πεδία (για να το φτιάχνεις εσύ εύκολα)
    public RepoResponse(long id, String name, String htmlUrl, String language, boolean isPrivate) {
        this.id = id;
        this.name = name;
        this.htmlUrl = htmlUrl;
        this.language = language;
        this.isPrivate = isPrivate;
    }

    // 3. Getters και Setters
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHtmlUrl() {
        return htmlUrl;
    }

    public void setHtmlUrl(String htmlUrl) {
        this.htmlUrl = htmlUrl;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean isPrivate) {
        this.isPrivate = isPrivate;
    }
}