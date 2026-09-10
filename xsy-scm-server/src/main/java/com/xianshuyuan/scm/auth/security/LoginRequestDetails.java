package com.xianshuyuan.scm.auth.security;

/**
 * Only request metadata needed by the audit trail; never contains credentials or session IDs.
 */
public record LoginRequestDetails(String ip, String userAgent) {
}
