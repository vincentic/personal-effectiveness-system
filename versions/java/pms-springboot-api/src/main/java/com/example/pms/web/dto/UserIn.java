package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 管理员创建用户写入体。 */
public record UserIn(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("is_staff") boolean isStaff
) {
    public String u() { return username == null ? "" : username.trim(); }
    public String p() { return password == null ? "" : password; }
    public String dn() { return displayName == null ? "" : displayName; }
}
