package server

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/database"
	"pms-gin/internal/models"
)

// ---------- 通用 helper ----------

func vstr(v any) string {
	if v == nil {
		return ""
	}
	return fmt.Sprint(v)
}

func parseID(c *gin.Context) (int64, bool) {
	n, err := strconv.ParseInt(c.Param("id"), 10, 64)
	return n, err == nil && n > 0
}

func parseMemberIDs(c *gin.Context) []int64 {
	ids := make([]int64, 0)
	for _, raw := range c.PostFormArray("member_ids") {
		if n, err := strconv.ParseInt(strings.TrimSpace(raw), 10, 64); err == nil && n > 0 {
			ids = append(ids, n)
		}
	}
	return ids
}

func userViews() []map[string]any {
	rows, err := models.Users()
	if err != nil {
		return []map[string]any{}
	}
	out := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		out = append(out, models.UserView(r))
	}
	return out
}

// ---------- 表单校验（错误回填 + 重新渲染） ----------

type projectInput struct {
	Name, Description, Status string
	StartDate, EndDate        any
	MemberIDs                 []int64
}

func validateProject(c *gin.Context) (projectInput, gin.H, gin.H) {
	name := strings.TrimSpace(c.PostForm("name"))
	description := c.PostForm("description")
	status := c.PostForm("status")
	if status == "" {
		status = "active"
	}
	startRaw, endRaw := c.PostForm("start_date"), c.PostForm("end_date")
	ids := parseMemberIDs(c)
	errs := gin.H{}

	if name == "" {
		errs["name"] = "项目名称不能为空"
	} else if len([]rune(name)) > 100 {
		errs["name"] = "项目名称不能超过 100 字"
	}
	if !models.ValidProjectStatus(status) {
		errs["status"] = "状态值非法"
	}
	startDate, e := models.ParseDate(startRaw)
	if e != nil {
		errs["start_date"] = e.Error()
	}
	endDate, e := models.ParseDate(endRaw)
	if e != nil {
		errs["end_date"] = e.Error()
	}
	validIDs, e := models.ValidUserIDs(ids)
	if e != nil {
		errs["member_ids"] = e.Error()
	}
	form := gin.H{
		"Name": name, "Description": description, "Status": status,
		"StartDate": startRaw, "EndDate": endRaw, "MemberIDs": ids,
	}
	return projectInput{name, description, status, startDate, endDate, validIDs}, form, errs
}

type taskInput struct {
	Title, Description, Status, Priority string
	AssigneeID, DueDate                  any
}

func validateTask(c *gin.Context) (taskInput, gin.H, gin.H) {
	title := strings.TrimSpace(c.PostForm("title"))
	description := c.PostForm("description")
	status := c.PostForm("status")
	if status == "" {
		status = "todo"
	}
	priority := c.PostForm("priority")
	if priority == "" {
		priority = "medium"
	}
	dueRaw := c.PostForm("due_date")
	assigneeRaw := strings.TrimSpace(c.PostForm("assignee_id"))

	var assignee any
	var assigneeID int64
	if assigneeRaw != "" {
		if n, err := strconv.ParseInt(assigneeRaw, 10, 64); err == nil && n > 0 {
			assigneeID, assignee = n, n
		}
	}
	errs := gin.H{}
	if title == "" {
		errs["title"] = "任务标题不能为空"
	} else if len([]rune(title)) > 200 {
		errs["title"] = "任务标题不能超过 200 字"
	}
	if !models.ValidTaskStatus(status) {
		errs["status"] = "状态值非法"
	}
	if !models.ValidPriority(priority) {
		errs["priority"] = "优先级值非法"
	}
	dueDate, e := models.ParseDate(dueRaw)
	if e != nil {
		errs["due_date"] = e.Error()
	}
	if assignee != nil {
		ok, err := models.UserExists([]int64{assigneeID})
		if err != nil || !ok {
			errs["assignee_id"] = "执行人不存在"
		}
	}
	form := gin.H{
		"Title": title, "Description": description, "Status": status,
		"Priority": priority, "DueDate": dueRaw, "AssigneeID": assignee,
	}
	return taskInput{title, description, status, priority, assignee, dueDate}, form, errs
}

type userInput struct {
	Username, Password, DisplayName string
	IsStaff                         bool
}

