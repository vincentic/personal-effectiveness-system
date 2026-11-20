package main

import (
	_ "embed"
	"log"

	"pms-gin-api/internal/config"
	"pms-gin-api/internal/database"
	"pms-gin-api/internal/server"
)

//go:embed sql/schema.sql
var schemaSQL string

func main() {
	cfg := config.Load()
	if err := database.Open(cfg, schemaSQL); err != nil {
		log.Fatalf("数据库初始化失败: %v", err)
	}
	defer database.Close()

	srv := server.New(cfg)
	addr := ":" + cfg.ServerPort
	log.Printf("PMS Gin API 启动：http://127.0.0.1%s", addr)
	if err := srv.Router().Run(addr); err != nil {
		log.Fatalf("服务退出: %v", err)
	}
}
