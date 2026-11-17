package main

import (
	"embed"
	"log"

	"pms-gin/internal/config"
	"pms-gin/internal/database"
	"pms-gin/internal/server"
)

//go:embed sql/schema.sql
var schemaSQL string

//go:embed templates/*.html
var templatesFS embed.FS

//go:embed all:static
var staticFS embed.FS

func main() {
	cfg := config.Load()
	if err := database.Open(cfg, schemaSQL); err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer database.Close()

	srv, err := server.New(cfg, templatesFS, staticFS)
	if err != nil {
		log.Fatalf("服务初始化失败: %v", err)
	}
	addr := ":" + cfg.ServerPort
	log.Printf("PMS Gin 版启动：http://127.0.0.1%s", addr)
	if err := srv.Router().Run(addr); err != nil {
		log.Fatalf("服务退出: %v", err)
	}
}
