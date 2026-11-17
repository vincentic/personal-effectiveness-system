// Package models 提供视图模型、字段映射与业务数据访问（store.go）。
package models

import (
	"fmt"
	"html"
	"math"
	"strconv"
	"strings"
	"time"
)

// ---------- 通用清洗与转换 ----------

// str 把任意值转为字符串，nil 转为空串。
func str(v any) string {
	if v == nil {
		return ""
	}
	return fmt.Sprint(v)
}

// ToInt64 兼容 database/sql 返回的 int64 / float64 / []byte / string 等。
func ToInt64(v any) int64 {
	switch x := v.(type) {
	case nil:
		return 0
	case int64:
		return x
	case int:
		return int64(x)
	case int32:
		return int64(x)
	case float64:
		return int64(x)
	case float32:
		return int64(x)
	case bool:
		if x {
			return 1
		}
		return 0
	case string:
		n, _ := strconv.ParseInt(strings.TrimSpace(x), 10, 64)
		return n
	case []byte:
		n, _ := strconv.ParseInt(strings.TrimSpace(string(x)), 10, 64)
		return n
	default:
		return 0
	}
}

// DateOnly 把 DATE / DATETIME / time.Time / 字符串统一为 YYYY-MM-DD。
func DateOnly(v any) string {
	if v == nil {
		return ""
	}
	switch x := v.(type) {
	case time.Time:
		return x.Format("2006-01-02")
	case string:
		if len(x) >= 10 {
			return x[:10]
		}
		return x
	case []byte:
		s := string(x)
		if len(s) >= 10 {
			return s[:10]
		}
		return s
	default:
		return str(v)
	}
}

// Nl2br 先 html.EscapeString，再把换行替换为 <br>，供 safe() 安全输出。
func Nl2br(v any) string {
	if v == nil {
		return ""
	}
	s := html.EscapeString(str(v))
	s = strings.ReplaceAll(s, "\r\n", "\n")
	s = strings.ReplaceAll(s, "\n", "<br>")
	return s
}

// Truncate 按 rune 截断并追加省略号（对齐中文字符）。
func Truncate(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n]) + "…"
}

// Progress 读时计算完成百分比：done / total * 100，四舍五入。
func Progress(done, total any) int {
	t := ToInt64(total)
	if t == 0 {
		return 0
	}
	return int(math.Round(float64(ToInt64(done)) * 100.0 / float64(t)))
}

// ParseDate 校验 YYYY-MM-DD；空串返回 nil（SQL NULL）。
func ParseDate(s string) (any, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	t, err := time.Parse("2006-01-02", s)
	if err != nil {
		return nil, fmt.Errorf("日期格式应为 YYYY-MM-DD")
	}
	return t.Format("2006-01-02"), nil
}

// ---------- 枚举白名单与中文标签 / badge ----------

func ValidProjectStatus(s string) bool {
	return s == "active" || s == "paused" || s == "done"
}

func ValidTaskStatus(s string) bool {
	return s == "todo" || s == "doing" || s == "done"
}

func ValidPriority(s string) bool {
	return s == "low" || s == "medium" || s == "high"
}

func ProjectStatusLabel(code string) string {
	switch code {
	case "active":
		return "进行中"
	case "paused":
		return "已暂停"
	case "done":
		return "已完成"
	}
	return code
}

func ProjectStatusBadge(code string) string {
	switch code {
	case "active":
		return "bg-primary"
	case "paused":
		return "bg-warning text-dark"
	case "done":
		return "bg-success"
	}
	return "bg-secondary"
}

func TaskStatusLabel(code string) string {
	switch code {
	case "todo":
		return "待处理"
	case "doing":
		return "进行中"
	case "done":
		return "已完成"
	}
	return code
}

func PriorityLabel(code string) string {
	switch code {
	case "low":
		return "低"
	case "medium":
		return "中"
	case "high":
		return "高"
	}
	return code
}

func PriorityBadge(code string) string {
	switch code {
	case "high":
		return "bg-danger"
	case "medium":
		return "bg-info text-dark"
	}
	return "bg-secondary"
}

// ---------- 页面视图模型（大写别名，供 html/template 使用） ----------

