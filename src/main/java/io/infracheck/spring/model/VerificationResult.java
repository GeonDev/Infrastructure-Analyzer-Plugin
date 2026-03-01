package io.infracheck.spring.model;

public class VerificationResult {
    private final String type; // "file", "api", "directory"
    private final String identifier; // path or URL
    private final boolean success;
    private final String errorMessage;
    private final boolean critical;

    private VerificationResult(String type, String identifier, boolean success, String errorMessage, boolean critical) {
        this.type = type;
        this.identifier = identifier;
        this.success = success;
        this.errorMessage = errorMessage;
        this.critical = critical;
    }

    public static VerificationResult success(String type, String identifier) {
        return new VerificationResult(type, identifier, true, null, false);
    }

    public static VerificationResult failure(String type, String identifier, String error, boolean critical) {
        return new VerificationResult(type, identifier, false, error, critical);
    }

    public String getType() { return type; }
    public String getIdentifier() { return identifier; }
    public boolean isSuccess() { return success; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isCritical() { return critical; }
}
