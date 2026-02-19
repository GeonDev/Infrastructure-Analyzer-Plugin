package io.infracheck.gradle.analyzer;

import io.infracheck.gradle.model.*;
import io.infracheck.gradle.util.PatternMatcher;
import io.infracheck.gradle.util.YamlParser;

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

        // 회사 도메인 설정
        String domain = YamlParser.getNestedValue(config, "infrastructure.validation.company-domain");
        this.companyDomain = (domain != null) ? domain : "company.com";

        // 제외 패턴 설정
        List<String> patterns = YamlParser.getNestedValue(config, "infrastructure.validation.exclude-patterns");
        this.excludePatterns = (patterns != null) ? patterns : Collections.emptyList();

        // 소스코드 분석 활성화 여부
        Boolean enabled = YamlParser.getNestedValue(config, "infrastructure.validation.source-code-analysis.enabled");
        this.sourceCodeAnalysisEnabled = (enabled == null || enabled); // 기본값: true

        // 소스코드 분석기 초기화
        File sourceDir = new File(projectDir, "src/main/java");
        this.sourceAnalyzer = new SourceCodeAnalyzer(sourceDir);
    }

    public String getCompanyDomain() {
        return companyDomain;
    }

    // ========== 파일 추출 ==========

    /**
     * NAS/로컬 파일 검증 항목을 추출합니다.
     * 1. 명시적 선언 우선 (infrastructure.validation.files)
     * 2. 설정 파일 자동 추출 (패턴 기반)
     * 3. 소스코드 분석 (하드코딩 검출)
     */
    @SuppressWarnings("unchecked")
    public List<FileCheck> extractFiles() {
        List<FileCheck> allFiles = new ArrayList<>();

        // 1. 명시적 선언 확인
        List<Map<String, Object>> explicitFiles =
            YamlParser.getNestedValue(config, "infrastructure.validation.files");

        if (explicitFiles != null && !explicitFiles.isEmpty()) {
            allFiles.addAll(parseExplicitFiles(explicitFiles));
        } else {
            // 2. 설정 파일 자동 추출 (Fallback)
            allFiles.addAll(autoExtractFiles());
        }

        // 3. 소스코드 분석
        if (sourceCodeAnalysisEnabled) {
            allFiles.addAll(extractFilesFromSource());
        }

        // 중복 제거
        return deduplicateFiles(allFiles);
    }

    @SuppressWarnings("unchecked")
    private List<FileCheck> parseExplicitFiles(List<Map<String, Object>> explicitFiles) {
        List<FileCheck> files = new ArrayList<>();
        for (Map<String, Object> item : explicitFiles) {
            String path = YamlParser.resolveValue((String) item.get("path"), config);
            if (path == null || path.contains("${")) {
                continue; // 해석 불가한 변수는 건너뜀
            }
            Object criticalObj = item.getOrDefault("critical", true);
            boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
            String description = (String) item.getOrDefault("description", path);

            files.add(new FileCheck(path, PatternMatcher.detectLocation(path), critical, description));
        }
        return files;
    }

    private List<FileCheck> autoExtractFiles() {
        List<FileCheck> files = new ArrayList<>();
        Set<String> seen = new HashSet<>(); // 중복 방지

        YamlParser.findAllValues(config, "", (key, value) -> {
            if (!(value instanceof String)) return;
            String path = (String) value;

            // infrastructure.validation 섹션 자체는 건너뜀
            if (key.startsWith("infrastructure.validation")) return;

            if (PatternMatcher.shouldExclude(path, excludePatterns)) return;

            if (PatternMatcher.isFilePath(path) && !seen.contains(path)) {
                seen.add(path);
                files.add(new FileCheck(
                    path,
                    PatternMatcher.detectLocation(path),
                    true,
                    key
                ));
            }
        });

        return files;
    }

    /**
     * 소스코드에서 하드코딩된 파일 경로를 추출합니다.
     */
    private List<FileCheck> extractFilesFromSource() {
        List<FileCheck> files = new ArrayList<>();
        List<String> paths = sourceAnalyzer.extractFilePaths();

        for (String path : paths) {
            String location = PatternMatcher.detectLocation(path);
            files.add(new FileCheck(
                path,
                location,
                true,  // 소스코드 하드코딩은 기본적으로 critical
                "소스코드에서 검출됨"
            ));
        }

        return files;
    }

    /**
     * 파일 중복 제거 (경로 기준)
     */
    private List<FileCheck> deduplicateFiles(List<FileCheck> files) {
        Map<String, FileCheck> uniqueFiles = new LinkedHashMap<>();
        for (FileCheck file : files) {
            // 이미 있는 경우 명시적 선언이나 설정 파일 우선
            if (!uniqueFiles.containsKey(file.getPath())) {
                uniqueFiles.put(file.getPath(), file);
            }
        }
        return new ArrayList<>(uniqueFiles.values());
    }

    // ========== 디렉토리 권한 추출 (VM 전용) ==========

    /**
     * 디렉토리 권한 검증 항목을 추출합니다 (VM 환경 전용).
     * 명시적 선언만 지원 (infrastructure.validation.directories)
     */
    @SuppressWarnings("unchecked")
    public List<DirectoryCheck> extractDirectories() {
        List<Map<String, Object>> explicitDirs =
            YamlParser.getNestedValue(config, "infrastructure.validation.directories");

        if (explicitDirs == null || explicitDirs.isEmpty()) {
            return Collections.emptyList();
        }

        List<DirectoryCheck> directories = new ArrayList<>();
        for (Map<String, Object> dirMap : explicitDirs) {
            DirectoryCheck dir = new DirectoryCheck();
            dir.setPath((String) dirMap.get("path"));
            dir.setPermissions((String) dirMap.getOrDefault("permissions", "rwx"));
            dir.setCritical((Boolean) dirMap.getOrDefault("critical", true));
            dir.setDescription((String) dirMap.getOrDefault("description", ""));
            directories.add(dir);
        }

        return directories;
    }

    // ========== 외부 API 추출 ==========

    /**
     * 외부 API 검증 항목을 추출합니다.
     * 1. 명시적 선언 우선 (infrastructure.validation.apis)
     * 2. 설정 파일 자동 추출 (URL 패턴 기반)
     * 3. 소스코드 분석 (하드코딩 검출)
     */
    @SuppressWarnings("unchecked")
    public List<ApiCheck> extractApis() {
        List<ApiCheck> allApis = new ArrayList<>();

        // 1. 명시적 선언 확인
        List<Map<String, Object>> explicitApis =
            YamlParser.getNestedValue(config, "infrastructure.validation.apis");

        if (explicitApis != null && !explicitApis.isEmpty()) {
            allApis.addAll(parseExplicitApis(explicitApis));
        } else {
            // 2. 설정 파일 자동 추출 (Fallback)
            allApis.addAll(autoExtractApis());
        }

        // 3. 소스코드 분석
        if (sourceCodeAnalysisEnabled) {
            allApis.addAll(extractApisFromSource());
        }

        // 중복 제거
        return deduplicateApis(allApis);
    }

    @SuppressWarnings("unchecked")
    private List<ApiCheck> parseExplicitApis(List<Map<String, Object>> explicitApis) {
        List<ApiCheck> apis = new ArrayList<>();
        for (Map<String, Object> item : explicitApis) {
            String url = YamlParser.resolveValue((String) item.get("url"), config);
            if (url == null || url.contains("${")) {
                continue; // 해석 불가한 변수는 건너뜀
            }
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
        Set<String> seen = new HashSet<>(); // 중복 방지

        YamlParser.findAllValues(config, "", (key, value) -> {
            if (!(value instanceof String)) return;
            String str = (String) value;

            // infrastructure.validation 섹션 자체는 건너뜀
            if (key.startsWith("infrastructure.validation")) return;

            if (PatternMatcher.shouldExclude(str, excludePatterns)) return;

            if (PatternMatcher.isUrl(str) && !seen.contains(str)) {
                seen.add(str);
                boolean isCompanyDomain = str.contains(companyDomain);
                String desc = isCompanyDomain
                    ? key + " (회사 도메인)"
                    : key + " (외부 - 경고만)";

                apis.add(new ApiCheck(str, "HEAD", isCompanyDomain, desc));
            }
        });

        return apis;
    }

    /**
     * 소스코드에서 하드코딩된 API URL을 추출합니다.
     */
    private List<ApiCheck> extractApisFromSource() {
        List<ApiCheck> apis = new ArrayList<>();
        List<String> urls = sourceAnalyzer.extractUrls();

        for (String url : urls) {
            boolean isCompanyDomain = url.contains(companyDomain);
            String desc = isCompanyDomain
                ? "소스코드에서 검출됨 (회사 도메인)"
                : "소스코드에서 검출됨 (외부 - 경고만)";

            apis.add(new ApiCheck(url, "HEAD", isCompanyDomain, desc));
        }

        return apis;
    }

    /**
     * API 중복 제거 (URL 기준)
     */
    private List<ApiCheck> deduplicateApis(List<ApiCheck> apis) {
        Map<String, ApiCheck> uniqueApis = new LinkedHashMap<>();
        for (ApiCheck api : apis) {
            // 이미 있는 경우 명시적 선언이나 설정 파일 우선
            if (!uniqueApis.containsKey(api.getUrl())) {
                uniqueApis.put(api.getUrl(), api);
            }
        }
        return new ArrayList<>(uniqueApis.values());
    }

    // ========== 쿠버네티스 리소스 추출 ==========

    /**
     * K8s ConfigMap 검증 항목을 추출합니다.
     * 명시적 선언 또는 자동 추출
     */
    @SuppressWarnings("unchecked")
    public List<ConfigMapCheck> extractConfigMaps() {
        // 명시적 선언 확인
        List<Map<String, Object>> explicit =
            YamlParser.getNestedValue(config, "infrastructure.validation.configmaps");

        if (explicit != null && !explicit.isEmpty()) {
            List<ConfigMapCheck> result = new ArrayList<>();
            for (Map<String, Object> item : explicit) {
                String name = (String) item.get("name");
                Object criticalObj = item.getOrDefault("critical", true);
                boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
                String description = (String) item.getOrDefault("description", name);
                result.add(new ConfigMapCheck(name, critical, description));
            }
            return result;
        }

        // 자동 추출: 기본 app-config 생성
        List<ConfigMapCheck> configMaps = new ArrayList<>();
        configMaps.add(new ConfigMapCheck("app-config", true, "애플리케이션 기본 설정"));
        return configMaps;
    }

    /**
     * K8s Secret 검증 항목을 추출합니다.
     * 명시적 선언 또는 자동 추출 (Vault, Redis 등 감지)
     */
    @SuppressWarnings("unchecked")
    public List<SecretCheck> extractSecrets() {
        // 명시적 선언 확인
        List<Map<String, Object>> explicit =
            YamlParser.getNestedValue(config, "infrastructure.validation.secrets");

        if (explicit != null && !explicit.isEmpty()) {
            List<SecretCheck> result = new ArrayList<>();
            for (Map<String, Object> item : explicit) {
                String name = (String) item.get("name");
                Object criticalObj = item.getOrDefault("critical", true);
                boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
                String description = (String) item.getOrDefault("description", name);
                result.add(new SecretCheck(name, critical, description));
            }
            return result;
        }

        // 자동 추출
        List<SecretCheck> secrets = new ArrayList<>();

        // Vault 토큰 감지
        if (YamlParser.getNestedValue(config, "spring.cloud.vault.uri") != null) {
            secrets.add(new SecretCheck("vault-token", true, "Vault 인증 토큰"));
        }

        // Redis 인증 감지
        if (YamlParser.getNestedValue(config, "spring.redis.host") != null
            || YamlParser.getNestedValue(config, "redis.host") != null) {
            secrets.add(new SecretCheck("redis-credentials", true, "Redis 인증 정보"));
        }

        // NAS 파일이 있으면 CDN 키 Secret 추가
        List<FileCheck> files = extractFiles();
        if (!files.isEmpty()) {
            secrets.add(new SecretCheck("file-keys", true, "파일 기반 인증 키"));
        }

        return secrets;
    }

    /**
     * K8s PVC 검증 항목을 추출합니다.
     * 명시적 선언 또는 자동 추출 (NAS 파일 경로 감지)
     */
    @SuppressWarnings("unchecked")
    public List<PvcCheck> extractPvcs() {
        // 명시적 선언 확인
        List<Map<String, Object>> explicit =
            YamlParser.getNestedValue(config, "infrastructure.validation.pvcs");

        if (explicit != null && !explicit.isEmpty()) {
            List<PvcCheck> result = new ArrayList<>();
            for (Map<String, Object> item : explicit) {
                String name = (String) item.get("name");
                Object criticalObj = item.getOrDefault("critical", true);
                boolean critical = criticalObj instanceof Boolean ? (Boolean) criticalObj : true;
                String description = (String) item.getOrDefault("description", name);
                String mountPath = (String) item.get("mountPath");
                result.add(new PvcCheck(name, critical, description, mountPath));
            }
            return result;
        }

        // 자동 추출: NAS 파일이 있으면 PVC 필요
        List<PvcCheck> pvcs = new ArrayList<>();
        Set<String> nasRoots = new HashSet<>();

        List<FileCheck> files = extractFiles();
        for (FileCheck file : files) {
            if ("nas".equals(file.getLocation())) {
                // NAS 루트 디렉토리 추출 (예: /nas2/was → nas2-was)
                String[] parts = file.getPath().split("/");
                if (parts.length >= 3) {
                    String nasRoot = "/" + parts[1] + "/" + parts[2];
                    if (!nasRoots.contains(nasRoot)) {
                        nasRoots.add(nasRoot);
                        String pvcName = "nas-" + parts[1] + "-" + parts[2];
                        pvcs.add(new PvcCheck(pvcName, true, "NAS 스토리지: " + nasRoot, nasRoot));
                    }
                }
            }
        }

        return pvcs;
    }

    /**
     * K8s 네임스페이스를 결정합니다.
     */
    public static String determineNamespace(String profile) {
        return switch (profile) {
            case "dev" -> "development";
            case "stg", "stage" -> "staging";
            case "prod" -> "production";
            default -> "default";
        };
    }
}
