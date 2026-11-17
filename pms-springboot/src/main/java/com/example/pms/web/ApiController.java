package com.example.pms.web;

import com.example.pms.domain.User;
import com.example.pms.repo.ProjectRepository;
import com.example.pms.repo.TaskRepository;
import com.example.pms.repo.UserRepository;
import com.example.pms.support.ApiToken;
import com.example.pms.support.Views;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 与 Gin 版、FastAPI 版共用同一份 db/API.md 契约的 REST API。
 * 页面仍走 Session + CSRF；/api/** 走 HMAC Token 并豁免 CSRF。
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserRepository users;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final ApiToken tokens;
    private final PasswordEncoder encoder;

    public ApiController(UserRepository users, ProjectRepository projects, TaskRepository tasks,
                         ApiToken tokens, PasswordEncoder encoder) {
        this.users = users;
        this.projects = projects;
        this.tasks = tasks;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    // ------------------------------------------------------------------ 认证

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@RequestBody(required = false) Map<String, Object> body) {
        String username = str(field(body, "username")).trim();
        String password = str(field(body, "password"));
        if (username.isEmpty() || password.isEmpty()) {
            return err(400, "用户名和密码不能为空");
        }
        Optional<User> found = users.findByUsername(username);
        if (found.isEmpty() || !encoder.matches(password, found.get().getPasswordHash())) {
            return err(401, "用户名或密码错误");
        }
        User u = found.get();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("token", tokens.issue(u.getId(), u.getUsername(), u.isStaff()));
        out.put("user", userMap(users.findRowById(u.getId()).orElse(Map.of())));
        return ResponseEntity.ok(out);
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(HttpServletRequest request) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(userMap(users.findRowById(u.getId()).orElse(Map.of())));
    }

    // ------------------------------------------------------------------ 工作台

    @GetMapping("/dashboard")
    public ResponseEntity<Object> dashboard(HttpServletRequest request) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        Map<String, Long> byStatus = tasks.countByStatus();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("project_count", projects.count());
        out.put("task_count", tasks.count());
        out.put("todo_count", byStatus.getOrDefault("todo", 0L));
        out.put("doing_count", byStatus.getOrDefault("doing", 0L));
        out.put("done_count", byStatus.getOrDefault("done", 0L));
        out.put("my_projects", projectMaps(projects.findByOwner(u.getId(), 5)));
        out.put("my_tasks", taskMaps(tasks.findByAssignee(u.getId(), 10)));
        return ResponseEntity.ok(out);
    }

    // ------------------------------------------------------------------ 项目

    @GetMapping("/projects")
    public ResponseEntity<Object> listProjects(HttpServletRequest request,
                                               @RequestParam(required = false, defaultValue = "") String status,
                                               @RequestParam(required = false, defaultValue = "") String q) {
        if (require(request) == null) {
            return unauthorized();
        }
        if (!status.isEmpty() && !Views.in(status, "active", "paused", "done")) {
            return err(400, "状态值非法");
        }
        return ResponseEntity.ok(projectMaps(projects.findAll(status, q)));
    }

    @PostMapping("/projects")
    public ResponseEntity<Object> createProject(HttpServletRequest request,
                                                @RequestBody(required = false) Map<String, Object> body) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        String name = str(field(body, "name")).trim();
        if (name.isEmpty()) {
            return err(400, "项目名称不能为空");
        }
        if (name.length() > 100) {
            return err(400, "项目名称不能超过 100 字");
        }
        String status = str(field(body, "status"));
        if (status.isEmpty()) {
            status = "active";
        }
        if (!Views.in(status, "active", "paused", "done")) {
            return err(400, "状态值非法");
        }
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = date(field(body, "start_date"));
            endDate = date(field(body, "end_date"));
        } catch (IllegalArgumentException e) {
            return err(400, "日期格式应为 YYYY-MM-DD");
        }
        ResponseEntity<Object> memberError = validateMembers(field(body, "member_ids"));
        if (memberError != null) {
            return memberError;
        }
        long id = projects.create(name, str(field(body, "description")), status, startDate, endDate,
                u.getId(), ids(field(body, "member_ids")));
        return ResponseEntity.status(201).body(projectDetail(id));
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<Object> getProject(HttpServletRequest request, @PathVariable long id) {
        if (require(request) == null) {
            return unauthorized();
        }
        return projects.findById(id)
                .<ResponseEntity<Object>>map(row -> ResponseEntity.ok(projectDetail(id)))
                .orElseGet(() -> err(404, "项目不存在"));
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<Object> updateProject(HttpServletRequest request, @PathVariable long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        Optional<Map<String, Object>> found = projects.findById(id);
        if (found.isEmpty()) {
            return err(404, "项目不存在");
        }
        if (!isOwner(found.get(), u)) {
            return err(403, "仅负责人可操作");
        }
        Map<String, Object> row = found.get();
        String name = field(body, "name") == null ? Views.str(row.get("name")) : str(field(body, "name")).trim();
        if (name.isEmpty()) {
            return err(400, "项目名称不能为空");
        }
        if (name.length() > 100) {
            return err(400, "项目名称不能超过 100 字");
        }
        String status = field(body, "status") == null ? Views.str(row.get("status")) : str(field(body, "status"));
        if (!Views.in(status, "active", "paused", "done")) {
            return err(400, "状态值非法");
        }
        String description = field(body, "description") == null
                ? Views.str(row.get("description")) : str(field(body, "description"));
        LocalDate startDate;
        LocalDate endDate;
        try {
            startDate = field(body, "start_date") == null ? Views.parseDate(Views.date(row.get("start_date")))
                    : date(field(body, "start_date"));
            endDate = field(body, "end_date") == null ? Views.parseDate(Views.date(row.get("end_date")))
                    : date(field(body, "end_date"));
        } catch (IllegalArgumentException e) {
            return err(400, "日期格式应为 YYYY-MM-DD");
        }
        Object rawMembers = field(body, "member_ids");
        if (rawMembers == null) {
            rawMembers = projects.memberIds(id);
        }
        ResponseEntity<Object> memberError = validateMembers(rawMembers);
        if (memberError != null) {
            return memberError;
        }
        projects.update(id, name, description, status, startDate, endDate, ids(rawMembers));
        return ResponseEntity.ok(projectDetail(id));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Object> deleteProject(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        Optional<Map<String, Object>> found = projects.findById(id);
        if (found.isEmpty()) {
            return err(404, "项目不存在");
        }
        if (!isOwner(found.get(), u)) {
            return err(403, "仅负责人可操作");
        }
        projects.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 任务

    @PostMapping("/projects/{id}/tasks")
    public ResponseEntity<Object> createTask(HttpServletRequest request, @PathVariable long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        if (require(request) == null) {
            return unauthorized();
        }
        if (projects.findById(id).isEmpty()) {
            return err(404, "项目不存在");
        }
        String title = str(field(body, "title")).trim();
        if (title.isEmpty()) {
            return err(400, "任务标题不能为空");
        }
        if (title.length() > 200) {
            return err(400, "任务标题不能超过 200 字");
        }
        String status = str(field(body, "status"));
        if (status.isEmpty()) {
            status = "todo";
        }
        String priority = str(field(body, "priority"));
        if (priority.isEmpty()) {
            priority = "medium";
        }
        if (!Views.in(status, "todo", "doing", "done") || !Views.in(priority, "low", "medium", "high")) {
            return err(400, "状态或优先级非法");
        }
        Long assigneeId = longValue(field(body, "assignee_id"));
        if (assigneeId != null && users.findById(assigneeId).isEmpty()) {
            return err(400, "执行人不存在");
        }
        LocalDate dueDate;
        try {
            dueDate = date(field(body, "due_date"));
        } catch (IllegalArgumentException e) {
            return err(400, "日期格式应为 YYYY-MM-DD");
        }
        long taskId = tasks.create(id, title, str(field(body, "description")), assigneeId,
                status, priority, dueDate);
        return tasks.findById(taskId)
                .<ResponseEntity<Object>>map(t -> ResponseEntity.status(201).body(taskMap(t)))
                .orElseGet(() -> err(404, "任务不存在"));
    }

    @PutMapping("/tasks/{id}")
    public ResponseEntity<Object> updateTask(HttpServletRequest request, @PathVariable long id,
                                             @RequestBody(required = false) Map<String, Object> body) {
        if (require(request) == null) {
            return unauthorized();
        }
        Optional<Map<String, Object>> found = tasks.findById(id);
        if (found.isEmpty()) {
            return err(404, "任务不存在");
        }
        Map<String, Object> row = found.get();
        String title = field(body, "title") == null ? Views.str(row.get("title")) : str(field(body, "title")).trim();
        if (title.isEmpty()) {
            return err(400, "任务标题不能为空");
        }
        if (title.length() > 200) {
            return err(400, "任务标题不能超过 200 字");
        }
        String description = field(body, "description") == null
                ? Views.str(row.get("description")) : str(field(body, "description"));
        String status = field(body, "status") == null ? Views.str(row.get("status")) : str(field(body, "status"));
        String priority = field(body, "priority") == null
                ? Views.str(row.get("priority")) : str(field(body, "priority"));
        if (!Views.in(status, "todo", "doing", "done") || !Views.in(priority, "low", "medium", "high")) {
            return err(400, "状态或优先级非法");
        }
        Long assigneeId = field(body, "assignee_id") == null
                ? longValue(row.get("assignee_id")) : longValue(field(body, "assignee_id"));
        if (assigneeId != null && users.findById(assigneeId).isEmpty()) {
            return err(400, "执行人不存在");
        }
        LocalDate dueDate;
        try {
            dueDate = field(body, "due_date") == null ? Views.parseDate(Views.date(row.get("due_date")))
                    : date(field(body, "due_date"));
        } catch (IllegalArgumentException e) {
            return err(400, "日期格式应为 YYYY-MM-DD");
        }
        tasks.update(id, title, description, assigneeId, status, priority, dueDate);
        return ResponseEntity.ok(taskMap(tasks.findById(id).orElse(row)));
    }

    @DeleteMapping("/tasks/{id}")
    public ResponseEntity<Object> deleteTask(HttpServletRequest request, @PathVariable long id) {
        if (require(request) == null) {
            return unauthorized();
        }
        if (tasks.findById(id).isEmpty()) {
            return err(404, "任务不存在");
        }
        tasks.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 用户

    @GetMapping("/users")
    public ResponseEntity<Object> listUsers(HttpServletRequest request) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        if (!u.isStaff()) {
            return err(403, "仅管理员可操作用户");
        }
        return ResponseEntity.ok(users.findAllRows().stream().map(this::userMap).toList());
    }

    @PostMapping("/users")
    public ResponseEntity<Object> createUser(HttpServletRequest request,
                                             @RequestBody(required = false) Map<String, Object> body) {
        User u = require(request);
        if (u == null) {
            return unauthorized();
        }
        if (!u.isStaff()) {
            return err(403, "仅管理员可操作用户");
        }
        String username = str(field(body, "username")).trim();
        String password = str(field(body, "password"));
        if (username.isEmpty() || password.isEmpty()) {
            return err(400, "用户名和密码不能为空");
        }
        if (username.length() > 50) {
            return err(400, "用户名不能超过 50 字");
        }
        if (password.length() < 8) {
            return err(400, "密码至少 8 位");
        }
        if (users.existsByUsername(username)) {
            return err(400, "用户名已存在");
        }
        Object rawStaff = field(body, "is_staff");
        boolean staff = rawStaff instanceof Boolean b ? b
                : rawStaff instanceof Number n && n.intValue() != 0;
        users.create(username, encoder.encode(password), str(field(body, "display_name")), staff);
        return ResponseEntity.status(201)
                .body(userMap(users.findRowByUsername(username).orElse(Map.of())));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> badJson() {
        return err(400, "请求体不是合法的 JSON");
    }

    // ------------------------------------------------------------------ 辅助

    private User require(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        Map<String, Object> claims = tokens.verify(header.substring(7).trim());
        if (claims == null || !(claims.get("uid") instanceof Number uid)) {
            return null;
        }
        return users.findById(uid.longValue()).orElse(null);
    }

    private static ResponseEntity<Object> unauthorized() {
        return err(401, "未认证或 Token 已过期");
    }

    private static ResponseEntity<Object> err(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("detail", message));
    }

    private static Object field(Map<String, Object> body, String key) {
        return body == null ? null : body.get(key);
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Long longValue(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static LocalDate date(Object value) {
        if (value == null || str(value).isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(str(value).trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("bad date");
        }
    }

    private static List<Long> ids(Object value) {
        List<Long> result = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                Long id = longValue(item);
                if (id != null && !result.contains(id)) {
                    result.add(id);
                }
            }
        }
        return result;
    }

    private ResponseEntity<Object> validateMembers(Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof List<?>)) {
            return err(400, "member_ids 必须是整数数组");
        }
        for (Long id : ids(value)) {
            if (users.findById(id).isEmpty()) {
                return err(400, "成员不存在：" + id);
            }
        }
        return null;
    }

    private static boolean isOwner(Map<String, Object> row, User user) {
        return Views.number(row.get("owner_id")) == user.getId();
    }

    private List<Map<String, Object>> projectMaps(List<Map<String, Object>> rows) {
        return rows.stream().map(this::projectMap).toList();
    }

    private List<Map<String, Object>> taskMaps(List<Map<String, Object>> rows) {
        return rows.stream().map(this::taskMap).toList();
    }

    private Map<String, Object> projectDetail(long id) {
        Map<String, Object> out = projects.findById(id).map(this::projectMap).orElse(new LinkedHashMap<>());
        out.put("tasks", taskMaps(tasks.findByProject(id)));
        return out;
    }

    private Map<String, Object> projectMap(Map<String, Object> row) {
        long id = Views.number(row.get("id"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("name", Views.str(row.get("name")));
        m.put("description", Views.str(row.get("description")));
        m.put("status", Views.str(row.get("status")));
        m.put("start_date", dateOrNull(row.get("start_date")));
        m.put("end_date", dateOrNull(row.get("end_date")));
        m.put("owner_id", Views.number(row.get("owner_id")));
        m.put("owner_name", Views.str(row.get("owner_name")));
        m.put("task_count", Views.number(row.get("task_count")));
        m.put("done_task_count", Views.number(row.get("done_task_count")));
        m.put("progress", Views.progress(row.get("done_task_count"), row.get("task_count")));
        m.put("created_at", datetime(row.get("created_at")));
        m.put("member_ids", projects.memberIds(id));
        return m;
    }

    private Map<String, Object> taskMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Views.number(row.get("id")));
        m.put("project_id", Views.number(row.get("project_id")));
        m.put("title", Views.str(row.get("title")));
        m.put("description", Views.str(row.get("description")));
        m.put("assignee_id", longValue(row.get("assignee_id")));
        m.put("assignee_name", Views.str(row.get("assignee_name")));
        m.put("status", Views.str(row.get("status")));
        m.put("priority", Views.str(row.get("priority")));
        m.put("due_date", dateOrNull(row.get("due_date")));
        m.put("created_at", datetime(row.get("created_at")));
        m.put("updated_at", datetime(row.get("updated_at")));
        return m;
    }

    private Map<String, Object> userMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Views.number(row.get("id")));
        m.put("username", Views.str(row.get("username")));
        m.put("display_name", Views.str(row.get("display_name")));
        Object staff = row.get("is_staff");
        m.put("is_staff", staff instanceof Number n ? n.intValue() : 0);
        m.put("created_at", datetime(row.get("created_at")));
        return m;
    }

    private static String dateOrNull(Object value) {
        String s = Views.date(value);
        return s.isEmpty() ? null : s;
    }

    private static String datetime(Object value) {
        String s = Views.str(value);
        if (s.isEmpty()) {
            return null;
        }
        return s.length() >= 19 ? s.substring(0, 19).replace('T', ' ') : s;
    }
}
