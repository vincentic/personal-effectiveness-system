package com.example.pms.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.regex.Pattern;

/**
 * PC / Mobile 双端视图模式判定（三版统一规范，见 docs/guides/dual-view-guidelines.md）。
 *
 * <p>纯函数部分（pref / effective / isMobileUA / page）不依赖容器，便于单测；
 * current* 部分从 RequestContextHolder 读取当前请求的 cookie 与 User-Agent。</p>
 */
public final class ViewMode {

    public static final String COOKIE = "pms_view";
    public static final String AUTO = "auto";
    public static final String PC = "pc";
    public static final String MOBILE = "mobile";

    /** 移动端 UA 特征，大小写不敏感。 */
    private static final Pattern MOBILE_UA = Pattern.compile(
            "Android|iPhone|iPad|iPod|Mobile|Windows Phone|BlackBerry|Opera Mini|IEMobile",
            Pattern.CASE_INSENSITIVE);

    private ViewMode() {
    }

    /** 是否命中移动端 UA。 */
    public static boolean isMobileUA(String ua) {
        return ua != null && MOBILE_UA.matcher(ua).find();
    }

    /** 归一化偏好值；非 pc/mobile 一律回落到 auto。 */
    public static String pref(String raw) {
        if (PC.equalsIgnoreCase(raw)) return PC;
        if (MOBILE.equalsIgnoreCase(raw)) return MOBILE;
        return AUTO;
    }

    /** 生效模式只可能是 pc / mobile；auto 时按 UA 判断。 */
    public static String effective(String pref, String ua) {
        String p = pref(pref);
        if (PC.equals(p) || MOBILE.equals(p)) return p;
        return isMobileUA(ua) ? MOBILE : PC;
    }

    /** 视图名前缀：mobile 时加 m/，pc 保持原名。 */
    public static String page(String effectiveMode, String name) {
        if (name == null) return name;
        return MOBILE.equals(effectiveMode) ? "m/" + name : name;
    }

    /** 当前请求的偏好值（读 cookie pms_view，缺省 auto）。 */
    public static String currentPref() {
        HttpServletRequest request = currentRequest();
        return request == null ? AUTO : pref(readCookie(request, COOKIE));
    }

    /** 当前请求的 User-Agent。 */
    public static String currentUA() {
        HttpServletRequest request = currentRequest();
        return request == null ? "" : request.getHeader("User-Agent");
    }

    /** 当前请求路径，供模板拼接切换链接的 next 参数。 */
    public static String currentPath() {
        HttpServletRequest request = currentRequest();
        if (request == null) return "/";
        String uri = request.getRequestURI();
        return uri == null || uri.isEmpty() ? "/" : uri;
    }

    /** 当前请求的生效模式。 */
    public static String currentEffectiveMode() {
        return effective(currentPref(), currentUA());
    }

    private static HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes s
                ? s.getRequest() : null;
    }

    private static String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}
