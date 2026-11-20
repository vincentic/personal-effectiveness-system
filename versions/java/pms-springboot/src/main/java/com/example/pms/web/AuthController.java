package com.example.pms.web;

import com.example.pms.support.Auth;
import com.example.pms.support.Views;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 登录页；POST /login 与 POST /logout 由 Spring Security 过滤器处理。 */
@Controller
public class AuthController {

    @GetMapping("/login")
    public String login(@RequestParam(value = "error", required = false) String error, Model model) {
        if (Auth.isAuthenticated()) {
            return "redirect:/";
        }
        model.addAttribute("loginError", error != null);
        return Views.page("login");
    }
}
