package com.quipux.playlist.startup;

import com.quipux.playlist.application.port.in.RegisterDemoUsersPort;
import com.quipux.playlist.domain.model.Credentials;
import com.quipux.playlist.domain.model.constants.PasswordConstraints;
import com.quipux.playlist.domain.model.enums.DemoUser;
import com.quipux.playlist.domain.model.enums.ErrorMessageEnum;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DemoUsersInitializer implements SmartInitializingSingleton {

    private static final String USER_PASSWORD_VARIABLE = "DEMO_USER_PASSWORD";
    private static final String ADMIN_PASSWORD_VARIABLE = "DEMO_ADMIN_PASSWORD";

    private final RegisterDemoUsersPort registerDemoUsers;

    @Value("${playlist.security.demo-users.user-password:}")
    private String userPassword;

    @Value("${playlist.security.demo-users.admin-password:}")
    private String adminPassword;

    public DemoUsersInitializer(RegisterDemoUsersPort registerDemoUsers) {
        this.registerDemoUsers = registerDemoUsers;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<DemoUser, String> passwords = new EnumMap<>(DemoUser.class);
        passwords.put(DemoUser.USER, requireUsable(DemoUser.USER, userPassword, USER_PASSWORD_VARIABLE));
        passwords.put(DemoUser.ADMIN, requireUsable(DemoUser.ADMIN, adminPassword, ADMIN_PASSWORD_VARIABLE));
        registerDemoUsers.register(passwords).block();
    }

    private static String requireUsable(DemoUser demoUser, String password, String variable) {
        if (password.isBlank() || new Credentials(demoUser.username(), password).passwordExceedsLimit()) {
            throw new IllegalStateException(
                    ErrorMessageEnum.DEMO_PASSWORD_UNUSABLE.withCode(variable, PasswordConstraints.MAX_BYTES));
        }
        return password;
    }
}
