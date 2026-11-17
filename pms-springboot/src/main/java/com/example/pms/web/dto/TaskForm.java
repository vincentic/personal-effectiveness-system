package com.example.pms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新建 / 编辑任务表单。 */
public class TaskForm {

    @NotBlank(message = "请输入任务标题")
    @Size(max = 200, message = "任务标题不能超过 200 个字符")
    private String title = "";

    @Size(max = 65535, message = "任务描述过长")
    private String description = "";

    private Long assigneeId;

    private String status = "todo";

    private String priority = "medium";

    private String dueDate = "";

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public String getDueDate() { return dueDate; }
    public void setDueDate(String dueDate) { this.dueDate = dueDate; }
}
