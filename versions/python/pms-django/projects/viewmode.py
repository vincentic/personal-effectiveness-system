"""PC / Mobile 双端视图模式工具（三版统一规范中的 Django 实现）。"""
import re
from urllib.parse import urlsplit

from django.shortcuts import redirect, render

COOKIE_NAME = "pms_view"
VALID_PREFS = ("auto", "pc", "mobile")
ONE_YEAR = 31536000

# 移动特征（大小写不敏感），与 docs/guides/dual-view-guidelines.md 中的正则保持一致
MOBILE_UA_RE = re.compile(
    r"Android|iPhone|iPad|iPod|Mobile|Windows Phone|BlackBerry|Opera Mini|IEMobile",
    re.IGNORECASE,
)

# 供移动模板底部 Tab 高亮使用
_TAB_BY_URL_NAME = {
    "dashboard": "dashboard",
    "daily_list": "daily",
    "daily_create": "daily",
    "daily_update": "daily",
    "daily_delete": "daily",
    "daily_toggle_block": "daily",
    "weekly_list": "weekly",
    "weekly_create": "weekly",
    "weekly_update": "weekly",
    "weekly_delete": "weekly",
    "monthly_list": "monthly",
    "monthly_create": "monthly",
    "monthly_update": "monthly",
    "monthly_delete": "monthly",
}


def get_view_pref(request):
    """读取用户偏好 pms_view，非法值按 auto 处理。"""
    pref = request.COOKIES.get(COOKIE_NAME, "auto")
    return pref if pref in VALID_PREFS else "auto"


def effective_mode(request):
    """返回最终生效模式：pc 或 mobile。auto 仅作为偏好值。"""
    pref = get_view_pref(request)
    if pref in ("pc", "mobile"):
        return pref
    ua = request.META.get("HTTP_USER_AGENT", "") or ""
    return "mobile" if MOBILE_UA_RE.search(ua) else "pc"


def render_page(request, name, context=None):
    """统一渲染入口：移动端渲染 m/<name>，PC 端渲染 <name>。

    同时向模板注入 view_mode（生效模式）、view_pref（偏好）与 active_tab。
    """
    ctx = dict(context or {})
    mode = effective_mode(request)
    ctx["view_mode"] = mode
    ctx["view_pref"] = get_view_pref(request)
    resolver_match = getattr(request, "resolver_match", None)
    url_name = resolver_match.url_name if resolver_match else ""
    ctx.setdefault("active_tab", _TAB_BY_URL_NAME.get(url_name, ""))
    template = f"m/{name}" if mode == "mobile" else name
    return render(request, template, ctx)


def _is_internal_path(path):
    """仅接受以 / 开头、不以 // 开头且不含反斜杠的站内路径。"""
    return (
        bool(path)
        and path.startswith("/")
        and not path.startswith("//")
        and "\\" not in path
    )


def _safe_next(request):
    """回跳目标：next → 同源 Referer path → /。"""
    candidate = request.GET.get("next", "") or ""
    if _is_internal_path(candidate):
        return candidate

    referer = request.META.get("HTTP_REFERER", "") or ""
    if referer:
        parts = urlsplit(referer)
        # 绝对 URL 的 host 必须与当前请求一致，避免 Referer 导致外跳
        if parts.netloc and parts.netloc.lower() != request.get_host().lower():
            return "/"
        if _is_internal_path(parts.path):
            query = f"?{parts.query}" if parts.query else ""
            return parts.path + query
    return "/"


def switch_view(request, mode):
    """切换视图偏好并 302 回跳。

    登录页需要在未登录状态下也能切换（移动端登录入口），故此处不加
    login_required；GET 幂等且只写 cookie，不会改变任何业务数据。
    """
    if mode not in VALID_PREFS:
        mode = "auto"
    response = redirect(_safe_next(request))
    response.set_cookie(
        COOKIE_NAME,
        mode,
        max_age=ONE_YEAR,
        path="/",
        samesite="Lax",
    )
    return response
