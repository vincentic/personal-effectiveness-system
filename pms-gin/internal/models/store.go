package models

import (
	"fmt"
	"strings"

	"pms-gin/internal/database"
)

// 统一 SELECT：进度读时计算，任务按看板顺序排列。
const projectSelect = `SELECT p.*, u.username AS owner_name,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id) AS task_count,
       (SELECT COUNT(*) FROM tasks t WHERE t.project_id = p.id AND t.status = 'done') AS done_task_count
FROM projects p JOIN users u ON u.id = p.owner_id`

const taskSelect = `SELECT t.*, a.username AS assignee_name
FROM tasks t LEFT JOIN users a ON a.id = t.assignee_id`

const taskOrder = ` ORDER BY FIELD(t.status, 'todo', 'doing', 'done'),
         FIELD(t.priority, 'high', 'medium', 'low'),
         t.due_date IS NULL, t.due_date, t.id`

const userFields = "id, username, display_name, is_staff, created_at"

// placeholders 生成 n 个 ? 占位符，避免 fmt 与 SQL 中 % 冲突。
func placeholders(n int) string {
	if n <= 0 {
		return ""
	}
	return strings.TrimSuffix(strings.Repeat("?,", n), ",")
}

// ---------- 项目 ----------

func Projects(status, q string) ([]map[string]any, error) {
	query := projectSelect + ` WHERE (? = '' OR p.status = ?)
        AND (? = '' OR p.name LIKE CONCAT('%', ?, '%'))
        ORDER BY p.created_at DESC, p.id DESC`
	return database.Rows(query, status, status, q, q)
}

func Project(id int64) (map[string]any, error) {
	return database.One(projectSelect+" WHERE p.id = ?", id)
}

func ProjectExists(id int64) (bool, error) {
	row, err := database.One("SELECT id FROM projects WHERE id = ?", id)
	return row != nil, err
}

func ProjectMemberIDs(pid int64) ([]int64, error) {
	rows, err := database.Rows(
		"SELECT user_id FROM project_members WHERE project_id = ? ORDER BY user_id", pid)
	if err != nil {
		return nil, err
	}
	ids := make([]int64, 0, len(rows))
	for _, r := range rows {
		ids = append(ids, ToInt64(r["user_id"]))
	}
	return ids, nil
}

func ProjectMemberNames(pid int64) ([]string, error) {
	rows, err := database.Rows(
		`SELECT u.username AS username FROM project_members pm
         JOIN users u ON u.id = pm.user_id WHERE pm.project_id = ? ORDER BY u.id`, pid)
	if err != nil {
		return nil, err
	}
	names := make([]string, 0, len(rows))
	for _, r := range rows {
		names = append(names, str(r["username"]))
	}
	return names, nil
}

// SetMembers 先清空再写入，IGNORE 防止重复主键报错。
func SetMembers(pid int64, ids []int64) error {
	if _, err := database.Exec("DELETE FROM project_members WHERE project_id = ?", pid); err != nil {
		return err
	}
	for _, id := range ids {
		if _, err := database.Exec(
			"INSERT IGNORE INTO project_members (project_id, user_id) VALUES (?, ?)", pid, id); err != nil {
			return err
		}
	}
	return nil
}

func InsertProject(name, description, status string, startDate, endDate any, ownerID int64) (int64, error) {
	return database.InsertID(
		"INSERT INTO projects (name, description, status, start_date, end_date, owner_id) VALUES (?, ?, ?, ?, ?, ?)",
		name, description, status, startDate, endDate, ownerID)
}

