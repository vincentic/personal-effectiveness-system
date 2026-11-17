package com.example.pms.web;

import com.example.pms.repo.ProjectRepository;
import com.example.pms.repo.TaskRepository;
import com.example.pms.repo.UserRepository;
import com.example.pms.support.Views;
import com.example.pms.web.dto.TaskForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.time.format.DateTimeParseException;
import java.util.Map;

/** 任务新建 / 编辑 / 删除；与原 Django 版一致，写操作仅要求登录。 */
@Controller
public class TaskController {

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserRepository users;

    public TaskController(TaskRepository tasks, ProjectRepository projects, UserRepository users) {
        this.tasks = tasks;
        this.projects = projects;
        this.users = users;
    }

    @GetMapping("/projects/{projectId}/tasks/new/")
    public String createForm(@PathVariable long projectId, Model model) {
        Map<String, Object> row = projects.findById(projectId).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        model.addAttribute("form", new TaskForm());
        return formView(model, projectId, Views.str(row.get("name")), "新建任务",
                "/projects/" + projectId + "/tasks/new/");
    }

    @PostMapping("/projects/{projectId}/tasks/new/")
    public String create(@PathVariable long projectId,
                         @Valid @ModelAttribute("form") TaskForm form,
                         BindingResult errors, Model model) {
        Map<String, Object> row = projects.findById(projectId).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        validate(form, errors);
        if (errors.hasErrors()) {
            return formView(model, projectId, Views.str(row.get("name")), "新建任务",
                    "/projects/" + projectId + "/tasks/new/");
        }
        tasks.create(projectId, form.getTitle().trim(), nvl(form.getDescription()),
                form.getAssigneeId(), form.getStatus(), form.getPriority(),
                Views.parseDate(form.getDueDate()));
        return "redirect:/projects/" + projectId + "/";
    }

    @GetMapping("/tasks/{id}/edit/")
    public String editForm(@PathVariable long id, Model model) {
        Map<String, Object> row = tasks.findById(id).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        model.addAttribute("form", toForm(row));
        return formView(model, Views.number(row.get("project_id")),
                Views.str(row.get("project_name")), "编辑任务", "/tasks/" + id + "/edit/");
    }

    @PostMapping("/tasks/{id}/edit/")
    public String edit(@PathVariable long id,
                       @Valid @ModelAttribute("form") TaskForm form,
                       BindingResult errors, Model model) {
        Map<String, Object> row = tasks.findById(id).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        long projectId = Views.number(row.get("project_id"));
        validate(form, errors);
        if (errors.hasErrors()) {
            return formView(model, projectId, Views.str(row.get("project_name")), "编辑任务",
                    "/tasks/" + id + "/edit/");
        }
        tasks.update(id, form.getTitle().trim(), nvl(form.getDescription()),
                form.getAssigneeId(), form.getStatus(), form.getPriority(),
                Views.parseDate(form.getDueDate()));
        return "redirect:/projects/" + projectId + "/";
    }

    @GetMapping("/tasks/{id}/delete/")
    public String delete(@PathVariable long id) {
        Map<String, Object> row = tasks.findById(id).orElse(null);
        long projectId = row == null ? 0 : Views.number(row.get("project_id"));
        if (row != null) {
            tasks.delete(id);
        }
        return projectId == 0
                ? "redirect:/projects/"
                : "redirect:/projects/" + projectId + "/";
    }

    private TaskForm toForm(Map<String, Object> row) {
        TaskForm form = new TaskForm();
        form.setTitle(Views.str(row.get("title")));
        form.setDescription(Views.str(row.get("description")));
        form.setAssigneeId(row.get("assignee_id") == null ? null : Views.number(row.get("assignee_id")));
        form.setStatus(Views.str(row.get("status") == null ? "todo" : row.get("status")));
        form.setPriority(Views.str(row.get("priority") == null ? "medium" : row.get("priority")));
        form.setDueDate(Views.date(row.get("due_date")));
        return form;
    }

    private String formView(Model model, long projectId, String projectName,
                            String title, String action) {
        model.addAttribute("form", model.getAttribute("form"));
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectName", projectName);
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        model.addAttribute("cancelUrl", "/projects/" + projectId + "/");
        model.addAttribute("users", users.findAll());
        return "task_form";
    }

    private void validate(TaskForm form, BindingResult errors) {
        if (!Views.in(form.getStatus(), "todo", "doing", "done")) {
            errors.rejectValue("status", "invalid", "任务状态不合法");
        }
        if (!Views.in(form.getPriority(), "low", "medium", "high")) {
            errors.rejectValue("priority", "invalid", "优先级不合法");
        }
        if (form.getAssigneeId() != null && users.findById(form.getAssigneeId()).isEmpty()) {
            errors.rejectValue("assigneeId", "invalid", "执行人不存在");
        }
        try {
            Views.parseDate(form.getDueDate());
        } catch (DateTimeParseException e) {
            errors.rejectValue("dueDate", "invalid", "日期格式应为 YYYY-MM-DD");
        }
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
