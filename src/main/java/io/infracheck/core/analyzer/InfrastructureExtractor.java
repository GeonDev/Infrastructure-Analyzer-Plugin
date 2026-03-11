package io.infracheck.core.analyzer;

import io.infracheck.core.model.*;
import io.infracheck.core.util.PatternMatcher;
import io.infracheck.core.util.YamlParser;

import java.io.File;
import java.util.*;

/**
 * 설정 파일 및 소스코드에서 인프라 검증 항목을 추출하는 핵심 로직
 * 하이브리드 방식: 명시적 선언 우선, 자동 추출 Fallback, 소스코드 분석
 */
public class InfrastructureExtractor {

    private final Map<String, Object> config;
    private final String companyDomain;
    private final List<String> excludePatterns;
    private final SourceCodeAnalyzer sourceAnalyzer;
    private final boolean sourceCodeAnalysisEnabled;

    public InfrastructureExtractor(Map<String, Object> config, File projectDir) {
        this.config = config;

        String domain = YamlParser.getNestedValue(config, "infrastructure.validation.domain");
        this.companyDomain = (domain != null) ? domain : "company.com";

        List<String> patterns = YamlParser.getNestedValue(config, "infrastructure.validation.exclude-patterns");
        this.excludePatterns = (patterns != null) ? patterns : Collections.emptyList();

        Boolean enabled = YamlParser.getNestedValue(config, "infrastructure.validation.source-code-analysis.enabled");
        this.sourceCodeAnalysisEnabled = (enabled == null || enabled);

        File sourceDir = new File(projectDir, "src/main/java");
        this.sourceAnalyzer = new SourceCodeAnalyzer(sourceDir);
    }

    public String getCompanyDomain() {
        return companyDomain;
    }

    // ========== 파일 추출 ==========

    public List<FileCheck> extractFiles() {
        List<FileCheck> allFiles = new ArrayList<>();

        List<Map<String, Object>> explicitFiles = YamlParser.getNestedValue(config, "infrastructure.validation.files");

        if (explicitFiles != null && !explicitFiles.isEmpty()) {
            allFiles.addAll(parseExplicitFiles(explicitFiles));
        } else {
            allFiles.addAll(autoExtractFiles());
        }

        if (sourceCodeAnalysisEnabled) {
            allFiles.addAll(extractFilesFromSource());
        }

        return deduplicateFiles(allFiles);
    }

    private List<FileCheck> parseExplicitFiles(List<Map<String, Object>> explicitFiles) {
        List<FileCheck> files = new ArrayList<>();
        for (Map<String, Object> item : explicitFiles) {
            String path = YamlParser.resolveValue((String) item.get("path"), config);
            if (path == null || path.contains("${"))
                continue;
            Object criticalObj = item.getOrDefault("critical", true);
            boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
            String description = (String) item.getOrDefault("description", path);
            files.add(new FileCheck(path, PatternMatcher.detectLocation(path), critical, description));
        }
        return files;
    }

    private List<FileCheck> autoExtractFiles() {
        List<FileCheck> files = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        YamlParser.findAllValues(config, "", (key, value) -> {
            if (!(value instanceof String))
                return;
            String path = (String) value;
            if (key.startsWith("infrastructure.validation"))
                return;
            if (PatternMatcher.shouldExclude(path, excludePatterns))
                return;

            if (PatternMatcher.isFilePath(path) && !seen.contains(path)) {
                seen.add(path);
                files.add(new FileCheck(path, PatternMatcher.detectLocation(path), true, key));
            }
        });

        return files;
    }

    private List<FileCheck> extractFilesFromSource() {
        List<FileCheck> files = new ArrayList<>();
        for (String path : sourceAnalyzer.extractFilePaths()) {
            files.add(new FileCheck(path, PatternMatcher.detectLocation(path), true, "소스코드에서 검출됨"));
        }
        return files;
    }

    private List<FileCheck> deduplicateFiles(List<FileCheck> files) {
        Map<String, FileCheck> unique = new LinkedHashMap<>();
        for (FileCheck file : files) {
            if (!unique.containsKey(file.getPath()))
                unique.put(file.getPath(), file);
        }
        return new ArrayList<>(unique.values());
    }

