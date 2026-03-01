package io.infracheck.spring.verifier;

import io.infracheck.core.model.ApiCheck;
import io.infracheck.core.model.Requirements;
import io.infracheck.spring.model.VerificationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class ApiVerifier implements Verifier {
    private static final Logger log = LoggerFactory.getLogger(ApiVerifier.class);
    private final HttpClient httpClient;
    private final int timeoutSeconds;

    public ApiVerifier(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .build();
    }

    @Override
    public List<VerificationResult> verify(Requirements requirements) {
        List<VerificationResult> results = new ArrayList<>();
        if (requirements.getInfrastructure() == null || requirements.getInfrastructure().getExternal_apis() == null) {
            return results;
        }

        String companyDomain = requirements.getInfrastructure().getCompany_domain();
        List<ApiCheck> apis = requirements.getInfrastructure().getExternal_apis();

        // Sort: company domain first (Requirement 5.6)
        List<ApiCheck> sortedApis = apis.stream()
                .sorted(Comparator.comparing(api -> !isCompanyDomain(api.getUrl(), companyDomain)))
                .collect(Collectors.toList());

        for (ApiCheck apiCheck : sortedApis) {
            String url = apiCheck.getUrl();
            log.debug("Verifying API: {}", url);

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(Duration.ofSeconds(timeoutSeconds))
                        .build();

                HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
                int statusCode = response.statusCode();

                if (isAcceptableStatus(statusCode, apiCheck.getExpectedStatus())) {
                    log.debug("API accessible: {} (status: {})", url, statusCode);
                    results.add(VerificationResult.success("api", url));
                } else {
                    log.warn("API returned unexpected status: {} (status: {})", url, statusCode);
                    results.add(VerificationResult.failure("api", url, "Unexpected status code: " + statusCode, apiCheck.isCritical()));
                }
            } catch (Exception e) {
                log.error("API unreachable: {} - {}", url, e.getMessage());
                results.add(VerificationResult.failure("api", url, "Unreachable: " + e.getMessage(), apiCheck.isCritical()));
            }
        }

        return results;
    }

    private boolean isCompanyDomain(String url, String companyDomain) {
        if (companyDomain == null || companyDomain.isEmpty()) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            return host != null && host.endsWith(companyDomain);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isAcceptableStatus(int status, List<Integer> expectedStatus) {
        if (expectedStatus != null && !expectedStatus.isEmpty()) {
            return expectedStatus.contains(status);
        }
        // Default acceptance (Requirement 5.4)
        return (status >= 200 && status < 400) || status == 401 || status == 403;
    }
}
