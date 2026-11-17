package com.example.pms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 管理员新建用户表单。 */
public class UserForm {

    @NotBlank(message = "请输入用户名")
    @Size(max = 150, message = "用户名不能超过 150 个字符")
    private String username = "";

    @NotBlank(message = "请输入密码")
    @Size(min = 8, max = 72, message = "密码长度需为 8-72 个字符")
    private String password = "";

    @Size(max = 150, message = "显示名称过长")
    private String displayName = "";

    private boolean staff;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public boolean isStaff() { return staff; }
    public void setStaff(boolean staff) { this.staff = staff; }
}
