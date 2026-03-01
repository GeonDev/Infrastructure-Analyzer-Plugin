package io.infracheck.core.model;

/**
 * VM 환경 디렉토리 권한 검증 스펙
 */
public class DirectoryCheck {
    private String path;
    private String permissions;  // "r", "w", "x" 조합 (예: "rwx", "rw")
    private boolean critical;
    private String description;

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getPermissions() { return permissions; }
    public void setPermissions(String permissions) { this.permissions = permissions; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