func validateUser(c *gin.Context) (userInput, gin.H, gin.H) {
	username := strings.TrimSpace(c.PostForm("username"))
	password := c.PostForm("password")
	display := strings.TrimSpace(c.PostForm("display_name"))
	staff := c.PostForm("is_staff") != ""
	errs := gin.H{}
	if username == "" {
		errs["username"] = "用户名不能为空"
	} else if len([]rune(username)) > 150 {
		errs["username"] = "用户名不能超过 150 字"
	}
	if len(password) < 8 {
		errs["password"] = "密码至少 8 位"
	} else if len(password) > 72 {
		errs["password"] = "密码不能超过 72 位"
	}
	if username != "" {
		if row, _ := models.UserByName(username); row != nil {
			errs["username"] = "用户名已存在"
		}
	}
	if display == "" {
		display = username
	}
	form := gin.H{"Username": username, "DisplayName": display, "IsStaff": staff, "Password": ""}
	return userInput{username, password, display, staff}, form, errs
}

func (s *Server) renderProjectForm(c *gin.Context, title, action, cancelURL string, form, errs gin.H) {
	s.render(c, "project_form.html", title, gin.H{
		"Action": action, "CancelURL": cancelURL, "Form": form,
		"Users": userViews(), "Errors": errs,
	})
}

func (s *Server) renderTaskForm(c *gin.Context, title, action, cancelURL, projectName string, form, errs gin.H) {
	s.render(c, "task_form.html", title, gin.H{
		"Action": action, "CancelURL": cancelURL, "ProjectName": projectName,
		"Form": form, "Users": userViews(), "Errors": errs,
	})
}

func (s *Server) renderAdminUsers(c *gin.Context, form, errs gin.H, msg bool) {
	s.render(c, "admin_users.html", "用户管理", gin.H{
		"Users": userViews(), "Form": form, "Errors": errs, "Msg": msg,
	})
}

// ---------- 登录 / 退出 ----------

func (s *Server) loginGET(c *gin.Context) {
	if s.currentSession(c) != nil {
		c.Redirect(http.StatusFound, "/")
		return
	}
	s.render(c, "login.html", "登录", gin.H{"LoginError": false, "UsernameValue": ""})
}

func (s *Server) loginPOST(c *gin.Context) {
	if s.currentSession(c) != nil {
		c.Redirect(http.StatusFound, "/")
		return
	}
	username := strings.TrimSpace(c.PostForm("username"))
	password := c.PostForm("password")
	row, err := models.UserByName(username)
	if err != nil || row == nil || !database.VerifyPassword(password, vstr(row["password_hash"])) {
		s.render(c, "login.html", "登录", gin.H{"LoginError": true, "UsernameValue": username})
		return
	}
	s.setSession(c, Session{
		UID:      models.ToInt64(row["id"]),
		Username: vstr(row["username"]),
		Staff:    models.ToInt64(row["is_staff"]) != 0,
	})
	c.Redirect(http.StatusFound, "/")
}

func (s *Server) logoutPOST(c *gin.Context) {
	s.clearSession(c)
	c.Redirect(http.StatusFound, "/login")
}

// ---------- 工作台 ----------

func (s *Server) dashboardGET(c *gin.Context) {
	uid := s.currentSession(c).UID
	counts, _ := models.DashboardCounts()
	if counts == nil {
		counts = gin.H{}
	}
	myP, _ := models.MyProjects(uid)
	myT, _ := models.MyTasks(uid)
	projects := make([]map[string]any, 0, len(myP))
	for _, r := range myP {
		projects = append(projects, models.ProjectView(r, uid))
	}
	tasks := make([]map[string]any, 0, len(myT))
	for _, r := range myT {
		tasks = append(tasks, models.TaskView(r))
	}
	s.render(c, "dashboard.html", "工作台", gin.H{
		"ProjectCount": models.ToInt64(counts["project_count"]),
		"TaskCount":    models.ToInt64(counts["task_count"]),
		"TodoCount":    models.ToInt64(counts["todo_count"]),
		"DoingCount":   models.ToInt64(counts["doing_count"]),
		"DoneCount":    models.ToInt64(counts["done_count"]),
		"MyProjects":   projects,
		"MyTasks":      tasks,
	})
}

// ---------- 项目列表 / 详情 ----------

func (s *Server) projectListGET(c *gin.Context) {
	uid := s.currentSession(c).UID
	status, q := c.Query("status"), c.Query("q")
	rows, _ := models.Projects(status, q)
	projects := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		projects = append(projects, models.ProjectView(r, uid))
	}
	s.render(c, "project_list.html", "项目列表", gin.H{
		"Projects": projects, "Status": status, "Keyword": q, "CurrentUserID": uid,
	})
}

