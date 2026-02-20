package io.infracheck.core.util;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 파일 경로, URL 등의 패턴 매칭 유틸리티
 */
public class PatternMatcher {

    private static final Pattern FILE_EXTENSION_PATTERN = Pattern.compile(
        "^/[a-zA-Z0-9/_.-]+\\.(der|pem|p8|p12|cer|crt|key|jks|keystore|pfx|truststore)$"
    );

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
        "^/(nas[0-9]*|mnt|home|var|opt)/[a-zA-Z0-9/_.-]+$"
    );

    private static final Pattern URL_PATTERN = Pattern.compile(
        "^https?://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$"
    );

    private static final List<String> DEFAULT_EXCLUDES = List.of(
        "localhost", "127.0.0.1", "0.0.0.0", "host.docker.internal"
    );

    public static boolean isFilePath(String value) {
        if (value == null || value.isEmpty()) return false;
        return FILE_EXTENSION_PATTERN.matcher(value).matches()
            || FILE_PATH_PATTERN.matcher(value).matches();
    }

    public static String detectLocation(String path) {
        if (path == null) return "unknown";
        if (path.startsWith("/nas") || path.startsWith("/mnt/nas")) return "nas";
        if (path.startsWith("/mnt")) return "mount";
        if (path.startsWith("/home") || path.startsWith("/opt")) return "local";
        if (path.startsWith("/var")) return "var";
        return "unknown";
    }

    public static boolean isUrl(String value) {
        if (value == null || value.isEmpty()) return false;
        return URL_PATTERN.matcher(value).matches();
    }

    /**
     * 순수 도메인명인지 확인합니다. (예: "api.jtbc.co.kr")
     * http(s):// 없이 도메인만 있는 경우를 감지합니다.
     */
    public static boolean isDomainName(String value) {
        if (value == null || value.isEmpty()) return false;
        if (value.startsWith("http://") || value.startsWith("https://")) return false;
        if (value.contains("/") || value.contains(" ") || value.contains("$")) return false;
        return value.matches("^[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?)+$");
    }

    public static boolean shouldExclude(String value, List<String> userExcludePatterns) {
        if (value == null) return true;

        for (String exclude : DEFAULT_EXCLUDES) {
            if (value.contains(exclude)) return true;
        }

        if (userExcludePatterns != null) {
            for (String pattern : userExcludePatterns) {
                if (pattern == null) continue;
                String regex = pattern.replace(".", "\\.").replace("*", ".*");
                if (value.matches(".*" + regex + ".*")) return true;
            }
        }

        return false;
    }
}
