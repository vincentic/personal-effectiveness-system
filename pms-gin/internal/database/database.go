// Package database 负责 MySQL 连接、幂等建表、默认管理员与通用查询。
package database

import (
	"database/sql"
	"net"
	"time"

	"github.com/go-sql-driver/mysql"
	"golang.org/x/crypto/bcrypt"

	"pms-gin/internal/config"
)

// DB 为全局连接池；所有查询均通过 Rows 复用动态扫描逻辑。
var DB *sql.DB

// Open 建立连接、一次 Exec 建表，并在 users 为空时创建默认管理员。
func Open(cfg config.Config, schemaSQL string) error {
	mc := mysql.NewConfig()
	mc.User = cfg.DBUser
	mc.Passwd = cfg.DBPassword
	mc.Net = "tcp"
	mc.Addr = net.JoinHostPort(cfg.DBHost, cfg.DBPort)
	mc.DBName = cfg.DBName
	mc.Params = map[string]string{"charset": "utf8mb4"}
	mc.Loc = time.Local
	mc.ParseTime = true
	mc.MultiStatements = true
	mc.AllowNativePasswords = true

	db, err := sql.Open("mysql", mc.FormatDSN())
	if err != nil {
		return err
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	if err = db.Ping(); err != nil {
		_ = db.Close()
		return err
	}
	DB = db

	// multiStatements=true：允许一次 Exec 执行整段建表脚本（含 -- 注释）。
	if _, err = DB.Exec(schemaSQL); err != nil {
		return err
	}

	var n int64
	if err = DB.QueryRow("SELECT COUNT(*) FROM users").Scan(&n); err != nil {
		return err
	}
	if n == 0 {
		hash, err := HashPassword(cfg.AdminPassword)
		if err != nil {
			return err
		}
		if _, err = DB.Exec(
			"INSERT INTO users (username, password_hash, display_name, is_staff) VALUES (?, ?, ?, ?)",
			cfg.AdminUsername, hash, "系统管理员", 1,
		); err != nil {
			return err
		}
	}
	return nil
}

// Close 关闭连接池。
func Close() {
	if DB != nil {
		_ = DB.Close()
	}
}

// Rows 通用查询：rows.Columns() 动态扫描，统一清洗为可 JSON 序列化的值。
// []byte -> string，time.Time -> "2006-01-02 15:04:05"，nil -> nil。
func Rows(query string, args ...any) ([]map[string]any, error) {
	r, err := DB.Query(query, args...)
	if err != nil {
		return nil, err
	}
	defer r.Close()

	cols, err := r.Columns()
	if err != nil {
		return nil, err
	}
	out := make([]map[string]any, 0)
	for r.Next() {
		vals := make([]any, len(cols))
		ptrs := make([]any, len(cols))
		for i := range vals {
			ptrs[i] = &vals[i]
		}
		if err = r.Scan(ptrs...); err != nil {
			return nil, err
		}
		row := make(map[string]any, len(cols))
		for i, c := range cols {
			row[c] = normalize(vals[i])
		}
		out = append(out, row)
	}
	if err = r.Err(); err != nil {
		return nil, err
	}
	return out, nil
}

// One 返回首行；无结果时返回 (nil, nil)。
func One(query string, args ...any) (map[string]any, error) {
	rows, err := Rows(query, args...)
	if err != nil || len(rows) == 0 {
		return nil, err
	}
	return rows[0], nil
}

// Exec 执行写操作。
func Exec(query string, args ...any) (sql.Result, error) {
	return DB.Exec(query, args...)
}

// InsertID 执行 INSERT 并返回 LastInsertId。
func InsertID(query string, args ...any) (int64, error) {
	res, err := Exec(query, args...)
	if err != nil {
		return 0, err
	}
	return res.LastInsertId()
}

func normalize(v any) any {
	switch x := v.(type) {
	case nil:
		return nil
	case []byte:
		return string(x)
	case time.Time:
		return x.Format("2006-01-02 15:04:05")
	default:
		return x
	}
}

// HashPassword 生成 BCrypt 哈希（超过 72 字节按 BCrypt 限制截断）。
func HashPassword(password string) (string, error) {
	b := []byte(password)
	if len(b) > 72 {
		b = b[:72]
	}
	h, err := bcrypt.GenerateFromPassword(b, bcrypt.DefaultCost)
	if err != nil {
		return "", err
	}
	return string(h), nil
}

// VerifyPassword 校验明文密码与 BCrypt 哈希。
func VerifyPassword(password, hash string) bool {
	b := []byte(password)
	if len(b) > 72 {
		b = b[:72]
	}
	return bcrypt.CompareHashAndPassword([]byte(hash), b) == nil
}
