package com.example.pms.web;

import com.example.pms.support.Auth;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 所有页面共用的导航栏上下文。 */
@ControllerAdvice
public class GlobalModelAdvice {

    @ModelAttribute("username")
    public String username() {
        return Auth.username();
    }

    @ModelAttribute("authenticated")
    public boolean authenticated() {
        return Auth.isAuthenticated();
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin() {
        return Auth.isAdmin();
    }
}
