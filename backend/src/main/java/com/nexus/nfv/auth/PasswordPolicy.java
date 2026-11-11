package com.nexus.nfv.auth;

import com.nexus.nfv.common.exception.ApiException;

/** Enterprise password policy: >= 10 chars, upper, lower, digit, symbol, not containing the username. */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    public static void validate(String password, String username) {
        if (password == null || password.length() < 10 || password.length() > 128) {
            throw ApiException.badRequest("Password must be between 10 and 128 characters");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        boolean digit = password.chars().anyMatch(Character::isDigit);
        boolean symbol = password.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
        if (!(upper && lower && digit && symbol)) {
            throw ApiException.badRequest("Password must contain upper-case, lower-case, digit and symbol characters");
        }
        if (username != null && !username.isBlank()
                && password.toLowerCase().contains(username.toLowerCase())) {
            throw ApiException.badRequest("Password must not contain the username");
        }
    }
}
