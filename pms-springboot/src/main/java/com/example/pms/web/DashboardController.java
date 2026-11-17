package com.example.pms.web;

import com.example.pms.repo.ProjectRepository;
import com.example.pms.repo.TaskRepository;
import com.example.pms.support.CurrentUser;
import com.example.pms.support.Views;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/** 工作台：全局统计 + 我的项目 + 我的待办。 */
@Controller
public class DashboardController {

    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final CurrentUser me;

    public DashboardController(ProjectRepository projects, TaskRepository tasks, CurrentUser me) {
        this.projects = projects;
        this.tasks = tasks;
        this.me = me;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        Map<String, Long> byStatus = tasks.countByStatus();
        model.addAttribute("projectCount", projects.count());
        model.addAttribute("taskCount", tasks.count());
        model.addAttribute("todoCount", byStatus.getOrDefault("todo", 0L));
        model.addAttribute("doingCount", byStatus.getOrDefault("doing", 0L));
        model.addAttribute("doneCount", byStatus.getOrDefault("done", 0L));
        model.addAttribute("myProjects",
                projects.findByOwner(me.id(), 5).stream().map(Views::project).toList());
        model.addAttribute("myTasks",
                tasks.findByAssignee(me.id(), 10).stream().map(Views::task).toList());
        return "dashboard";
    }
}
