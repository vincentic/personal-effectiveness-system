package com.example.pms.web;

import com.example.pms.repo.UserRepository;
import com.example.pms.web.dto.UserForm;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 轻量用户管理（仅管理员），替代 Django Admin 的成员维护能力。 */
@Controller
public class AdminController {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminController(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @GetMapping("/admin/users/")
    public String list(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new UserForm());
        }
        model.addAttribute("users", users.findAll());
        return "admin_users";
    }

    @PostMapping("/admin/users/")
    public String create(@Valid @ModelAttribute("form") UserForm form,
                         BindingResult errors, Model model, RedirectAttributes flash) {
        String username = form.getUsername().trim();
        if (!username.isEmpty() && users.existsByUsername(username)) {
            errors.rejectValue("username", "duplicate", "用户名已存在");
        }
        if (errors.hasErrors()) {
            model.addAttribute("users", users.findAll());
            return "admin_users";
        }
        users.create(username, encoder.encode(form.getPassword()),
                form.getDisplayName().isBlank() ? username : form.getDisplayName().trim(),
                form.isStaff());
        flash.addFlashAttribute("msg", "已创建用户 " + username);
        return "redirect:/admin/users/";
    }
}
