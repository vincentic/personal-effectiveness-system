package server

import (
	"net/http"
	"net/url"
	"regexp"
	"strings"

	"github.com/gin-gonic/gin"
)

// 三版统一的视图模式：auto 仅作偏好，最终生效模式只有 pc / mobile。
const (
	viewAuto   = "auto"
	viewPC     = "pc"
	viewMobile = "mobile"
)

// mobileUA 命中任一移动特征即判定为 mobile（大小写不敏感）。
var mobileUA = regexp.MustCompile(`(?i)Android|iPhone|iPad|iPod|Mobile|Windows Phone|BlackBerry|Opera Mini|IEMobile`)

// viewPref 读取用户视图偏好 cookie pms_view，缺省为 auto。
// 仅接受 pc / mobile / auto 三个合法值，非法值一律回落 auto。
func viewPref(c *gin.Context) string {
	v, err := c.Cookie("pms_view")
	if err != nil {
		return viewAuto
	}
	switch v {
	case viewPC, viewMobile, viewAuto:
		return v
	default:
		return viewAuto
	}
}

// effectiveMode 返回最终生效模式（pc / mobile）。
// auto 时按 User-Agent 正则判断，其余偏好原样生效。
func effectiveMode(c *gin.Context) string {
	pref := viewPref(c)
	if pref == viewAuto {
		if mobileUA.MatchString(c.GetHeader("User-Agent")) {
			return viewMobile
		}
		return viewPC
	}
	return pref
}

// pageName 按生效模式返回模板 key：mobile 时加 "m:" 前缀（对应 templates/m/ 下的独立模板集），
// PC 时返回原名。集中在此处避免每个 handler 重复判断。
func (s *Server) pageName(c *gin.Context, page string) string {
	if effectiveMode(c) == viewMobile {
		return "m:" + page
	}
	return page
}

// safeRedirect 只接受以 / 开头且非 // 开头的站内相对路径，防止开放重定向。
// 同时拦截反斜线开头的协议相对形式（/\evil.com）与控制字符。
func safeRedirect(s string) string {
	if s == "" || !strings.HasPrefix(s, "/") || strings.HasPrefix(s, "//") ||
		strings.HasPrefix(s, "/\\") || strings.ContainsAny(s, "\r\n") {
		return ""
	}
	return s
}

// viewSwitch 写 pms_view cookie 后 302 回来源页。
// 回跳目标优先级：query next（仅站内路径）→ Referer path → /。
// 该端点为 GET，天然豁免 CSRF 中间件校验。
func (s *Server) viewSwitch(c *gin.Context) {
	mode := c.Param("mode")
	switch mode {
	case viewPC, viewMobile, viewAuto:
	default:
		mode = viewAuto
	}
	c.SetSameSite(http.SameSiteLaxMode)
	c.SetCookie("pms_view", mode, 31536000, "/", "", false, false)

	target := safeRedirect(c.Query("next"))
	if target == "" {
		if ref := c.GetHeader("Referer"); ref != "" {
			if u, err := url.Parse(ref); err == nil {
				p := u.Path
				if u.RawQuery != "" {
					p += "?" + u.RawQuery
				}
				target = safeRedirect(p)
			}
		}
	}
	if target == "" {
		target = "/"
	}
	c.Redirect(http.StatusFound, target)
}
