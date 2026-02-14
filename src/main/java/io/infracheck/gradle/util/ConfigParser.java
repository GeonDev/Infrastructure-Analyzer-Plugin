package io.infracheck.gradle.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Spring Boot 설정 파일 파싱 유틸리티
 * - application.yml 지원
 * - application.properties 지원
 * - 프로파일별 설정 병합
 */
public class ConfigParser {

    /**
     * 설정 파일을 자동 감지하여 파싱합니다.
     * 우선순위: application.yml > application.properties
     */
    public static Map<String, Object> parseWithProfile(File projectDir, String profile) {
        // 1. application.yml 시도
        File yamlFile = new File(projectDir, "src/main/resources/application.yml");
        if (yamlFile.exists()) {
            return YamlParser.parseWithProfile(yamlFile, profile);
        }

        // 2. application.properties 시도
        File propsFile = new File(projectDir, "src/main/resources/application.properties");
        if (propsFile.exists()) {
            return parseProperties(propsFile, profile);
        }

        return Collections.emptyMap();
    }

    /**
     * application.properties 파일을 파싱합니다.
     * 프로파일별 파일도 병합합니다.
     */
    private static Map<String, Object> parseProperties(File propsFile, String profile) {
        Map<String, Object> config = new LinkedHashMap<>();

        // 1. 기본 설정 로드
        loadPropertiesFile(propsFile, config);

        // 2. 프로파일별 설정 로드 (application-{profile}.properties)
        if (profile != null && !profile.isEmpty()) {
            File profilePropsFile = new File(propsFile.getParent(),
                "application-" + profile + ".properties");
            if (profilePropsFile.exists()) {
                loadPropertiesFile(profilePropsFile, config);
            }
        }

        return config;
    }

    /**
     * Properties 파일을 로드하여 중첩 Map 구조로 변환합니다.
     * 예: spring.datasource.url -> {spring: {datasource: {url: "..."}}}
     */
    private static void loadPropertiesFile(File file, Map<String, Object> target) {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(file)) {
            props.load(fis);

            for (String key : props.stringPropertyNames()) {
                String value = props.getProperty(key);
                setNestedValue(target, key, value);
            }
        } catch (IOException e) {
            System.err.println("⚠️  Properties 파일 읽기 실패: " + file.getPath());
        }
    }

    /**
     * 점(.) 구분자로 중첩된 Map에 값을 설정합니다.
     * 예: setNestedValue(map, "spring.datasource.url", "jdbc:...")
     */
    @SuppressWarnings("unchecked")
    private static void setNestedValue(Map<String, Object> map, String dotPath, Object value) {
        String[] keys = dotPath.split("\\.");
        Map<String, Object> current = map;

        for (int i = 0; i < keys.length - 1; i++) {
            String key = keys[i];
            Object next = current.get(key);

            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(key, next);
            }
            current = (Map<String, Object>) next;
        }

        current.put(keys[keys.length - 1], value);
    }
}
