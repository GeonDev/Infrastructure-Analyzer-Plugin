package io.infracheck.spring.autoconfigure;

import io.infracheck.spring.config.InfrastructureVerificationProperties;
import io.infracheck.spring.loader.RequirementsLoader;
import io.infracheck.spring.verifier.ApiVerifier;
import io.infracheck.spring.verifier.DirectoryVerifier;
import io.infracheck.spring.verifier.FileVerifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(
    name = "infrastructure.verification.enabled",
    havingValue = "true",
    matchIfMissing = true
)
@EnableConfigurationProperties(InfrastructureVerificationProperties.class)
public class InfrastructureVerificationAutoConfiguration {
    
    @Bean
    public RequirementsLoader requirementsLoader() {
        return new RequirementsLoader();
    }
    
    @Bean
    public FileVerifier fileVerifier() {
        return new FileVerifier();
    }
    
    @Bean
    public ApiVerifier apiVerifier(InfrastructureVerificationProperties properties) {
        return new ApiVerifier(properties.getTimeoutSeconds());
    }
    
    @Bean
    public DirectoryVerifier directoryVerifier() {
        return new DirectoryVerifier();
    }
}
