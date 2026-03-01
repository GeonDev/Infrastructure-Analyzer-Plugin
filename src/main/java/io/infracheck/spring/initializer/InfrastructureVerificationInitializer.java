package io.infracheck.spring.initializer;

import io.infracheck.core.model.Requirements;
import io.infracheck.spring.config.InfrastructureVerificationProperties;
import io.infracheck.spring.loader.RequirementsLoader;
import io.infracheck.spring.model.VerificationResult;
import io.infracheck.spring.verifier.ApiVerifier;
import io.infracheck.spring.verifier.DirectoryVerifier;
import io.infracheck.spring.verifier.FileVerifier;
import io.infracheck.spring.verifier.Verifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.boot.context.properties.bind.Binder;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class InfrastructureVerificationInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private static final Logger log = LoggerFactory.getLogger(InfrastructureVerificationInitializer.class);

    @Override
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment env = context.getEnvironment();
        InfrastructureVerificationProperties properties = Binder.get(env)
                .bind("infrastructure.verification", InfrastructureVerificationProperties.class)
                .orElse(new InfrastructureVerificationProperties());

        if (!properties.isEnabled()) {
            log.info("Infrastructure verification is disabled");
            return;
        }

        String profile = getActiveProfile(env);
        RequirementsLoader loader = new RequirementsLoader();
        Requirements requirements = loader.load(profile, properties.getRequirementsPath());

        if (requirements == null) {
            log.warn("No requirements specification found, skipping verification");
            return;
        }

        log.info("Starting infrastructure verification (profile: {}, path: {})", 
                profile, properties.getRequirementsPath().replace("{profile}", profile));

        long startTime = System.currentTimeMillis();

        List<Verifier> verifiers = createVerifiers(properties);
        List<VerificationResult> results = new ArrayList<>();
        for (Verifier verifier : verifiers) {
            results.addAll(verifier.verify(requirements));
        }

        long duration = System.currentTimeMillis() - startTime;
        boolean strictMode = determineStrictMode(profile, properties);
        
        handleResults(results, strictMode, duration);
    }

    private String getActiveProfile(ConfigurableEnvironment env) {
        String[] profiles = env.getActiveProfiles();
        if (profiles.length > 0) {
            return profiles[0];
        }
        return "default";
    }

    private List<Verifier> createVerifiers(InfrastructureVerificationProperties properties) {
        List<Verifier> verifiers = new ArrayList<>();
        verifiers.add(new FileVerifier());
        verifiers.add(new ApiVerifier(properties.getTimeoutSeconds()));
        verifiers.add(new DirectoryVerifier());
        return verifiers;
    }

    private boolean determineStrictMode(String profile, InfrastructureVerificationProperties props) {
        if (props.getFailOnError() != null) {
            return props.getFailOnError();
        }
        // Strict mode for prod, lenient for others (Requirement 7.5, 7.6)
        return "prod".equals(profile);
    }

    private void handleResults(List<VerificationResult> results, boolean strictMode, long duration) {
        long total = results.size();
        List<VerificationResult> failures = results.stream()
                .filter(r -> !r.isSuccess())
                .collect(Collectors.toList());
        long failedCount = failures.size();
        long passedCount = total - failedCount;

        if (failedCount == 0) {
            log.info("Infrastructure verification completed successfully. Passed: {}/{} ({}ms)", 
                    passedCount, total, duration);
            return;
        }

        String level = strictMode ? "ERROR" : "WARN";
        log.info("Infrastructure verification completed with failures. Passed: {}, Failed: {}/{} ({}ms)", 
                passedCount, failedCount, total, duration);

        for (VerificationResult failure : failures) {
            String msg = String.format("[%s] %s: %s", failure.getType().toUpperCase(), failure.getIdentifier(), failure.getErrorMessage());
            if (strictMode) {
                log.error(msg);
            } else {
                log.warn(msg);
            }
        }

        if (strictMode) {
            log.error("Application startup failed due to infrastructure verification errors in strict mode.");
            System.exit(1);
        }
    }
}
