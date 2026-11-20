package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 系统健康检查，对应 system_health_checks；monthly.health_checks[] 元素。 */
public class SystemHealthCheck {

    private long id;
    private long monthlyReviewId;
    private String module = "direction";
    private Integer score;
    private String neededChange = "";

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @JsonIgnore public long getMonthlyReviewId() { return monthlyReviewId; }
    public void setMonthlyReviewId(long v) { monthlyReviewId = v; }
    @JsonProperty("module") public String getModule() { return module; }
    public void setModule(String v) { module = v == null ? "" : v; }
    @JsonProperty("score") public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    @JsonProperty("needed_change") public String getNeededChange() { return neededChange; }
    public void setNeededChange(String v) { neededChange = v == null ? "" : v; }
}
