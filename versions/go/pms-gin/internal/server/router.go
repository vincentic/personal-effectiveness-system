package server

import (
	"html/template"
	"io/fs"
	"net/http"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/config"
)

// Server 聚合配置、模板集、静态资源与密钥。
type Server struct {
	cfg           config.Config
	templates     map[string]*template.Template
	staticFS      fs.FS
	sessionSecret string
	apiSecret     string
	cookieSecure  bool
}

// New 构造 Server：预解析全部页面模板，并取 static 子目录作为静态资源根。
func New(cfg config.Config, templatesFS, staticFS fs.FS) (*Server, error) {
	tpls, err := newTemplates(templatesFS)
	if err != nil {
		return nil, err
	}
	sub, err := fs.Sub(staticFS, "static")
	if err != nil {
		return nil, err
	}
	return &Server{
		cfg:           cfg,
		templates:     tpls,
		staticFS:      sub,
		sessionSecret: cfg.SessionSecret,
		apiSecret:     cfg.APISecret,
		cookieSecure:  cfg.CookieSecure,
	}, nil
}

// Router 注册页面路由与 /api/ 路由。
// 说明：gin 路由树不允许同一层既有静态段又有通配段，因此 /xxx/new/ 合并进
// /xxx/:id/ 内部分流（id == "new"），行为与独立路由完全一致。
func (s *Server) Router() *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(s.csrfMiddleware())
	r.StaticFS("/static", http.FS(s.staticFS))

	// 登录 / 退出（公开）
	r.GET("/login", s.loginGET)
	r.POST("/login", s.loginPOST)
	r.POST("/logout", s.logoutPOST)

	// 视图模式切换（公开，GET 幂等，豁免 CSRF；与 /api/** 一样不要求登录）
	r.GET("/view/:mode/", s.viewSwitch)

	// 业务页面（统一要求登录）
	page := r.Group("")
	page.Use(s.requireLogin)
	page.GET("/", s.dashboardGET)

	page.GET("/daily/", s.dailyListGET)
	page.GET("/daily/:id/", s.dailyEntryGET)
	page.POST("/daily/:id/", s.dailyNewPOST)
	page.GET("/daily/:id/edit/", s.dailyEditGET)
	page.POST("/daily/:id/edit/", s.dailyEditPOST)
	page.GET("/daily/:id/delete/", s.dailyDeleteGET)

	page.GET("/weekly/", s.weeklyListGET)
	page.GET("/weekly/:id/", s.weeklyEntryGET)
	page.POST("/weekly/:id/", s.weeklyNewPOST)
	page.GET("/weekly/:id/edit/", s.weeklyEditGET)
	page.POST("/weekly/:id/edit/", s.weeklyEditPOST)
	page.GET("/weekly/:id/delete/", s.weeklyDeleteGET)

	page.GET("/monthly/", s.monthlyListGET)
	page.GET("/monthly/:id/", s.monthlyEntryGET)
	page.POST("/monthly/:id/", s.monthlyNewPOST)
	page.GET("/monthly/:id/edit/", s.monthlyEditGET)
	page.POST("/monthly/:id/edit/", s.monthlyEditPOST)
	page.GET("/monthly/:id/delete/", s.monthlyDeleteGET)

	page.GET("/admin/users/", s.adminUsersGET)
	page.POST("/admin/users/", s.adminUsersPOST)

	// REST API（豁免 CSRF，除 login 外均需 Bearer Token）
	api := r.Group("/api")
	api.POST("/auth/login", s.apiLogin)
	authed := api.Group("")
	authed.Use(s.apiAuth)
	authed.GET("/me", s.apiMe)
	authed.GET("/dashboard", s.apiDashboard)

	authed.GET("/daily", s.apiDailyList)
	authed.POST("/daily", s.apiDailyCreate)
	authed.GET("/daily/:id", s.apiDailyGet)
	authed.PUT("/daily/:id", s.apiDailyUpdate)
	authed.DELETE("/daily/:id", s.apiDailyDelete)

	authed.GET("/weekly", s.apiWeeklyList)
	authed.POST("/weekly", s.apiWeeklyCreate)
	authed.GET("/weekly/:id", s.apiWeeklyGet)
	authed.PUT("/weekly/:id", s.apiWeeklyUpdate)
	authed.DELETE("/weekly/:id", s.apiWeeklyDelete)

	authed.GET("/monthly", s.apiMonthlyList)
	authed.POST("/monthly", s.apiMonthlyCreate)
	authed.GET("/monthly/:id", s.apiMonthlyGet)
	authed.PUT("/monthly/:id", s.apiMonthlyUpdate)
	authed.DELETE("/monthly/:id", s.apiMonthlyDelete)

	authed.GET("/users", s.apiUsersList)
	authed.POST("/users", s.apiUsersCreate)

	return r
}
