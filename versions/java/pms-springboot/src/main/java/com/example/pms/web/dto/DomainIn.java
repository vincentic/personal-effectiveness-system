package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 周复盘领域回顾写入体（weekly.domains[] 元素）。 */
public record DomainIn(
        @JsonProperty("domain") String domain,
        @JsonProperty("facts") String facts,
        @JsonProperty("status") String status
) {
    public String fa() { return facts == null ? "" : facts; }
    public String st() { return status == null || status.isBlank() ? "smooth" : status; }
}
