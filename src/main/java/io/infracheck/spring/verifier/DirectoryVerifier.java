package io.infracheck.spring.verifier;

import io.infracheck.core.model.DirectoryCheck;
import io.infracheck.core.model.Requirements;
import io.infracheck.spring.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DirectoryVerifier implements Verifier {
    private static final Logger log = LoggerFactory.getLogger(DirectoryVerifier.class);

    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        if (requirements.getInfrastructure() == null || requirements.getInfrastructure().getDirectories() == null) {
            return results;
        }

        for (DirectoryCheck dirCheck : requirements.getInfrastructure().getDirectories()) {
            String pathStr = dirCheck.getPath();
            log.debug("Verifying directory: {}", pathStr);

            try {
                Path path = Paths.get(pathStr);
                if (!Files.exists(path)) {
                    log.warn("Directory not found: {}", pathStr);
                    results.add(VerificationResult.failure("directory", pathStr, "Directory does not exist", dirCheck.isCritical()));
                    continue;
                }

                String permissions = dirCheck.getPermissions() != null ? dirCheck.getPermissions().toLowerCase() : "";
                List<String> missingPermissions = new ArrayList<>();

                if (permissions.contains("r") && !Files.isReadable(path)) {
                    missingPermissions.add("read");
                }
                if (permissions.contains("w") && !Files.isWritable(path)) {
                    missingPermissions.add("write");
                }
                if (permissions.contains("x") && !Files.isExecutable(path)) {
                    missingPermissions.add("execute");
                }

                if (missingPermissions.isEmpty()) {
                    log.debug("Directory permissions OK: {} ({})", pathStr, permissions);
                    results.add(VerificationResult.success("directory", pathStr));
                } else {
                    String missing = String.join(", ", missingPermissions);
                    log.warn("Directory missing permissions: {} - {}", pathStr, missing);
                    results.add(VerificationResult.failure("directory", pathStr, "Missing permissions: " + missing, dirCheck.isCritical()));
                }
            } catch (Exception e) {
                log.error("Error verifying directory {}: {}", pathStr, e.getMessage());
                results.add(VerificationResult.failure("directory", pathStr, "Exception: " + e.getMessage(), dirCheck.isCritical()));
            }
        }

        return results;
    }
}
