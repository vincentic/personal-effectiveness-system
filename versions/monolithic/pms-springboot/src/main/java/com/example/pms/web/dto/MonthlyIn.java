package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 每月重构清单写入体，字段 snake_case 与 schema 一致。 */
public record MonthlyIn(
        @JsonProperty("month") String month,
        @JsonProperty("top_achievement") String topAchievement,
        @JsonProperty("worth_continue") String worthContinue,
        @JsonProperty("worth_reason") String worthReason,
        @JsonProperty("most_valuable_input") String mostValuableInput,
        @JsonProperty("biggest_opportunity_cost") String biggestOpportunityCost,
        @JsonProperty("keep_item") String keepItem,
        @JsonProperty("cut_item") String cutItem,
        @JsonProperty("experiment_item") String experimentItem,
        @JsonProperty("q_skill_portfolio") boolean qSkillPortfolio,
        @JsonProperty("q_career_alignment") boolean qCareerAlignment,
        @JsonProperty("q_life_autonomy") boolean qLifeAutonomy,
        @JsonProperty("q_recovery_relations") boolean qRecoveryRelations,
        @JsonProperty("q_longterm_decision") boolean qLongtermDecision,
        @JsonProperty("health_checks") List<HealthIn> healthChecks
) {
    public String wc() { return worthContinue == null || worthContinue.isBlank() ? "yes" : worthContinue; }
    public List<HealthIn> safeHealthChecks() { return healthChecks == null ? List.of() : healthChecks; }
}
