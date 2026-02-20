package io.infracheck.core.util;

import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot application.yml 파싱 유틸리티
 * - 멀티 프로파일 문서(---) 지원
 * - ${...} 변수 해석 지원
 */
public class YamlParser {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseWithProfile(File yamlFile, String profile) {
        if (!yamlFile.exists()) return Collections.emptyMap();

        Yaml yaml = new Yaml();
        Map<String, Object> baseConfig = new LinkedHashMap<>();
        Map<String, Object> profileConfig = new LinkedHashMap<>();

        try (FileInputStream fis = new FileInputStream(yamlFile)) {
            Iterable<Object> documents = yaml.loadAll(fis);

            for (Object doc : documents) {
                if (!(doc instanceof Map)) continue;
                Map<String, Object> docMap = (Map<String, Object>) doc;
                String docProfile = extractProfile(docMap);

                if (docProfile == null) {
                    deepMerge(baseConfig, docMap);
                } else if (docProfile.equals(profile)) {
                    deepMerge(profileConfig, docMap);
                }
            }
        } catch (IOException e) {
            System.err.println("⚠️  YAML 파일 읽기 실패: " + yamlFile.getPath());
            return Collections.emptyMap();
        }

        deepMerge(baseConfig, profileConfig);
        return baseConfig;
    }

    @SuppressWarnings("unchecked")
    private static String extractProfile(Map<String, Object> doc) {
        Object spring = doc.get("spring");
        if (spring instanceof Map) {
            Map<String, Object> springMap = (Map<String, Object>) spring;
            Object config = springMap.get("config");
            if (config instanceof Map) {
                Map<String, Object> configMap = (Map<String, Object>) config;
                Object activate = configMap.get("activate");
                if (activate instanceof Map) {
                    Map<String, Object> activateMap = (Map<String, Object>) activate;
                    Object onProfile = activateMap.get("on-profile");
                    if (onProfile != null) return onProfile.toString();
                }
            }
            Object profiles = springMap.get("profiles");
            if (profiles instanceof String) return (String) profiles;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            Object targetValue = target.get(key);

            if (sourceValue instanceof Map && targetValue instanceof Map) {
                deepMerge((Map<String, Object>) targetValue, (Map<String, Object>) sourceValue);
            } else {
                target.put(key, sourceValue);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getNestedValue(Map<String, Object> map, String dotPath) {
        if (map == null || dotPath == null) return null;

        String[] keys = dotPath.split("\\.");
        Object current = map;

        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }

        try {
            return (T) current;
        } catch (ClassCastException e) {
            return null;
        }
    }

    public static String resolveValue(String value, Map<String, Object> config) {
        if (value == null || !value.contains("${")) return value;

        Matcher matcher = VARIABLE_PATTERN.matcher(value);
        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String key = matcher.group(1);
            String defaultValue = null;
            if (key.contains(":")) {
                String[] parts = key.split(":", 2);
                key = parts[0];
                defaultValue = parts[1];
            }

            Object resolved = getNestedValue(config, key);
            String replacement;
            if (resolved != null) {
                replacement = resolved.toString();
            } else if (defaultValue != null) {
                replacement = defaultValue;
            } else {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Map의 모든 리프(leaf) 값을 재귀적으로 순회합니다.
     * YAML에서 숫자 키(Integer 등)가 올 수 있으므로 Map<?, Object>로 처리합니다.
     */
    @SuppressWarnings("unchecked")
    public static void findAllValues(Map<?, Object> map, String prefix, ValueConsumer consumer) {
        if (map == null) return;

        for (Map.Entry<?, Object> entry : map.entrySet()) {
            String entryKey = entry.getKey() == null ? "" : entry.getKey().toString();
            String key = prefix.isEmpty() ? entryKey : prefix + "." + entryKey;
            Object value = entry.getValue();

            if (value instanceof Map) {
                findAllValues((Map<?, Object>) value, key, consumer);
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (int i = 0; i < list.size(); i++) {
                    Object item = list.get(i);
                    if (item instanceof Map) {
                        findAllValues((Map<?, Object>) item, key + "[" + i + "]", consumer);
                    } else if (item != null) {
                        consumer.accept(key + "[" + i + "]", item);
                    }
                }
            } else if (value != null) {
                consumer.accept(key, value);
            }
        }
    }

    @FunctionalInterface
    public interface ValueConsumer {
        void accept(String key, Object value);
    }
}
