package com.example.pms.support;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前登录用户读取工具，对应 Django 的 request.user。 */
public final class Auth {

    private Auth() {
    }

    public static Authentication authentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static boolean isAuthenticated() {
        Authentication a = authentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
    }

    public static String username() {
        Authentication a = authentication();
        return isAuthenticated() && a != null ? a.getName() : "";
    }

    public static boolean isAdmin() {
        Authentication a = authentication();
        if (!isAuthenticated() || a == null) {
            return false;
        }
        return a.getAuthorities().stream().anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }
}
