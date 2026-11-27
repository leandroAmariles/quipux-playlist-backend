package com.quipux.playlist.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.Role;
import org.junit.jupiter.api.Test;

class UserTest {

    @Test
    void requiresUsernameHashAndRole() {
        assertThatThrownBy(() -> new User(null, "hash", Role.USER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new User("user", null, Role.USER)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new User("user", "hash", null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void doesNotShowThePasswordHash() {
        assertThat(new User("user", "the-hash", Role.USER).toString())
                .contains("user", "USER")
                .doesNotContain("the-hash");
    }

    @Test
    void doesNotShowTheTokenOfAnAccessToken() {
        assertThat(new AccessToken("the-token", 60).toString()).doesNotContain("the-token");
    }

    @Test
    void requiresATokenThatLasts() {
        assertThatThrownBy(() -> new AccessToken(null, 60)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AccessToken("token", 0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void definesOneDemoUserPerRole() {
        assertThat(DemoUser.values())
                .extracting(DemoUser::username, DemoUser::role)
                .containsExactly(tuple("user", Role.USER), tuple("admin", Role.ADMIN));
    }
}
