package server

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestAPICORSAllowsConfiguredOrigin(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(apiCORSMiddleware("http://localhost:5173,http://127.0.0.1:5173"))
	router.GET("/api/me", func(c *gin.Context) { c.Status(http.StatusOK) })

	req := httptest.NewRequest(http.MethodGet, "/api/me", nil)
	req.Header.Set("Origin", "http://localhost:5173")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, req)

	if res.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", res.Code, http.StatusOK)
	}
	if got := res.Header().Get("Access-Control-Allow-Origin"); got != "http://localhost:5173" {
		t.Fatalf("allow-origin = %q", got)
	}
}

func TestAPICORSHandlesPreflightAndRejectsUnknownOrigin(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.Use(apiCORSMiddleware("http://localhost:5173"))
	router.GET("/api/me", func(c *gin.Context) { c.Status(http.StatusOK) })

	preflight := httptest.NewRequest(http.MethodOptions, "/api/me", nil)
	preflight.Header.Set("Origin", "http://localhost:5173")
	preflight.Header.Set("Access-Control-Request-Method", "GET")
	res := httptest.NewRecorder()
	router.ServeHTTP(res, preflight)
	if res.Code != http.StatusNoContent {
		t.Fatalf("preflight status = %d, want %d", res.Code, http.StatusNoContent)
	}
	if got := res.Header().Get("Access-Control-Allow-Headers"); got != "Authorization, Content-Type" {
		t.Fatalf("allow-headers = %q", got)
	}

	rejected := httptest.NewRequest(http.MethodOptions, "/api/me", nil)
	rejected.Header.Set("Origin", "https://untrusted.example")
	res = httptest.NewRecorder()
	router.ServeHTTP(res, rejected)
	if res.Code != http.StatusForbidden {
		t.Fatalf("unknown-origin status = %d, want %d", res.Code, http.StatusForbidden)
	}
}
