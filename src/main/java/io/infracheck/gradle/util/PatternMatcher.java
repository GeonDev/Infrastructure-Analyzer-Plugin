package io.infracheck.gradle.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 파일 경로, URL 등의 패턴 매칭 유틸리티
 */
public class PatternMatcher {

    // 인증서/키 파일 확장자 패턴
    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(
        "^/[a-zA-Z0-9/_.-]+\\.(der|pem|p8|p12|cer|crt|key|jks|keystore|pfx|truststore)$"
    );

    // NAS/마운트 경로 패턴
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "^/(nas[0-9]*|mnt|home|var|opt)/[a-zA-Z0-9/_.-]+$"
    );

    // URL 패턴
    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$"
    );

    // 기본 제외 패턴 (자동 추출 시)
    private static final List<String> DEFAULT_EXCLUDES = List.of(
        "localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal"
    );

    /**
     * 파일 경로인지 확인합니다.
     * - 인증서/키 파일 확장자 (.der, .pem, .p8 등)
     * - NAS/마운트 경로 (/nas, /mnt, /home 등)
     */
    public static boolean isFilePath(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return FILE_EXTENSION_PATTERN.matcher(value).matches()
            || FILE_PATH_PATTERN.matcher(value).matches();
    }

    /**
     * 파일 위치를 감지합니다.
     */
    public static String detectLocation(String path) {
        if (path == null) return "unknown";
        if (path.startsWith("/nas") || path.startsWith("/mnt/nas")) return "nas";
        if (path.startsWith("/mnt")) return "mount";
        if (path.startsWith("/home") || path.startsWith("/opt")) return "local";
        if (path.startsWith("/var")) return "var";
        return "unknown";
    }

    /**
     * URL인지 확인합니다.
     */
    public static boolean isUrl(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return URL_PATTERN.matcher(value).matches();
    }

    /**
     * 순수 도메인명인지 확인합니다. (예: "api.jtbc.co.kr", "voddev.jtbc.co.kr")
     * http(s):// 없이 도메인만 있는 경우를 감지합니다.
     */
    public static boolean isDomainName(String value) {
        if (value == null || value.isEmpty()) return false;
        // 이미 URL이면 제외
        if (value.startsWith("http://") || value.startsWith("https://")) return false;
        // 경로나 특수문자 포함 시 제외
        if (value.contains("/") || value.contains(" ") || value.contains("$")) return false;
        // 점(.)이 포함된 도메인 형태: xxx.yyy.zzz
        return value.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$");
    }

    /**
     * 제외 패턴에 해당하는지 확인합니다.
     * 기본 제외 패턴 + 사용자 정의 제외 패턴을 모두 확인합니다.
     */
    public static boolean shouldExclude(String value, List<String> userExcludePatterns) {
        if (value == null) return true;

        // 기본 제외 패턴 확인
        for (String exclude : DEFAULT_EXCLUDES) {
            if (value.contains(exclude)) {
                return true;
            }
        }

        // 사용자 정의 제외 패턴 확인
        if (userExcludePatterns != null) {
            for (String pattern : userExcludePatterns) {
                if (pattern == null) continue;
                // 와일드카드 패턴 지원 (*.local → .*\.local)
                String regex = pattern
                    .replace(".", "\\.")
                    .replace("*", ".*");
                if (value.matches(".*" + regex + ".*")) {
                    return true;
                }
            }
        }

        return false;
    }
}
