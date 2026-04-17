package com.christos_bramis.bram_vortex_repo_analyzer.dto;

import java.util.List;

public class FileDiffResponse {
    private String jobId;
    private List<FileDiff> files;

    public FileDiffResponse() {}

    public FileDiffResponse(String jobId, List<FileDiff> files) {
        this.jobId = jobId;
        this.files = files;
    }

    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public List<FileDiff> getFiles() { return files; }
    public void setFiles(List<FileDiff> files) { this.files = files; }

    public static class FileDiff {
        private String filename;
        private String language;
        private String draftContent;
        private String validatedContent;

        public FileDiff() {}

        public FileDiff(String filename, String language, String draftContent, String validatedContent) {
            this.filename = filename;
            this.language = language;
            this.draftContent = draftContent;
            this.validatedContent = validatedContent;
        }

        public String getFilename() { return filename; }
        public void setFilename(String filename) { this.filename = filename; }
        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
        public String getDraftContent() { return draftContent; }
        public void setDraftContent(String draftContent) { this.draftContent = draftContent; }
        public String getValidatedContent() { return validatedContent; }
        public void setValidatedContent(String validatedContent) { this.validatedContent = validatedContent; }
    }
}