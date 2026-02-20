package io.infracheck.core.model;

/**
 * 쿠버네티스 PersistentVolumeClaim 검증 항목
 */
public class PvcCheck {
    private String name;
    private boolean critical;
    private String description;
    private String mountPath;

    public PvcCheck() {}

    public PvcCheck(String name, boolean critical, String description, String mountPath) {
        this.name = name;
        this.critical = critical;
        this.description = description;
        this.mountPath = mountPath;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getMountPath() { return mountPath; }
    public void setMountPath(String mountPath) { this.mountPath = mountPath; }
}
