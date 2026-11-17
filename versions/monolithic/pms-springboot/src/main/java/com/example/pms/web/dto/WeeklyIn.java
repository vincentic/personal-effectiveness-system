package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** 每周复盘清单写入体，字段 snake_case 与 schema 一致。 */
public record WeeklyIn(
        @JsonProperty("week_start") String weekStart,
        @JsonProperty("next_mit_1") String nextMit1,
        @JsonProperty("next_mit_2") String nextMit2,
        @JsonProperty("next_mit_3") String nextMit3,
        @JsonProperty("deep_work_time") String deepWorkTime,
        @JsonProperty("prep_env_materials") String prepEnvMaterials,
        @JsonProperty("if_condition") String ifCondition,
        @JsonProperty("then_action") String thenAction,
        @JsonProperty("q_skilled_block") boolean qSkilledBlock,
        @JsonProperty("q_low_value_time") boolean qLowValueTime,
        @JsonProperty("q_estimate_gap") boolean qEstimateGap,
        @JsonProperty("q_rest_connection") boolean qRestConnection,
        @JsonProperty("q_next_adjust") boolean qNextAdjust,
        @JsonProperty("domains") List<DomainIn> domains,
        @JsonProperty("flows") List<FlowIn> flows
) {
    public List<DomainIn> safeDomains() { return domains == null ? List.of() : domains; }
    public List<FlowIn> safeFlows() { return flows == null ? List.of() : flows; }
}