// projectEntryGET/POST 合并 /projects/new/ 与 /projects/:id/，规避 gin 路由树冲突。
func (s *Server) projectEntryGET(c *gin.Context) {
	if c.Param("id") == "new" {
		s.projectNewGET(c)
		return
	}
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	uid := s.currentSession(c).UID
	row, err := models.Project(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	project := models.ProjectView(row, uid)
	names, _ := models.ProjectMemberNames(id)
	if len(names) == 0 {
		project["MemberNames"] = "无"
	} else {
		project["MemberNames"] = strings.Join(names, "、")
	}
	tasks, _ := models.TasksByProject(id)
	todo := make([]map[string]any, 0)
	doing := make([]map[string]any, 0)
	done := make([]map[string]any, 0)
	for _, t := range tasks {
		view := models.TaskView(t)
		switch vstr(t["status"]) {
		case "todo":
			todo = append(todo, view)
		case "doing":
			doing = append(doing, view)
		default:
			done = append(done, view)
		}
	}
	s.render(c, "project_detail.html", vstr(project["Name"]), gin.H{
		"Project": project, "TodoTasks": todo, "DoingTasks": doing,
		"DoneTasks": done, "IsOwner": project["IsOwner"],
	})
}

func (s *Server) projectEntryPOST(c *gin.Context) {
	if c.Param("id") == "new" {
		s.projectNewPOST(c)
		return
	}
	c.Redirect(http.StatusFound, "/projects/"+c.Param("id")+"/")
}

func (s *Server) projectNewGET(c *gin.Context) {
	s.renderProjectForm(c, "新建项目", "/projects/new/", "/projects/", gin.H{
		"Name": "", "Description": "", "Status": "active",
		"StartDate": "", "EndDate": "", "MemberIDs": []int64{},
	}, gin.H{})
}

func (s *Server) projectNewPOST(c *gin.Context) {
	input, form, errs := validateProject(c)
	if len(errs) > 0 {
		s.renderProjectForm(c, "新建项目", "/projects/new/", "/projects/", form, errs)
		return
	}
	pid, err := models.InsertProject(input.Name, input.Description, input.Status,
		input.StartDate, input.EndDate, s.currentSession(c).UID)
	if err == nil {
		err = models.SetMembers(pid, input.MemberIDs)
	}
	if err != nil {
		errs["name"] = "保存失败：" + err.Error()
		s.renderProjectForm(c, "新建项目", "/projects/new/", "/projects/", form, errs)
		return
	}
	c.Redirect(http.StatusFound, "/projects/"+strconv.FormatInt(pid, 10)+"/")
}

func (s *Server) projectEditGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	uid := s.currentSession(c).UID
	row, err := models.Project(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	if models.ToInt64(row["owner_id"]) != uid {
		c.Redirect(http.StatusFound, "/projects/"+strconv.FormatInt(id, 10)+"/")
		return
	}
	ids, _ := models.ProjectMemberIDs(id)
	form := gin.H{
		"Name": vstr(row["name"]), "Description": vstr(row["description"]),
		"Status": vstr(row["status"]), "StartDate": models.DateOnly(row["start_date"]),
		"EndDate": models.DateOnly(row["end_date"]), "MemberIDs": ids,
	}
	s.renderProjectForm(c, "编辑项目", "/projects/"+c.Param("id")+"/edit/",
		"/projects/"+c.Param("id")+"/", form, gin.H{})
}

func (s *Server) projectEditPOST(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	uid := s.currentSession(c).UID
	row, err := models.Project(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	detail := "/projects/" + strconv.FormatInt(id, 10) + "/"
	if models.ToInt64(row["owner_id"]) != uid {
		c.Redirect(http.StatusFound, detail)
		return
	}
	input, form, errs := validateProject(c)
	if len(errs) > 0 {
		s.renderProjectForm(c, "编辑项目", "/projects/"+c.Param("id")+"/edit/", detail, form, errs)
		return
	}
	if err = models.UpdateProject(id, input.Name, input.Description, input.Status,
		input.StartDate, input.EndDate); err == nil {
		err = models.SetMembers(id, input.MemberIDs)
	}
	if err != nil {
		errs["name"] = "保存失败：" + err.Error()
		s.renderProjectForm(c, "编辑项目", "/projects/"+c.Param("id")+"/edit/", detail, form, errs)
		return
	}
	c.Redirect(http.StatusFound, detail)
}

func (s *Server) projectDeleteGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	uid := s.currentSession(c).UID
	row, err := models.Project(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	if models.ToInt64(row["owner_id"]) != uid {
		c.Redirect(http.StatusFound, "/projects/"+strconv.FormatInt(id, 10)+"/")
		return
	}
	_ = models.DeleteProject(id)
	c.Redirect(http.StatusFound, "/projects/")
}

// ---------- 任务 ----------

func (s *Server) taskNewGET(c *gin.Context) {
	pid, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	row, err := models.Project(pid)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	form := gin.H{
		"Title": "", "Description": "", "AssigneeID": nil,
		"Status": "todo", "Priority": "medium", "DueDate": "",
	}
	s.renderTaskForm(c, "新建任务",
		"/projects/"+c.Param("id")+"/tasks/new/", "/projects/"+c.Param("id")+"/",
		vstr(row["name"]), form, gin.H{})
}

func (s *Server) taskNewPOST(c *gin.Context) {
	pid, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	row, err := models.Project(pid)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	detail := "/projects/" + strconv.FormatInt(pid, 10) + "/"
	input, form, errs := validateTask(c)
	if len(errs) > 0 {
		s.renderTaskForm(c, "新建任务", "/projects/"+c.Param("id")+"/tasks/new/",
			detail, vstr(row["name"]), form, errs)
		return
	}
	if _, err = models.InsertTask(pid, input.Title, input.Description, input.AssigneeID,
		input.Status, input.Priority, input.DueDate); err != nil {
		errs["title"] = "保存失败：" + err.Error()
		s.renderTaskForm(c, "新建任务", "/projects/"+c.Param("id")+"/tasks/new/",
			detail, vstr(row["name"]), form, errs)
		return
	}
	c.Redirect(http.StatusFound, detail)
}

func (s *Server) taskEditGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	row, err := models.Task(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	pid := models.ToInt64(row["project_id"])
	project, _ := models.Project(pid)
	form := gin.H{
		"Title": vstr(row["title"]), "Description": vstr(row["description"]),
		"AssigneeID": row["assignee_id"], "Status": vstr(row["status"]),
		"Priority": vstr(row["priority"]), "DueDate": models.DateOnly(row["due_date"]),
	}
	s.renderTaskForm(c, "编辑任务", "/tasks/"+c.Param("id")+"/edit/",
		"/projects/"+strconv.FormatInt(pid, 10)+"/", vstr(project["name"]), form, gin.H{})
}

func (s *Server) taskEditPOST(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	row, err := models.Task(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	pid := models.ToInt64(row["project_id"])
	project, _ := models.Project(pid)
	detail := "/projects/" + strconv.FormatInt(pid, 10) + "/"
	input, form, errs := validateTask(c)
	if len(errs) > 0 {
		s.renderTaskForm(c, "编辑任务", "/tasks/"+c.Param("id")+"/edit/",
			detail, vstr(project["name"]), form, errs)
		return
	}
	if err = models.UpdateTask(id, input.Title, input.Description, input.AssigneeID,
		input.Status, input.Priority, input.DueDate); err != nil {
		errs["title"] = "保存失败：" + err.Error()
		s.renderTaskForm(c, "编辑任务", "/tasks/"+c.Param("id")+"/edit/",
			detail, vstr(project["name"]), form, errs)
		return
	}
	c.Redirect(http.StatusFound, detail)
}

func (s *Server) taskDeleteGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	row, err := models.Task(id)
	if err != nil || row == nil {
		c.Redirect(http.StatusFound, "/projects/")
		return
	}
	pid := models.ToInt64(row["project_id"])
	_ = models.DeleteTask(id)
	c.Redirect(http.StatusFound, "/projects/"+strconv.FormatInt(pid, 10)+"/")
}

// ---------- 用户管理（仅管理员） ----------

func (s *Server) adminUsersGET(c *gin.Context) {
	if !s.currentSession(c).Staff {
		c.Redirect(http.StatusFound, "/")
		return
	}
	s.renderAdminUsers(c,
		gin.H{"Username": "", "DisplayName": "", "IsStaff": false, "Password": ""},
		gin.H{}, c.Query("created") == "1")
}

func (s *Server) adminUsersPOST(c *gin.Context) {
	if !s.currentSession(c).Staff {
		c.Redirect(http.StatusFound, "/")
		return
	}
	input, form, errs := validateUser(c)
	if len(errs) > 0 {
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	hash, err := database.HashPassword(input.Password)
	if err != nil {
		errs["password"] = "保存失败：" + err.Error()
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	if _, err = models.InsertUser(input.Username, hash, input.DisplayName, input.IsStaff); err != nil {
		errs["username"] = "用户名已存在"
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	c.Redirect(http.StatusFound, "/admin/users/?created=1")
}
