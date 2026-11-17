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
// 说明：gin 路由树不允许同一层既有静态段又有通配段，因此 /projects/new/ 合并进
// /projects/:id/ 内部分流（id == "new"），行为与独立路由完全一致。
func (s *Server) Router() *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery())
	r.Use(s.csrfMiddleware())
	r.StaticFS("/static", http.FS(s.staticFS))

	// 登录 / 退出（公开）
	r.GET("/login", s.loginGET)
	r.POST("/login", s.loginPOST)
	r.POST("/logout", s.logoutPOST)

	// 业务页面（统一要求登录）
	page := r.Group("")
	page.Use(s.requireLogin)
	page.GET("/", s.dashboardGET)
	page.GET("/projects/", s.projectListGET)
	page.GET("/projects/:id/", s.projectEntryGET)
	page.POST("/projects/:id/", s.projectEntryPOST)
	page.GET("/projects/:id/edit/", s.projectEditGET)
	page.POST("/projects/:id/edit/", s.projectEditPOST)
	page.GET("/projects/:id/delete/", s.projectDeleteGET)
	page.GET("/projects/:id/tasks/new/", s.taskNewGET)
	page.POST("/projects/:id/tasks/new/", s.taskNewPOST)
	page.GET("/tasks/:id/edit/", s.taskEditGET)
	page.POST("/tasks/:id/edit/", s.taskEditPOST)
	page.GET("/tasks/:id/delete/", s.taskDeleteGET)
	page.GET("/admin/users/", s.adminUsersGET)
	page.POST("/admin/users/", s.adminUsersPOST)

	// REST API（豁免 CSRF，除 login 外均需 Bearer Token）
	api := r.Group("/api")
	api.POST("/auth/login", s.apiLogin)
	authed := api.Group("")
	authed.Use(s.apiAuth)
	authed.GET("/me", s.apiMe)
	authed.GET("/dashboard", s.apiDashboard)
	authed.GET("/projects", s.apiProjectsList)
	authed.POST("/projects", s.apiProjectsCreate)
	authed.GET("/projects/:id", s.apiProjectGet)
	authed.PUT("/projects/:id", s.apiProjectUpdate)
	authed.DELETE("/projects/:id", s.apiProjectDelete)
	authed.POST("/projects/:id/tasks", s.apiTaskCreate)
	authed.PUT("/tasks/:id", s.apiTaskUpdate)
	authed.DELETE("/tasks/:id", s.apiTaskDelete)
	authed.GET("/users", s.apiUsersList)
	authed.POST("/users", s.apiUsersCreate)

	return r
}
