package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 登录写入体。 */
public record LoginIn(
        @JsonProperty("username") String username,
        @JsonProperty("password") String password
) {
    public String u() { return username == null ? "" : username.trim(); }
    public String p() { return password == null ? "" : password; }
}
