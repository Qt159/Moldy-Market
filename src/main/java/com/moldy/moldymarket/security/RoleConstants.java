package com.moldy.moldymarket.security;

public final class RoleConstants {

    private RoleConstants() {}

    public static final String USER = "USER";
    public static final String APPRAISER = "APPRAISER";
    public static final String STAFF      = "STAFF";
    public static final String ADMIN      = "ADMIN";
    public static final String OWNER      = "OWNER";

    // Dùng trong @PreAuthorize — Spring tự thêm prefix ROLE_
    // hasRole('ADMIN') → tìm authority 'ROLE_ADMIN'
}
