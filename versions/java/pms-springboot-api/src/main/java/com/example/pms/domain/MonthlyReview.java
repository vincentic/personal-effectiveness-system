package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** 每月重构清单，对应 monthly_reviews；含 health_checks 明细与读时计算 avg_score。 */
public class MonthlyReview {

    private long id;
    private long userId;
    private String month = "";

    private String topAchievement = "";
    private String worthContinue = "yes";
    private String worthReason = "";
    private String mostValuableInput = "";
    private String biggestOpportunityCost = "";
    private String keepItem = "";
    private String cutItem = "";
    private String experimentItem = "";

    private boolean qSkillPortfolio;
    private boolean qCareerAlignment;
    private boolean qLifeAutonomy;
    private boolean qRecoveryRelations;
    private boolean qLongtermDecision;

    private String createdAt;
    private String updatedAt;
    private List<SystemHealthCheck> healthChecks = new ArrayList<>();

    private Double avgScore;

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @JsonProperty("user_id") public long getUserId() { return userId; }
    public void setUserId(long v) { userId = v; }
    @JsonProperty("month") public String getMonth() { return month; }
    public void setMonth(String v) { month = v == null ? "" : v; }

    @JsonProperty("top_achievement") public String getTopAchievement() { return topAchievement; }
    public void setTopAchievement(String v) { topAchievement = v == null ? "" : v; }
    @JsonProperty("worth_continue") public String getWorthContinue() { return worthContinue; }
    public void setWorthContinue(String v) { worthContinue = v == null ? "" : v; }
    @JsonProperty("worth_reason") public String getWorthReason() { return worthReason; }
    public void setWorthReason(String v) { worthReason = v == null ? "" : v; }
    @JsonProperty("most_valuable_input") public String getMostValuableInput() { return mostValuableInput; }
    public void setMostValuableInput(String v) { mostValuableInput = v == null ? "" : v; }
    @JsonProperty("biggest_opportunity_cost") public String getBiggestOpportunityCost() { return biggestOpportunityCost; }
    public void setBiggestOpportunityCost(String v) { biggestOpportunityCost = v == null ? "" : v; }
    @JsonProperty("keep_item") public String getKeepItem() { return keepItem; }
    public void setKeepItem(String v) { keepItem = v == null ? "" : v; }
    @JsonProperty("cut_item") public String getCutItem() { return cutItem; }
    public void setCutItem(String v) { cutItem = v == null ? "" : v; }
    @JsonProperty("experiment_item") public String getExperimentItem() { return experimentItem; }
    public void setExperimentItem(String v) { experimentItem = v == null ? "" : v; }

    @JsonProperty("q_skill_portfolio") public boolean isQSkillPortfolio() { return qSkillPortfolio; }
    public void setQSkillPortfolio(boolean v) { qSkillPortfolio = v; }
    @JsonProperty("q_career_alignment") public boolean isQCareerAlignment() { return qCareerAlignment; }
    public void setQCareerAlignment(boolean v) { qCareerAlignment = v; }
    @JsonProperty("q_life_autonomy") public boolean isQLifeAutonomy() { return qLifeAutonomy; }
    public void setQLifeAutonomy(boolean v) { qLifeAutonomy = v; }
    @JsonProperty("q_recovery_relations") public boolean isQRecoveryRelations() { return qRecoveryRelations; }
    public void setQRecoveryRelations(boolean v) { qRecoveryRelations = v; }
    @JsonProperty("q_longterm_decision") public boolean isQLongtermDecision() { return qLongtermDecision; }
    public void setQLongtermDecision(boolean v) { qLongtermDecision = v; }

    @JsonProperty("created_at") public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String v) { createdAt = v; }
    @JsonProperty("updated_at") public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String v) { updatedAt = v; }

    @JsonProperty("health_checks") public List<SystemHealthCheck> getHealthChecks() { return healthChecks; }
    public void setHealthChecks(List<SystemHealthCheck> v) { healthChecks = v == null ? new ArrayList<>() : v; }

    @JsonProperty("avg_score") public Double getAvgScore() { return avgScore; }
    public void setAvgScore(Double avgScore) { this.avgScore = avgScore; }
}
