package com.moldy.moldymarket.security.annotation;

import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.annotation.*;


@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF')")
public @interface IsAdminOrStaff {
}
