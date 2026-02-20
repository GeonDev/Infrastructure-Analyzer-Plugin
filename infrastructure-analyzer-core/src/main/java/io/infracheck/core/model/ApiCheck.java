package io.infracheck.core.model;

import java.util.List;

/**
 * 외부 API 접근 검증 항목
 */
public class ApiCheck {
    private String url;
    private String method;
    private List<Integer> expectedStatus;
    private boolean critical;
    private String description;

    public ApiCheck() {}

    public ApiCheck(String url, String method, boolean critical, String description) {
        this.url = url;
        this.method = method;
        this.critical = critical;
        this.description = description;
        this.expectedStatus = List.of(200, 301, 302, 401, 403, 404);
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public List<Integer> getExpectedStatus() { return expectedStatus; }
    public void setExpectedStatus(List<Integer> expectedStatus) { this.expectedStatus = expectedStatus; }
    public boolean isCritical() { return critical; }
    public void setCritical(boolean critical) { this.critical = critical; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
