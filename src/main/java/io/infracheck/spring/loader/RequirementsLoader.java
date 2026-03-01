package io.infracheck.spring.loader;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import io.infracheck.core.model.Requirements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class RequirementsLoader {
    private static final Logger log = LoggerFactory.getLogger(RequirementsLoader.class);
    private final ResourceLoader resourceLoader = new DefaultResourceLoader();
    private final Gson gson = new Gson();

    public Requirements load(String profile, String pathTemplate) {
        if (profile == null) {
            profile = "default";
        }
        
        String profilePath = pathTemplate.replace("{profile}", profile);
        log.debug("Attempting to load requirements for profile '{}' from: {}", profile, profilePath);

        try {
            Resource resource = resourceLoader.getResource(profilePath);
            if (resource.exists()) {
                return parseRequirements(resource);
            }
        } catch (Exception e) {
            log.debug("Profile-specific requirements not found at {}: {}", profilePath, e.getMessage());
        }

        // Fallback to default requirements.json
        String defaultPath = "classpath:infrastructure/requirements.json";
        log.debug("Falling back to default requirements from: {}", defaultPath);
        try {
            Resource resource = resourceLoader.getResource(defaultPath);
            if (resource.exists()) {
                return parseRequirements(resource);
            }
        } catch (Exception e) {
            log.warn("Default requirements not found at {}", defaultPath);
        }

        return null;
    }

    private Requirements parseRequirements(Resource resource) {
        try (InputStreamReader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)) {
            Requirements req = gson.fromJson(reader, Requirements.class);
            if (req == null) {
                log.error("Requirements file is empty: {}", resource.getDescription());
                return null;
            }
            // validateRequirements(req); // Optional additional validation
            return req;
        } catch (JsonSyntaxException e) {
            log.error("Invalid JSON format in requirements file: {}", e.getMessage());
            throw new IllegalStateException("Malformed requirements specification", e);
        } catch (IOException e) {
            log.error("Error reading requirements file: {}", e.getMessage());
            return null;
        }
    }
}
