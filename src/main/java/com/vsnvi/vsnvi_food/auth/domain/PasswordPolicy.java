package com.vsnvi.vsnvi_food.auth.domain;

import com.vsnvi.vsnvi_food.common.domain.ValidationException;
import java.nio.charset.StandardCharsets;

/** Minimum 8 characters (docs/SECURITY.md §1) and at most 72 UTF-8 bytes, BCrypt's input limit. */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {}

    /** Enforced for every account, including the admin bootstrap, not only through the web layer. */
    public static void check(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException("Password must be at least " + MIN_LENGTH + " characters");
        }
        if (!fitsBcrypt(password)) {
            throw new ValidationException("Password must be at most " + MAX_BYTES + " bytes");
        }
    }

    public static boolean fitsBcrypt(String password) {
        return password.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }
}
