package com.xianshuyuan.scm.auth.validation;

public final class PasswordPolicies {
    private PasswordPolicies() {}
    public static boolean valid(String value) {
        if (value == null) return false;
        int bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return bytes >= 12 && bytes <= 72
                && value.chars().noneMatch(Character::isWhitespace)
                && value.chars().anyMatch(Character::isUpperCase)
                && value.chars().anyMatch(Character::isLowerCase)
                && value.chars().anyMatch(Character::isDigit)
                && value.chars().anyMatch(c -> !Character.isLetterOrDigit(c));
    }
}
