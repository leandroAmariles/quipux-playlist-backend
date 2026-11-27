package com.quipux.playlist.archfixture.application;

import org.springframework.util.ObjectUtils;

/**
 * Deliberately breaks the application rule, to prove that the architecture test detects it.
 */
public class ForbiddenDependencyFixture {

    public boolean isEmpty(Object value) {
        return ObjectUtils.isEmpty(value);
    }
}
