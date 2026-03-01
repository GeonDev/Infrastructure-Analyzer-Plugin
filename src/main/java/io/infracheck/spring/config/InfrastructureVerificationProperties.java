package io.infracheck.spring.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ConfigurationProperties(prefix = "infrastructure.verification")
public class InfrastructureVerificationProperties {
    private static final Logger log = LoggerFactory.getLogger(InfrastructureVerificationProperties.class);

    private boolean enabled = true;
    private Boolean failOnError; // null = auto-detect from profile
    private String requirementsPath = "classpath:infrastructure/requirements-{profile}.json";
    private int timeoutSeconds = 5;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getFailOnError() {
        return failOnError;
    }

    public void setFailOnError(Boolean failOnError) {
        this.failOnError = failOnError;
    }

    public String getRequirementsPath() {
        return requirementsPath;
    }

    public void setRequirementsPath(String requirementsPath) {
        this.requirementsPath = requirementsPath;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            log.error("Invalid timeout-seconds: {}, using default: 5", timeoutSeconds);
            this.timeoutSeconds = 5;
        } else {
            this.timeoutSeconds = timeoutSeconds;
        }
    }
}
