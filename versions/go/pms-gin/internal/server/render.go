package server

import (
	"bytes"
	"html/template"
	"io/fs"
	"net/http"
	"reflect"
	"strings"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/models"
)

var funcMap = template.FuncMap{
	"has":           has,
	"eq64":          eq64,
	"safe":          func(s string) template.HTML { return template.HTML(s) },
	"energyLabel":   models.EnergyLabel,
	"domainLabel":   models.DomainLabel,
	"statusLabel":   models.DomainStatusLabel,
	"bucketLabel":   models.BucketLabel,
	"worthLabel":    models.WorthLabel,
	"moduleLabel":   models.ModuleLabel,
	"list":          func(vals ...any) []any { return vals },
	"scoreSelected": func(p *int, n int) bool { return p != nil && *p == n },
	"hasPrefix":     strings.HasPrefix,
}

// newTemplates 为每页构造独立的 template set，避免 define 冲突。
// base.html 的 define 名为 "base.html"，恰与 ParseFS 按文件名生成的根模板名相同，
// 故先用与任何 define 都不同的自定义根名 Parse(base.html 文本) 注册 "base.html"，
// 再 ParseFS 加入页面文件（页面只 define "content"，与文件名不冲突）。
func newTemplates(templatesFS fs.FS) (map[string]*template.Template, error) {
	baseText, err := fs.ReadFile(templatesFS, "templates/base.html")
	if err != nil {
		return nil, err
	}
	mBaseText, err := fs.ReadFile(templatesFS, "templates/m/base.html")
	if err != nil {
		return nil, err
	}
	pages := []string{
		"login.html", "dashboard.html", "admin_users.html",
		"daily_list.html", "daily_detail.html", "daily_form.html",
		"weekly_list.html", "weekly_detail.html", "weekly_form.html",
		"monthly_list.html", "monthly_detail.html", "monthly_form.html",
	}
	out := make(map[string]*template.Template, len(pages)*2)
	// PC 页用 templates/base.html，移动页用 templates/m/base.html；
	// 两者 define 名相同（base.html），但各自是独立 template set，互不冲突。
	for _, page := range pages {
		root := template.New("pms-" + page).Funcs(funcMap)
		if _, err = root.Parse(string(baseText)); err != nil {
			return nil, err
		}
		if _, err = root.ParseFS(templatesFS, "templates/"+page); err != nil {
			return nil, err
		}
		out[page] = root
	}
	for _, page := range pages {
		root := template.New("pms-m-" + page).Funcs(funcMap)
		if _, err = root.Parse(string(mBaseText)); err != nil {
			return nil, err
		}
		if _, err = root.ParseFS(templatesFS, "templates/m/"+page); err != nil {
			return nil, err
		}
		out["m:"+page] = root
	}
	return out, nil
}

// has 判断 id 是否在 ids 中，用于多选成员回显。
func has(ids, id any) bool {
	want := models.ToInt64(id)
	rv := reflect.ValueOf(ids)
	if rv.Kind() != reflect.Slice && rv.Kind() != reflect.Array {
		return false
	}
	for i := 0; i < rv.Len(); i++ {
		if models.ToInt64(rv.Index(i).Interface()) == want {
			return true
		}
	}
	return false
}

func eq64(a, b any) bool {
	return models.ToInt64(a) == models.ToInt64(b)
}

// render 注入公共上下文并渲染指定页面（先写 buffer，避免出错时输出半页）。
func (s *Server) render(c *gin.Context, page, title string, data gin.H) {
	if data == nil {
		data = gin.H{}
	}
	data["Title"] = title
	data["CSRF"] = s.csrfValue(c)
	data["ViewMode"] = effectiveMode(c)
	data["ViewPref"] = viewPref(c)
	data["Path"] = c.Request.URL.Path
	sess := s.currentSession(c)
	data["Authenticated"] = sess != nil
	data["Username"] = ""
	data["IsAdmin"] = false
	if sess != nil {
		data["Username"] = sess.Username
		data["IsAdmin"] = sess.Staff
	}
	tpl := s.templates[page]
	var buf bytes.Buffer
	if err := tpl.ExecuteTemplate(&buf, "base.html", data); err != nil {
		c.String(http.StatusInternalServerError, "模板渲染失败: %v", err)
		return
	}
	c.Data(http.StatusOK, "text/html; charset=utf-8", buf.Bytes())
}
