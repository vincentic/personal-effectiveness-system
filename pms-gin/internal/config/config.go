// Package config 提供极简 .env 加载与运行期配置。
package config

import (
	"os"
	"strings"
)

// Config 汇总所有运行期配置。系统环境变量优先于 .env 文件。
type Config struct {
	DBName     string
	DBUser     string
	DBPassword string
	DBHost     string
	DBPort     string

	ServerPort string

	SessionSecret string
	APISecret     string

	AdminUsername string
	AdminPassword string

	CookieSecure bool
}

// Load 读取工作目录下的 .env（KEY=VALUE，# 为注释），随后组装配置。
func Load() Config {
	loadDotenv(".env")
	return Config{
		DBName:        env("DB_NAME", "pms"),
		DBUser:        env("DB_USER", "root"),
		DBPassword:    env("DB_PASSWORD", ""),
		DBHost:        env("DB_HOST", "127.0.0.1"),
		DBPort:        env("DB_PORT", "3306"),
		ServerPort:    env("SERVER_PORT", "8081"),
		SessionSecret: env("SESSION_SECRET", "dev-insecure-session-secret-change-me"),
		APISecret:     env("API_SECRET", "dev-insecure-api-secret-change-me"),
		AdminUsername: env("ADMIN_USERNAME", "admin"),
		AdminPassword: env("ADMIN_PASSWORD", "admin123"),
		CookieSecure:  boolEnv("COOKIE_SECURE", false),
	}
}

// loadDotenv 逐行解析 KEY=VALUE；仅当系统环境变量未设置时才写入进程环境。
func loadDotenv(path string) {
	data, err := os.ReadFile(path)
	if err != nil {
		return
	}
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, "#") || !strings.Contains(line, "=") {
			continue
		}
		key, value, _ := strings.Cut(line, "=")
		key = strings.TrimSpace(key)
		if key == "" {
			continue
		}
		if _, exists := os.LookupEnv(key); exists {
			continue // 系统环境变量优先
		}
		value = strings.Trim(strings.TrimSpace(value), "\"'")
		_ = os.Setenv(key, value)
	}
}

func env(key, def string) string {
	if v, ok := os.LookupEnv(key); ok && v != "" {
		return v
	}
	return def
}

func boolEnv(key string, def bool) bool {
	v, ok := os.LookupEnv(key)
	if !ok || strings.TrimSpace(v) == "" {
		return def
	}
	switch strings.ToLower(strings.TrimSpace(v)) {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	}
	return def
}
