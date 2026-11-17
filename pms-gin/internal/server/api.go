package server

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/database"
	"pms-gin/internal/models"
)

// ---------- API Token：base64url(json claims) + "." + hex(HMAC(secret, payload)) ----------

type apiClaims struct {
	UID      int64  `json:"uid"`
	Username string `json:"username"`
	Staff    bool   `json:"staff"`
	Exp      int64  `json:"exp"`
}

func (s *Server) issueToken(uid int64, username string, staff bool) string {
	claims := apiClaims{
		UID: uid, Username: username, Staff: staff,
		Exp: time.Now().Add(24 * time.Hour).Unix(),
	}
	raw, _ := json.Marshal(claims) // 紧凑序列化：分隔符为 , 与 :
	payload := b64encode(raw)
	return payload + "." + hmacHex(s.apiSecret, payload)
}

func (s *Server) verifyToken(token string) (*apiClaims, bool) {
	if token == "" {
		return nil, false
	}
	payload, sig, ok := strings.Cut(token, ".")
	if !ok || payload == "" || sig == "" {
		return nil, false
	}
	// 验签只覆盖收到的 payload 串，不重新序列化 claims，故字段顺序无关紧要。
	if !ctEq(hmacHex(s.apiSecret, payload), sig) {
		return nil, false
	}
	raw, err := b64decode(payload)
	if err != nil {
		return nil, false
	}
	var cl apiClaims
	if err = json.Unmarshal(raw, &cl); err != nil {
		return nil, false
	}
	if cl.Exp <= time.Now().Unix() || cl.UID <= 0 {
		return nil, false
	}
	return &cl, true
}

func apiFail(c *gin.Context, code int, detail string) {
	c.JSON(code, gin.H{"detail": detail})
}

func (s *Server) apiAuth(c *gin.Context) {
	auth := c.GetHeader("Authorization")
	if !strings.HasPrefix(auth, "Bearer ") {
		apiFail(c, http.StatusUnauthorized, "未认证或 Token 已过期")
		c.Abort()
		return
	}
	cl, ok := s.verifyToken(strings.TrimPrefix(auth, "Bearer "))
	if !ok {
		apiFail(c, http.StatusUnauthorized, "未认证或 Token 已过期")
		c.Abort()
		return
	}
	row, err := models.UserByID(cl.UID)
	if err != nil || row == nil {
		apiFail(c, http.StatusUnauthorized, "用户不存在")
		c.Abort()
		return
	}
	c.Set("apiUser", row)
	c.Next()
}

func apiUser(c *gin.Context) map[string]any {
	v, _ := c.Get("apiUser")
	row, _ := v.(map[string]any)
	return row
}

func isStaff(row map[string]any) bool {
	return models.ToInt64(row["is_staff"]) != 0
}

// ---------- 请求体与校验 ----------

type loginIn struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type projectIn struct {
	Name        string  `json:"name"`
	Description string  `json:"description"`
	Status      string  `json:"status"`
	StartDate   *string `json:"start_date"`
	EndDate     *string `json:"end_date"`
	MemberIDs   []int64 `json:"member_ids"`
}

type taskIn struct {
	Title       string  `json:"title"`
	Description string  `json:"description"`
	AssigneeID  *int64  `json:"assignee_id"`
	Status      string  `json:"status"`
	Priority    string  `json:"priority"`
	DueDate     *string `json:"due_date"`
}

