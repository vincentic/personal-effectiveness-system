package com.example.pms.web;

import com.example.pms.support.Auth;
import com.example.pms.support.ViewModeService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 所有页面共用的导航栏上下文。 */
@ControllerAdvice
public class GlobalModelAdvice {

    private final ViewModeService viewMode;

    public GlobalModelAdvice(ViewModeService viewMode) {
        this.viewMode = viewMode;
    }

    /** 当前生效模式：pc / mobile，供模板按模式微调。 */
    @ModelAttribute("view_mode")
    public String viewMode() {
        return viewMode.effectiveMode();
    }

    /** 用户偏好：auto / pc / mobile。 */
    @ModelAttribute("view_pref")
    public String viewPref() {
        return viewMode.pref();
    }

    /** 当前请求路径，供「手机版 / PC 版」切换链接作为 next 参数。 */
    @ModelAttribute("currentUri")
    public String currentUri() {
        return viewMode.currentPath();
    }

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