func UpdateProject(id int64, name, description, status string, startDate, endDate any) error {
	_, err := database.Exec(
		`UPDATE projects SET name = ?, description = ?, status = ?, start_date = ?, end_date = ?,
         updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
		name, description, status, startDate, endDate, id)
	return err
}

func DeleteProject(id int64) error {
	_, err := database.Exec("DELETE FROM projects WHERE id = ?", id)
	return err
}

// ---------- 任务 ----------

func TasksByProject(pid int64) ([]map[string]any, error) {
	return database.Rows(taskSelect+" WHERE t.project_id = ?"+taskOrder, pid)
}

func Task(id int64) (map[string]any, error) {
	return database.One(taskSelect+" WHERE t.id = ?", id)
}

func TaskExists(id int64) (bool, error) {
	row, err := database.One("SELECT id FROM tasks WHERE id = ?", id)
	return row != nil, err
}

func InsertTask(pid int64, title, description string, assigneeID any, status, priority string, dueDate any) (int64, error) {
	return database.InsertID(
		`INSERT INTO tasks (project_id, title, description, assignee_id, status, priority, due_date)
         VALUES (?, ?, ?, ?, ?, ?, ?)`,
		pid, title, description, assigneeID, status, priority, dueDate)
}

func UpdateTask(id int64, title, description string, assigneeID any, status, priority string, dueDate any) error {
	_, err := database.Exec(
		`UPDATE tasks SET title = ?, description = ?, assignee_id = ?, status = ?, priority = ?,
         due_date = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?`,
		title, description, assigneeID, status, priority, dueDate, id)
	return err
}

func DeleteTask(id int64) error {
	_, err := database.Exec("DELETE FROM tasks WHERE id = ?", id)
	return err
}

// ---------- 用户 ----------

func Users() ([]map[string]any, error) {
	return database.Rows("SELECT " + userFields + " FROM users ORDER BY id")
}

func UserByID(id int64) (map[string]any, error) {
	return database.One("SELECT "+userFields+" FROM users WHERE id = ?", id)
}

// UserByName 返回含 password_hash 的完整行，仅用于登录校验。
func UserByName(username string) (map[string]any, error) {
	return database.One("SELECT * FROM users WHERE username = ?", username)
}

func CountUsers() (int64, error) {
	row, err := database.One("SELECT COUNT(*) AS n FROM users")
	if err != nil || row == nil {
		return 0, err
	}
	return ToInt64(row["n"]), nil
}

func InsertUser(username, passwordHash, displayName string, staff bool) (int64, error) {
	flag := 0
	if staff {
		flag = 1
	}
	return database.InsertID(
		"INSERT INTO users (username, password_hash, display_name, is_staff) VALUES (?, ?, ?, ?)",
		username, passwordHash, displayName, flag)
}

// UserExists 判断给定 id 集合是否全部存在。
func UserExists(ids []int64) (bool, error) {
	if len(ids) == 0 {
		return true, nil
	}
	args := make([]any, len(ids))
	for i, id := range ids {
		args[i] = id
	}
	rows, err := database.Rows(
		"SELECT id FROM users WHERE id IN ("+placeholders(len(ids))+")", args...)
	if err != nil {
		return false, err
	}
	return len(rows) == len(ids), nil
}

// ValidUserIDs 去重并校验用户存在，返回规范化的 id 列表。
func ValidUserIDs(ids []int64) ([]int64, error) {
	seen := make(map[int64]bool, len(ids))
	unique := make([]int64, 0, len(ids))
	for _, id := range ids {
		if id > 0 && !seen[id] {
			seen[id] = true
			unique = append(unique, id)
		}
	}
	if len(unique) == 0 {
		return unique, nil
	}
	ok, err := UserExists(unique)
	if err != nil {
		return nil, err
	}
	if !ok {
		return nil, fmt.Errorf("包含不存在的用户")
	}
	return unique, nil
}

// ---------- 工作台 ----------

func DashboardCounts() (map[string]any, error) {
	return database.One(`SELECT
        (SELECT COUNT(*) FROM projects) AS project_count,
        (SELECT COUNT(*) FROM tasks) AS task_count,
        (SELECT COUNT(*) FROM tasks WHERE status = 'todo') AS todo_count,
        (SELECT COUNT(*) FROM tasks WHERE status = 'doing') AS doing_count,
        (SELECT COUNT(*) FROM tasks WHERE status = 'done') AS done_count`)
}

func MyProjects(uid int64) ([]map[string]any, error) {
	return database.Rows(
		projectSelect+" WHERE p.owner_id = ? ORDER BY p.created_at DESC, p.id DESC LIMIT 5", uid)
}

func MyTasks(uid int64) ([]map[string]any, error) {
	return database.Rows(
		taskSelect+" WHERE t.assignee_id = ? AND t.status <> 'done'"+taskOrder+" LIMIT 10", uid)
}
