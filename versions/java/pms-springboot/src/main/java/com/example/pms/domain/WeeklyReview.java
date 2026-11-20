package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** 每周复盘清单，对应 weekly_reviews；含 domains/flows 明细与读时计算 week_end。 */
public class WeeklyReview {

    private long id;
    private long userId;
    private String weekStart = "";

    private String nextMit1 = "";
    private String nextMit2 = "";
    private String nextMit3 = "";
    private String deepWorkTime = "";
    private String prepEnvMaterials = "";
    private String ifCondition = "";
    private String thenAction = "";

    private boolean qSkilledBlock;
    private boolean qLowValueTime;
    private boolean qEstimateGap;
    private boolean qRestConnection;
    private boolean qNextAdjust;

    private String createdAt;
    private String updatedAt;
    private List<WeeklyDomainReview> domains = new ArrayList<>();
    private List<WeeklyTaskFlow> flows = new ArrayList<>();

    private String weekEnd;

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @JsonProperty("user_id") public long getUserId() { return userId; }
    public void setUserId(long v) { userId = v; }
    @JsonProperty("week_start") public String getWeekStart() { return weekStart; }
    public void setWeekStart(String v) { weekStart = v == null ? "" : v; }

    @JsonProperty("next_mit_1") public String getNextMit1() { return nextMit1; }
    public void setNextMit1(String v) { nextMit1 = v == null ? "" : v; }
    @JsonProperty("next_mit_2") public String getNextMit2() { return nextMit2; }
    public void setNextMit2(String v) { nextMit2 = v == null ? "" : v; }
    @JsonProperty("next_mit_3") public String getNextMit3() { return nextMit3; }
    public void setNextMit3(String v) { nextMit3 = v == null ? "" : v; }
    @JsonProperty("deep_work_time") public String getDeepWorkTime() { return deepWorkTime; }
    public void setDeepWorkTime(String v) { deepWorkTime = v == null ? "" : v; }
    @JsonProperty("prep_env_materials") public String getPrepEnvMaterials() { return prepEnvMaterials; }
    public void setPrepEnvMaterials(String v) { prepEnvMaterials = v == null ? "" : v; }
    @JsonProperty("if_condition") public String getIfCondition() { return ifCondition; }
    public void setIfCondition(String v) { ifCondition = v == null ? "" : v; }
    @JsonProperty("then_action") public String getThenAction() { return thenAction; }
    public void setThenAction(String v) { thenAction = v == null ? "" : v; }

    @JsonProperty("q_skilled_block") public boolean isQSkilledBlock() { return qSkilledBlock; }
    public void setQSkilledBlock(boolean v) { qSkilledBlock = v; }
    @JsonProperty("q_low_value_time") public boolean isQLowValueTime() { return qLowValueTime; }
    public void setQLowValueTime(boolean v) { qLowValueTime = v; }
    @JsonProperty("q_estimate_gap") public boolean isQEstimateGap() { return qEstimateGap; }
    public void setQEstimateGap(boolean v) { qEstimateGap = v; }
    @JsonProperty("q_rest_connection") public boolean isQRestConnection() { return qRestConnection; }
    public void setQRestConnection(boolean v) { qRestConnection = v; }
    @JsonProperty("q_next_adjust") public boolean isQNextAdjust() { return qNextAdjust; }
    public void setQNextAdjust(boolean v) { qNextAdjust = v; }

    @JsonProperty("created_at") public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
    @JsonProperty("updated_at") public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String v) { updatedAt = v; }

    @JsonProperty("domains") public List<WeeklyDomainReview> getDomains() { return domains; }
    public void setDomains(List<WeeklyDomainReview> v) { domains = v == null ? new ArrayList<>() : v; }
    @JsonProperty("flows") public List<WeeklyTaskFlow> getFlows() { return flows; }
    public void setFlows(List<WeeklyTaskFlow> v) { flows = v == null ? new ArrayList<>() : v; }

    @JsonProperty("week_end") public String getWeekEnd() { return weekEnd; }
    public void setWeekEnd(String weekEnd) { this.weekEnd = weekEnd; }
}
