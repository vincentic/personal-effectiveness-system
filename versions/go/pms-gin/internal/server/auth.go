package server

import (
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"fmt"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

// Session 为页面登录态（载荷 uid|username|staff|exp）。
type Session struct {
	UID      int64
	Username string
	Staff    bool
}

// ---------- 通用签名工具（会话 Cookie 与 API Token 共用） ----------

func hmacHex(secret, payload string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	_, _ = mac.Write([]byte(payload))
	return hex.EncodeToString(mac.Sum(nil))
}

func b64encode(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}

func b64decode(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

// ctEq 常量时间比较，避免时序侧信道。
func ctEq(a, b string) bool {
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

// ---------- 会话签名 Cookie ----------

func (s *Server) issueSession(sess Session) string {
	exp := time.Now().Add(24 * time.Hour).Unix()
	payload := fmt.Sprintf("%d|%s|%t|%d", sess.UID, sess.Username, sess.Staff, exp)
	encoded := b64encode([]byte(payload))
	return encoded + "." + hmacHex(s.sessionSecret, encoded)
}

func (s *Server) verifySession(token string) (*Session, bool) {
	if token == "" {
		return nil, false
	}
	encoded, sig, ok := strings.Cut(token, ".")
	if !ok || encoded == "" || sig == "" {
		return nil, false
	}
	if !ctEq(hmacHex(s.sessionSecret, encoded), sig) {
		return nil, false
	}
	raw, err := b64decode(encoded)
	if err != nil {
		return nil, false
	}
	parts := strings.SplitN(string(raw), "|", 4)
	if len(parts) != 4 {
		return nil, false
	}
	uid, err := strconv.ParseInt(parts[0], 10, 64)
	if err != nil {
		return nil, false
	}
	staff, err := strconv.ParseBool(parts[2])
	if err != nil {
		return nil, false
	}
	exp, err := strconv.ParseInt(parts[3], 10, 64)
	if err != nil || exp <= time.Now().Unix() {
		return nil, false
	}
	return &Session{UID: uid, Username: parts[1], Staff: staff}, true
}

func (s *Server) setCookie(c *gin.Context, name, value string, maxAge int, httpOnly bool) {
	c.SetSameSite(http.SameSiteLaxMode)
	c.SetCookie(name, value, maxAge, "/", "", s.cookieSecure, httpOnly)
}

func (s *Server) setSession(c *gin.Context, sess Session) {
	s.setCookie(c, "pms_session", s.issueSession(sess), 86400, true)
	c.Set("session", &sess)
}

func (s *Server) clearSession(c *gin.Context) {
	s.setCookie(c, "pms_session", "", -1, true)
	c.Set("session", (*Session)(nil))
}

// currentSession 读取并校验会话 Cookie，结果缓存在 gin.Context 中。
func (s *Server) currentSession(c *gin.Context) *Session {
	if v, exists := c.Get("session"); exists {
		sess, _ := v.(*Session)
		return sess
	}
	token, err := c.Cookie("pms_session")
	if err != nil {
		c.Set("session", (*Session)(nil))
		return nil
	}
	sess, ok := s.verifySession(token)
	if !ok {
		c.Set("session", (*Session)(nil))
		return nil
	}
	c.Set("session", sess)
	return sess
}

func (s *Server) requireLogin(c *gin.Context) {
	if s.currentSession(c) == nil {
		c.Redirect(http.StatusFound, "/login")
		c.Abort()
	}
}

// ---------- CSRF（double submit cookie） ----------

// csrfValue 读取 pms_csrf；GET 首次访问时生成并写入 Cookie。
func (s *Server) csrfValue(c *gin.Context) string {
	if v, err := c.Cookie("pms_csrf"); err == nil && v != "" {
		return v
	}
	buf := make([]byte, 16)
	if _, err := rand.Read(buf); err != nil {
		return fmt.Sprintf("%x", time.Now().UnixNano())
	}
	v := hex.EncodeToString(buf)
	s.setCookie(c, "pms_csrf", v, 86400, false)
	return v
}

// csrfMiddleware 对所有非 /api/ 的非安全方法校验表单 _csrf 与 Cookie 是否一致。
func (s *Server) csrfMiddleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		method := c.Request.Method
		if method != http.MethodGet && method != http.MethodHead && method != http.MethodOptions &&
			!strings.HasPrefix(c.Request.URL.Path, "/api/") {
			cookie, err := c.Cookie("pms_csrf")
			form := c.PostForm("_csrf")
			if err != nil || cookie == "" || form == "" || !ctEq(cookie, form) {
				c.AbortWithStatus(http.StatusForbidden)
				return
			}
		}
		c.Next()
	}
}
