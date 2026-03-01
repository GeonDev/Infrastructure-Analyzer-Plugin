package io.infracheck.spring.verifier;

import io.infracheck.core.model.FileCheck;
import io.infracheck.core.model.Requirements;
import io.infracheck.spring.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class FileVerifier implements Verifier {
    private static final Logger log = LoggerFactory.getLogger(FileVerifier.class);

    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        if (requirements.getInfrastructure() == null || requirements.getInfrastructure().getFiles() == null) {
            return results;
        }

        for (FileCheck fileCheck : requirements.getInfrastructure().getFiles()) {
            String pathStr = fileCheck.getPath();
            log.debug("Verifying file: {}", pathStr);

            try {
                Path path = Paths.get(pathStr);
                if (Files.exists(path)) {
                    log.debug("File exists: {}", pathStr);
                    results.add(VerificationResult.success("file", pathStr));
                } else {
                    log.warn("File not found: {}", pathStr);
                    results.add(VerificationResult.failure("file", pathStr, "File does not exist", fileCheck.isCritical()));
                }
            } catch (Exception e) {
                log.error("Error verifying file {}: {}", pathStr, e.getMessage());
                results.add(VerificationResult.failure("file", pathStr, "Exception: " + e.getMessage(), fileCheck.isCritical()));
            }
        }

        return results;
    }
}