// ProjectView 把 project 行加工成页面可直接渲染的视图。
func ProjectView(row map[string]any, currentUID int64) map[string]any {
	v := make(map[string]any, len(row)+16)
	for k, val := range row {
		v[k] = val
	}
	status := str(row["status"])
	v["ID"] = ToInt64(row["id"])
	v["Name"] = str(row["name"])
	v["OwnerID"] = ToInt64(row["owner_id"])
	v["OwnerName"] = str(row["owner_name"])
	v["StatusLabel"] = ProjectStatusLabel(status)
	v["StatusBadge"] = ProjectStatusBadge(status)
	v["TaskCount"] = ToInt64(row["task_count"])
	v["DoneTaskCount"] = ToInt64(row["done_task_count"])
	v["Progress"] = Progress(row["done_task_count"], row["task_count"])
	v["CreatedDate"] = DateOnly(row["created_at"])
	v["StartDate"] = DateOnly(row["start_date"])
	v["EndDate"] = DateOnly(row["end_date"])
	v["DescriptionHtml"] = Nl2br(row["description"])
	v["IsOwner"] = ToInt64(row["owner_id"]) == currentUID
	v["MemberNames"] = ""
	return v
}

// TaskView 把 task 行加工成看板卡片 / 待办列表视图。
func TaskView(row map[string]any) map[string]any {
	priority := str(row["priority"])
	return map[string]any{
		"ID":                ToInt64(row["id"]),
		"ProjectID":         ToInt64(row["project_id"]),
		"Title":             str(row["title"]),
		"AssigneeName":      str(row["assignee_name"]),
		"StatusLabel":       TaskStatusLabel(str(row["status"])),
		"PriorityLabel":     PriorityLabel(priority),
		"PriorityBadge":     PriorityBadge(priority),
		"DueDate":           DateOnly(row["due_date"]),
		"DescriptionShort":  Truncate(str(row["description"]), 50),
	}
}

// UserView 把 user 行加工成用户管理页视图。
func UserView(row map[string]any) map[string]any {
	staff := ToInt64(row["is_staff"]) != 0
	label, badge := "普通用户", "bg-secondary"
	if staff {
		label, badge = "管理员", "bg-primary"
	}
	return map[string]any{
		"ID":           ToInt64(row["id"]),
		"Username":     str(row["username"]),
		"DisplayName":  str(row["display_name"]),
		"IsStaff":      staff,
		"StaffLabel":   label,
		"StaffBadge":   badge,
		"CreatedDate":  DateOnly(row["created_at"]),
	}
}

// ---------- REST API 输出（snake_case，与 FastAPI 版字段一致） ----------

// ProjectOut 复制行、归一化日期、计算进度并附加 member_ids。
func ProjectOut(row map[string]any, memberIDs []int64) map[string]any {
	if row == nil {
		return nil
	}
	out := make(map[string]any, len(row)+3)
	for k, v := range row {
		out[k] = v
	}
	for _, k := range []string{"start_date", "end_date"} {
		if v, ok := out[k]; ok && v != nil {
			out[k] = DateOnly(v)
		}
	}
	out["task_count"] = ToInt64(row["task_count"])
	out["done_task_count"] = ToInt64(row["done_task_count"])
	out["progress"] = Progress(row["done_task_count"], row["task_count"])
	if memberIDs == nil {
		memberIDs = []int64{}
	}
	out["member_ids"] = memberIDs
	return out
}

// TaskOut 复制行并归一化 due_date。
func TaskOut(row map[string]any) map[string]any {
	if row == nil {
		return nil
	}
	out := make(map[string]any, len(row))
	for k, v := range row {
		out[k] = v
	}
	if v, ok := out["due_date"]; ok && v != nil {
		out["due_date"] = DateOnly(v)
	}
	return out
}

// UserOut 仅暴露安全字段，绝不包含 password_hash。
func UserOut(row map[string]any) map[string]any {
	if row == nil {
		return nil
	}
	return map[string]any{
		"id":           row["id"],
		"username":     row["username"],
		"display_name": row["display_name"],
		"is_staff":     row["is_staff"],
		"created_at":   row["created_at"],
	}
}
