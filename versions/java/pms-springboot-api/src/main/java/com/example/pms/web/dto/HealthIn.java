package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 系统健康检查写入体（monthly.health_checks[] 元素）。 */
public record HealthIn(
        @JsonProperty("module") String module,
        @JsonProperty("score") Integer score,
        @JsonProperty("needed_change") String neededChange
) {
    public String nc() { return neededChange == null ? "" : neededChange; }
}
