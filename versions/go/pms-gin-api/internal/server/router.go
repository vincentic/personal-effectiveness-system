package server

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"pms-gin-api/internal/config"
)

type Server struct {
	apiSecret   string
	corsOrigins string
}

func New(cfg config.Config) *Server {
	return &Server{
		apiSecret:   cfg.APISecret,
		corsOrigins: cfg.CORSOrigins,
	}
}

func (s *Server) Router() *gin.Engine {
	r := gin.New()
	r.Use(gin.Logger(), gin.Recovery(), apiCORSMiddleware(s.corsOrigins))

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

func apiCORSMiddleware(origins string) gin.HandlerFunc {
	allowed := map[string]bool{}
	for _, origin := range strings.Split(origins, ",") {
		if origin = strings.TrimSpace(origin); origin != "" {
			allowed[origin] = true
		}
	}

	return func(c *gin.Context) {
		origin := c.GetHeader("Origin")
		if !strings.HasPrefix(c.Request.URL.Path, "/api/") || origin == "" {
			c.Next()
			return
		}
		if !allowed["*"] && !allowed[origin] {
			if c.Request.Method == http.MethodOptions {
				c.AbortWithStatus(http.StatusForbidden)
				return
			}
			c.Next()
			return
		}
		c.Header("Vary", "Origin")
		if allowed["*"] {
			c.Header("Access-Control-Allow-Origin", "*")
		} else {
			c.Header("Access-Control-Allow-Origin", origin)
		}
		if c.Request.Method == http.MethodOptions {
			c.Header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
			c.Header("Access-Control-Allow-Headers", "Authorization, Content-Type")
			c.Header("Access-Control-Max-Age", "600")
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}
