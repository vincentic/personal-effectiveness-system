package com.example.pms.web;

import com.example.pms.repo.ProjectRepository;
import com.example.pms.repo.TaskRepository;
import com.example.pms.repo.UserRepository;
import com.example.pms.support.CurrentUser;
import com.example.pms.support.Views;
import com.example.pms.web.dto.ProjectForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/** 项目列表、新建 / 编辑 / 删除、详情与任务看板。 */
@Controller
public class ProjectController {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final UserRepository users;
    private final CurrentUser me;

    public ProjectController(ProjectRepository projects, TaskRepository tasks,
                             UserRepository users, CurrentUser me) {
        this.projects = projects;
        this.tasks = tasks;
        this.users = users;
        this.me = me;
    }

    @GetMapping("/projects/")
    public String list(@RequestParam(defaultValue = "") String status,
                       @RequestParam(defaultValue = "") String q, Model model) {
        model.addAttribute("projects",
                projects.findAll(status.trim(), q.trim()).stream().map(Views::project).toList());
        model.addAttribute("status", status);
        model.addAttribute("keyword", q);
        model.addAttribute("currentUserId", me.id());
        return "project_list";
    }

    @GetMapping("/projects/new/")
    public String createForm(Model model) {
        model.addAttribute("form", new ProjectForm());
        return formView(model, "新建项目", "/projects/new/", "/projects/");
    }

    @PostMapping("/projects/new/")
    public String create(@Valid @ModelAttribute("form") ProjectForm form,
                         BindingResult errors, Model model) {
        validate(form, errors);
        if (errors.hasErrors()) {
            return formView(model, "新建项目", "/projects/new/", "/projects/");
        }
        long id = projects.create(
                form.getName().trim(), nvl(form.getDescription()), form.getStatus(),
                Views.parseDate(form.getStartDate()), Views.parseDate(form.getEndDate()),
                me.id(), form.getMemberIds());
        return "redirect:/projects/" + id + "/";
    }

    @GetMapping("/projects/{id}/")
    public String detail(@PathVariable long id, Model model) {
        Map<String, Object> row = projects.findById(id).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        Map<String, Object> project = Views.project(row);
        project.put("memberNames", String.join("、", projects.memberNames(id)));
        boolean isOwner = isOwner(row);
        model.addAttribute("project", project);
        model.addAttribute("isOwner", isOwner);
        model.addAttribute("currentUserId", me.id());

        List<Map<String, Object>> all = tasks.findByProject(id).stream().map(Views::task).toList();
        model.addAttribute("todoTasks", filter(all, "todo"));
        model.addAttribute("doingTasks", filter(all, "doing"));
        model.addAttribute("doneTasks", filter(all, "done"));
        return "project_detail";
    }

    @GetMapping("/projects/{id}/edit/")
    public String editForm(@PathVariable long id, Model model) {
        Map<String, Object> row = projects.findById(id).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        if (!isOwner(row)) {
            return "redirect:/projects/" + id + "/";
        }
        model.addAttribute("form", toForm(row));
        return formView(model, "编辑项目", "/projects/" + id + "/edit/", "/projects/" + id + "/");
    }

    @PostMapping("/projects/{id}/edit/")
    public String edit(@PathVariable long id,
                       @Valid @ModelAttribute("form") ProjectForm form,
                       BindingResult errors, Model model) {
        Map<String, Object> row = projects.findById(id).orElse(null);
        if (row == null) {
            return "redirect:/projects/";
        }
        if (!isOwner(row)) {
            return "redirect:/projects/" + id + "/";
        }
        validate(form, errors);
        if (errors.hasErrors()) {
            return formView(model, "编辑项目", "/projects/" + id + "/edit/", "/projects/" + id + "/");
        }
        projects.update(id, form.getName().trim(), nvl(form.getDescription()), form.getStatus(),
                Views.parseDate(form.getStartDate()), Views.parseDate(form.getEndDate()),
                form.getMemberIds());
        return "redirect:/projects/" + id + "/";
    }

    @GetMapping("/projects/{id}/delete/")
    public String delete(@PathVariable long id) {
        projects.findById(id).filter(this::isOwner).ifPresent(ignored -> projects.delete(id));
        return "redirect:/projects/";
    }

    private ProjectForm toForm(Map<String, Object> row) {
        ProjectForm form = new ProjectForm();
        form.setName(Views.str(row.get("name")));
        form.setDescription(Views.str(row.get("description")));
        form.setStatus(Views.str(row.get("status")));
        form.setStartDate(Views.date(row.get("start_date")));
        form.setEndDate(Views.date(row.get("end_date")));
        form.setMemberIds(projects.memberIds(Views.number(row.get("id"))));
        return form;
    }

    private String formView(Model model, String title, String action, String cancelUrl) {
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        model.addAttribute("cancelUrl", cancelUrl);
        model.addAttribute("users", users.findAll());
        return "project_form";
    }

    private boolean isOwner(Map<String, Object> row) {
        return Views.number(row.get("owner_id")) == me.id();
    }

    private void validate(ProjectForm form, BindingResult errors) {
        if (!Views.in(form.getStatus(), "active", "paused", "done")) {
            errors.rejectValue("status", "invalid", "项目状态不合法");
        }
        checkDate(errors, "startDate", form.getStartDate());
        checkDate(errors, "endDate", form.getEndDate());
        for (Long memberId : form.getMemberIds()) {
            if (memberId != null && users.findById(memberId).isEmpty()) {
                errors.rejectValue("memberIds", "invalid", "包含不存在的用户");
                break;
            }
        }
    }

    private void checkDate(BindingResult errors, String field, String value) {
        try {
            Views.parseDate(value);
        } catch (DateTimeParseException e) {
            errors.rejectValue(field, "invalid", "日期格式应为 YYYY-MM-DD");
        }
    }

    private List<Map<String, Object>> filter(List<Map<String, Object>> all, String status) {
        return all.stream().filter(t -> status.equals(t.get("status"))).toList();
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }
}
