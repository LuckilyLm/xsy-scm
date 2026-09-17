package net.lab1024.sa.base.module.support.file.service;

/** Strict, canonical object keys. Reject path aliases before local/S3 URL construction. */
public final class FileKeyPolicy {
    private FileKeyPolicy() { }

    public static boolean isValid(String key) {
        if (key == null || !(key.startsWith("public/") || key.startsWith("private/"))) {
            return false;
        }
        if (key.indexOf('\\') >= 0 || key.indexOf('%') >= 0 || key.indexOf('?') >= 0
                || key.indexOf('#') >= 0 || key.indexOf(',') >= 0
                || key.chars().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))) {
            return false;
        }
        for (String segment : key.split("/")) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                return false;
            }
        }
        return true;
    }
}
