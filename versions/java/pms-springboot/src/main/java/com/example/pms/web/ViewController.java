package com.example.pms.web;

import com.example.pms.support.ViewMode;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.net.URI;
import java.time.Duration;

/**
 * 视图模式切换端点：GET /view/{pc,mobile,auto}/，写 cookie 后 302 回来源页。
 * GET 幂等，CSRF 无需处理；端点本身沿用 Spring Security 默认的「需登录」边界。
 */
@Controller
public class ViewController {

    @GetMapping("/view/{mode}/")
    public String switchView(@PathVariable String mode,
                             @RequestParam(value = "next", required = false) String next,
                             @RequestHeader(value = "Referer", required = false) String referer,
                             HttpServletResponse response) {
        String pref = ViewMode.pref(mode);
        ResponseCookie cookie = ResponseCookie.from(ViewMode.COOKIE, pref)
                .path("/")
                .maxAge(Duration.ofDays(365))
                .sameSite("Lax")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        if (isSafePath(next)) return "redirect:" + next;
        String refererPath = pathOf(referer);
        if (isSafePath(refererPath)) return "redirect:" + refererPath;
        return "redirect:/";
    }

    /** 只接受以 / 开头且非 // 的站内相对路径，防止开放重定向。 */
    private static boolean isSafePath(String path) {
        return path != null
                && path.startsWith("/")
                && !path.startsWith("//")
                && !path.startsWith("/\\")
                && path.indexOf('\n') < 0
                && path.indexOf('\r') < 0;
    }

    /** 只取 Referer 的 path，丢弃 scheme/host，避免外跳。 */
    private static String pathOf(String referer) {
        if (referer == null || referer.isBlank()) return null;
        try {
            return URI.create(referer).getPath();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
