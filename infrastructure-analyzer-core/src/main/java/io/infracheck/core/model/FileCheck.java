package io.infracheck.core.model;

/**
 * NAS/로컬 파일 검증 항목
 */
public class FileCheck {
    private String path;
    private String location; // "nas", "local", "unknown"
    private boolean critical;
    private String description;

    public FileCheck() {}

    public FileCheck(String path, String location, boolean critical, String description) {
        this.path = path;
        this.location = location;
        this.critical = critical;
        this.description = description;
    }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
