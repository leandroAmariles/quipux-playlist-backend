package com.quipux.playlist;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.stream.Stream;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertiesLoaderUtils;

final class SpecialNames {

    private static final String FILE = "special-names.properties";

    private SpecialNames() {
    }

    static Stream<String> all() {
        try {
            Properties names = PropertiesLoaderUtils.loadProperties(
                    new EncodedResource(new ClassPathResource(FILE), StandardCharsets.UTF_8));
            return names.stringPropertyNames().stream().sorted().map(names::getProperty);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
