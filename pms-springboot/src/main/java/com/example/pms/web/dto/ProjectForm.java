package com.example.pms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.ArrayList;
import java.util.List;

/** 新建 / 编辑项目表单。 */
public class ProjectForm {

    @NotBlank(message = "请输入项目名称")
    @Size(max = 100, message = "项目名称不能超过 100 个字符")
    private String name = "";

    @Size(max = 65535, message = "项目描述过长")
    private String description = "";

    private String status = "active";

    private String startDate = "";

    private String endDate = "";

    private List<Long> memberIds = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }
    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }
    public List<Long> getMemberIds() { return memberIds; }
    public void setMemberIds(List<Long> memberIds) { this.memberIds = memberIds; }
}
