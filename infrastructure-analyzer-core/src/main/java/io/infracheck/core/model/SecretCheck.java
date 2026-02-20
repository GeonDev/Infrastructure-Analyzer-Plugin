package io.infracheck.core.model;

/**
 * 쿠버네티스 Secret 검증 항목
 */
public class SecretCheck {
    private String name;
    private boolean critical;
    private String description;

    public SecretCheck() {}

    public SecretCheck(String name, boolean critical, String description) {
        this.name = name;
        this.critical = critical;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
