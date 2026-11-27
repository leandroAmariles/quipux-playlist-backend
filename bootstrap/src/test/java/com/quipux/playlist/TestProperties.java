package com.quipux.playlist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

final class TestProperties {

    private TestProperties() {
    }

    static Properties load(String file) {
        try {
            return PropertiesLoaderUtils.loadProperties(
                    new EncodedResource(new ClassPathResource(file), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static List<String> values(String file, String prefix) {
        Properties properties = load(file);
        return properties.stringPropertyNames().stream()
                .filter(key -> key.startsWith(prefix))
                .sorted()
                .map(properties::getProperty)
                .toList();
    }
}