type userIn struct {
	Username    string `json:"username"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
	IsStaff     bool   `json:"is_staff"`
}

func decodeJSON(c *gin.Context, dst any) bool {
	if err := json.NewDecoder(c.Request.Body).Decode(dst); err != nil {
		apiFail(c, http.StatusBadRequest, "请求数据格式错误")
		return false
	}
	return true
}

func dateFromPtr(p *string) (any, error) {
	if p == nil {
		return nil, nil
	}
	return models.ParseDate(*p)
}

func normalizeProjectIn(b *projectIn) string {
	b.Name = strings.TrimSpace(b.Name)
	if b.Name == "" {
		return "项目名称不能为空"
	}
	if len([]rune(b.Name)) > 100 {
		return "项目名称不能超过 100 字"
	}
	if b.Status == "" {
		b.Status = "active"
	}
	if !models.ValidProjectStatus(b.Status) {
		return "状态值非法"
	}
	if _, err := dateFromPtr(b.StartDate); err != nil {
		return err.Error()
	}
	if _, err := dateFromPtr(b.EndDate); err != nil {
		return err.Error()
	}
	if _, err := models.ValidUserIDs(b.MemberIDs); err != nil {
		return err.Error()
	}
	return ""
}

func normalizeTaskIn(b *taskIn) string {
	b.Title = strings.TrimSpace(b.Title)
	if b.Title == "" {
		return "任务标题不能为空"
	}
	if len([]rune(b.Title)) > 200 {
		return "任务标题不能超过 200 字"
	}
	if b.Status == "" {
		b.Status = "todo"
	}
	if b.Priority == "" {
		b.Priority = "medium"
	}
	if !models.ValidTaskStatus(b.Status) {
		return "状态值非法"
	}
	if !models.ValidPriority(b.Priority) {
		return "优先级值非法"
	}
	if _, err := dateFromPtr(b.DueDate); err != nil {
		return err.Error()
	}
	if b.AssigneeID != nil {
		ok, err := models.UserExists([]int64{*b.AssigneeID})
		if err != nil {
			return "执行人校验失败"
		}
		if !ok {
			return "执行人不存在"
		}
	}
	return ""
}

func normalizeUserIn(b *userIn) string {
	b.Username = strings.TrimSpace(b.Username)
	if b.Username == "" {
		return "用户名不能为空"
	}
	if len([]rune(b.Username)) > 150 {
		return "用户名不能超过 150 字"
	}
	if len(b.Password) < 8 {
		return "密码至少 8 位"
	}
	if len(b.Password) > 72 {
		return "密码不能超过 72 位"
	}
	if b.DisplayName == "" {
		b.DisplayName = b.Username
	}
	return ""
}

func projectOutWithMembers(row map[string]any) (map[string]any, error) {
	if row == nil {
		return nil, nil
	}
	ids, err := models.ProjectMemberIDs(models.ToInt64(row["id"]))
	if err != nil {
		return nil, err
	}
	return models.ProjectOut(row, ids), nil
}

func apiID(c *gin.Context) (int64, bool) {
	n, err := strconv.ParseInt(c.Param("id"), 10, 64)
	return n, err == nil && n > 0
}

// ---------- 端点 ----------

func (s *Server) apiLogin(c *gin.Context) {
	var body loginIn
	if !decodeJSON(c, &body) {
		return
	}
	row, err := models.UserByName(body.Username)
	if err != nil || row == nil || !database.VerifyPassword(body.Password, vstr(row["password_hash"])) {
		apiFail(c, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"token": s.issueToken(models.ToInt64(row["id"]), vstr(row["username"]),
			models.ToInt64(row["is_staff"]) != 0),
		"user": models.UserOut(row),
	})
}

func (s *Server) apiMe(c *gin.Context) {
	c.JSON(http.StatusOK, models.UserOut(apiUser(c)))
}

func (s *Server) apiDashboard(c *gin.Context) {
	uid := models.ToInt64(apiUser(c)["id"])
	counts, err := models.DashboardCounts()
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out := gin.H{}
	for k, v := range counts {
		out[k] = v
	}
	myP, _ := models.MyProjects(uid)
	projects := make([]map[string]any, 0, len(myP))
	for _, r := range myP {
		o, err := projectOutWithMembers(r)
		if err != nil {
			apiFail(c, http.StatusBadRequest, err.Error())
			return
		}
		projects = append(projects, o)
	}
	myT, _ := models.MyTasks(uid)
	tasks := make([]map[string]any, 0, len(myT))
	for _, r := range myT {
		tasks = append(tasks, models.TaskOut(r))
	}
	out["my_projects"] = projects
	out["my_tasks"] = tasks
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiProjectsList(c *gin.Context) {
	rows, err := models.Projects(c.Query("status"), c.Query("q"))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		o, err := projectOutWithMembers(r)
		if err != nil {
			apiFail(c, http.StatusBadRequest, err.Error())
			return
		}
		out = append(out, o)
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiProjectsCreate(c *gin.Context) {
	var body projectIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeProjectIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	startDate, _ := dateFromPtr(body.StartDate)
	endDate, _ := dateFromPtr(body.EndDate)
	ids, _ := models.ValidUserIDs(body.MemberIDs)
	pid, err := models.InsertProject(body.Name, body.Description, body.Status,
		startDate, endDate, models.ToInt64(apiUser(c)["id"]))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if err = models.SetMembers(pid, ids); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	row, _ := models.Project(pid)
	out, _ := projectOutWithMembers(row)
	c.JSON(http.StatusCreated, out)
}

func (s *Server) apiProjectGet(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	row, err := models.Project(id)
	if err != nil || row == nil {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	out, err := projectOutWithMembers(row)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	tasks, _ := models.TasksByProject(id)
	ts := make([]map[string]any, 0, len(tasks))
	for _, t := range tasks {
		ts = append(ts, models.TaskOut(t))
	}
	out["tasks"] = ts
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiProjectUpdate(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	row, err := models.Project(id)
	if err != nil || row == nil {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	if models.ToInt64(row["owner_id"]) != models.ToInt64(apiUser(c)["id"]) {
		apiFail(c, http.StatusForbidden, "仅负责人可操作")
		return
	}
	var body projectIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeProjectIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	startDate, _ := dateFromPtr(body.StartDate)
	endDate, _ := dateFromPtr(body.EndDate)
	ids, _ := models.ValidUserIDs(body.MemberIDs)
	if err = models.UpdateProject(id, body.Name, body.Description, body.Status,
		startDate, endDate); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if err = models.SetMembers(id, ids); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	updated, _ := models.Project(id)
	out, _ := projectOutWithMembers(updated)
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiProjectDelete(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	row, err := models.Project(id)
	if err != nil || row == nil {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	if models.ToInt64(row["owner_id"]) != models.ToInt64(apiUser(c)["id"]) {
		apiFail(c, http.StatusForbidden, "仅负责人可操作")
		return
	}
	if err = models.DeleteProject(id); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.Status(http.StatusNoContent)
}

func (s *Server) apiTaskCreate(c *gin.Context) {
	pid, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	exists, err := models.ProjectExists(pid)
	if err != nil || !exists {
		apiFail(c, http.StatusNotFound, "项目不存在")
		return
	}
	var body taskIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeTaskIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	dueDate, _ := dateFromPtr(body.DueDate)
	var assignee any
	if body.AssigneeID != nil {
		assignee = *body.AssigneeID
	}
	tid, err := models.InsertTask(pid, body.Title, body.Description, assignee,
		body.Status, body.Priority, dueDate)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	row, _ := models.Task(tid)
	c.JSON(http.StatusCreated, models.TaskOut(row))
}

func (s *Server) apiTaskUpdate(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "任务不存在")
		return
	}
	row, err := models.Task(id)
	if err != nil || row == nil {
		apiFail(c, http.StatusNotFound, "任务不存在")
		return
	}
	var body taskIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeTaskIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	dueDate, _ := dateFromPtr(body.DueDate)
	var assignee any
	if body.AssigneeID != nil {
		assignee = *body.AssigneeID
	}
	if err = models.UpdateTask(id, body.Title, body.Description, assignee,
		body.Status, body.Priority, dueDate); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	updated, _ := models.Task(id)
	c.JSON(http.StatusOK, models.TaskOut(updated))
}

func (s *Server) apiTaskDelete(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiFail(c, http.StatusNotFound, "任务不存在")
		return
	}
	exists, err := models.TaskExists(id)
	if err != nil || !exists {
		apiFail(c, http.StatusNotFound, "任务不存在")
		return
	}
	if err = models.DeleteTask(id); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.Status(http.StatusNoContent)
}

func (s *Server) apiUsersList(c *gin.Context) {
	if !isStaff(apiUser(c)) {
		apiFail(c, http.StatusForbidden, "仅管理员可操作")
		return
	}
	rows, err := models.Users()
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		out = append(out, models.UserOut(r))
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiUsersCreate(c *gin.Context) {
	if !isStaff(apiUser(c)) {
		apiFail(c, http.StatusForbidden, "仅管理员可操作")
		return
	}
	var body userIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeUserIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	if row, _ := models.UserByName(body.Username); row != nil {
		apiFail(c, http.StatusBadRequest, "用户名已存在")
		return
	}
	hash, err := database.HashPassword(body.Password)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	uid, err := models.InsertUser(body.Username, hash, body.DisplayName, body.IsStaff)
	if err != nil {
		apiFail(c, http.StatusBadRequest, "用户名已存在")
		return
	}
	row, _ := models.UserByID(uid)
	c.JSON(http.StatusCreated, models.UserOut(row))
}
