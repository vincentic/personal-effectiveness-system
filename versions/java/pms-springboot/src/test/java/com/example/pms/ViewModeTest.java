package com.example.pms;

import com.example.pms.support.ViewMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 双端模式判定纯函数单测，不依赖 Spring 容器与数据库。 */
class ViewModeTest {

    private static final String DESKTOP_UA =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    @Test
    void uaRegexMatchesAllMobileFeaturesCaseInsensitively() {
        assertTrue(ViewMode.isMobileUA("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"));
        assertTrue(ViewMode.isMobileUA("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Mobile Safari"));
        assertTrue(ViewMode.isMobileUA("iPad"));
        assertTrue(ViewMode.isMobileUA("iPod touch"));
        assertTrue(ViewMode.isMobileUA("Windows Phone"));
        assertTrue(ViewMode.isMobileUA("BlackBerry"));
        assertTrue(ViewMode.isMobileUA("Opera Mini"));
        assertTrue(ViewMode.isMobileUA("IEMobile"));
        assertTrue(ViewMode.isMobileUA("MOBILE"));
        assertFalse(ViewMode.isMobileUA(DESKTOP_UA));
        assertFalse(ViewMode.isMobileUA(null));
        assertFalse(ViewMode.isMobileUA(""));
    }

    @Test
    void prefNormalizesToPcMobileOrAuto() {
        assertEquals("pc", ViewMode.pref("pc"));
        assertEquals("mobile", ViewMode.pref("MOBILE"));
        assertEquals("auto", ViewMode.pref("auto"));
        assertEquals("auto", ViewMode.pref(null));
        assertEquals("auto", ViewMode.pref(""));
        assertEquals("auto", ViewMode.pref("//evil.com"));
    }

    @Test
    void effectiveModeHonorsManualChoiceThenUa() {
        assertEquals("pc", ViewMode.effective("pc", "iPhone"));
        assertEquals("mobile", ViewMode.effective("mobile", DESKTOP_UA));
        assertEquals("mobile", ViewMode.effective("auto", "iPhone"));
        assertEquals("pc", ViewMode.effective("auto", DESKTOP_UA));
        assertEquals("pc", ViewMode.effective(null, null));
    }

    @Test
    void pageAddsPrefixOnlyForMobile() {
        assertEquals("m/daily/list", ViewMode.page("mobile", "daily/list"));
        assertEquals("daily/list", ViewMode.page("pc", "daily/list"));
        assertEquals("m/dashboard", ViewMode.page("mobile", "dashboard"));
        assertEquals("admin_users", ViewMode.page("pc", "admin_users"));
    }

    @Test
    void openRedirectTargetsAreRejected() {
        // 与 ViewController.safeTarget 相同的判定规则：只接受 / 开头、非 // 的站内路径。
        assertTrue(isSafe("/daily/"));
        assertTrue(isSafe("/view/mobile/?next=/daily/"));
        assertFalse(isSafe("//evil.com"));
        assertFalse(isSafe("http://evil.com"));
        assertFalse(isSafe("/\\evil.com"));
        assertFalse(isSafe("daily/"));
        assertFalse(isSafe(null));
    }

    private static boolean isSafe(String path) {
        return path != null && path.startsWith("/") && !path.startsWith("//")
                && !path.startsWith("/\\") && path.indexOf('\n') < 0 && path.indexOf('\r') < 0;
    }
}