    // ========== 디렉토리 권한 추출 (VM 전용) ==========

    public List<DirectoryCheck> extractDirectories() {
        List<Map<String, Object>> explicitDirs = YamlParser.getNestedValue(config,
                "infrastructure.validation.directories");

        if (explicitDirs == null || explicitDirs.isEmpty())
            return Collections.emptyList();

        List<DirectoryCheck> directories = new ArrayList<>();
        for (Map<String, Object> dirMap : explicitDirs) {
            DirectoryCheck dir = new DirectoryCheck();
            dir.setPath((String) dirMap.get("path"));
            dir.setPermissions((String) dirMap.getOrDefault("permissions", "rwx"));
            Object criticalObj = dirMap.getOrDefault("critical", true);
            dir.setCritical(criticalObj instanceof Boolean ? (Boolean) criticalObj : true);
            dir.setDescription((String) dirMap.getOrDefault("description", ""));
            directories.add(dir);
        }

        return directories;
    }

    // ========== 외부 API 추출 ==========

    public List<ApiCheck> extractApis() {
        List<ApiCheck> allApis = new ArrayList<>();

        List<Map<String, Object>> explicitApis = YamlParser.getNestedValue(config, "infrastructure.validation.apis");

        if (explicitApis != null && !explicitApis.isEmpty()) {
            allApis.addAll(parseExplicitApis(explicitApis));
        } else {
            allApis.addAll(autoExtractApis());
        }

        if (sourceCodeAnalysisEnabled) {
            allApis.addAll(extractApisFromSource());
        }

        return deduplicateApis(allApis);
    }

    private List<ApiCheck> parseExplicitApis(List<Map<String, Object>> explicitApis) {
        List<ApiCheck> apis = new ArrayList<>();
        for (Map<String, Object> item : explicitApis) {
            String url = YamlParser.resolveValue((String) item.get("url"), config);
            if (url == null || url.contains("${"))
                continue;
            Object criticalObj = item.getOrDefault("critical", true);
            boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
            String description = (String) item.getOrDefault("description", url);
            String method = (String) item.getOrDefault("method", "HEAD");
            apis.add(new ApiCheck(url, method, critical, description));
        }
        return apis;
    }

    private List<ApiCheck> autoExtractApis() {
        List<ApiCheck> apis = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        YamlParser.findAllValues(config, "", (key, value) -> {
            if (!(value instanceof String))
                return;
            String str = (String) value;
            if (key.startsWith("infrastructure.validation"))
                return;
            if (PatternMatcher.shouldExclude(str, excludePatterns))
                return;

            String normalizedUrl = null;

            if (PatternMatcher.isUrl(str)) {
                normalizedUrl = str;
            } else if (PatternMatcher.isDomainName(str) && str.contains(companyDomain)) {
                normalizedUrl = "https://" + str;
            }

            if (normalizedUrl != null && !seen.contains(normalizedUrl)) {
                seen.add(normalizedUrl);
                boolean isCompanyDomain = normalizedUrl.contains(companyDomain);
                String desc = isCompanyDomain ? key + " (회사 도메인)" : key + " (외부 - 경고만)";
                apis.add(new ApiCheck(normalizedUrl, "HEAD", isCompanyDomain, desc));
            }
        });

        return apis;
    }

    private List<ApiCheck> extractApisFromSource() {
        List<ApiCheck> apis = new ArrayList<>();
        for (String url : sourceAnalyzer.extractUrls()) {
            boolean isCompanyDomain = url.contains(companyDomain);
            String desc = isCompanyDomain ? "소스코드에서 검출됨 (회사 도메인)" : "소스코드에서 검출됨 (외부 - 경고만)";
            apis.add(new ApiCheck(url, "HEAD", isCompanyDomain, desc));
        }
        return apis;
    }

    private List<ApiCheck> deduplicateApis(List<ApiCheck> apis) {
        Map<String, ApiCheck> unique = new LinkedHashMap<>();
        for (ApiCheck api : apis) {
            if (!unique.containsKey(api.getUrl()))
                unique.put(api.getUrl(), api);
        }
        return new ArrayList<>(unique.values());
    }

}
