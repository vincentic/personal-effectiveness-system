package com.example.pms.support;

import org.springframework.stereotype.Component;

/** 供 @ControllerAdvice 注入模板上下文的视图模式服务。 */
@Component
public class ViewModeService {

    public String pref() {
        return ViewMode.currentPref();
    }

    public String effectiveMode() {
        return ViewMode.currentEffectiveMode();
    }

    public String page(String name) {
        return ViewMode.page(effectiveMode(), name);
    }

    public String currentPath() {
        return ViewMode.currentPath();
    }
}
