package com.quipux.playlist.archfixture.domain;

import org.springframework.util.StringUtils;

/**
 * Deliberately breaks the domain rule, to prove that the architecture test detects it.
 */
public class FrameworkDependentFixture {

    public boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
