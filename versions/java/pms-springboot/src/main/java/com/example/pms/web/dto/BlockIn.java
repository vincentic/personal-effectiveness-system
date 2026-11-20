package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 时间盒组块写入体（daily.blocks[] 元素）。 */
public record BlockIn(
        @JsonProperty("seq") int seq,
        @JsonProperty("block_name") String blockName,
        @JsonProperty("input_desc") String inputDesc,
        @JsonProperty("key_action") String keyAction,
        @JsonProperty("deliverable") String deliverable,
        @JsonProperty("time_start") String timeStart,
        @JsonProperty("time_end") String timeEnd,
        @JsonProperty("is_done") boolean isDone
) {
    public String bn() { return blockName == null ? "" : blockName.trim(); }
    public String in() { return inputDesc == null ? "" : inputDesc; }
    public String ka() { return keyAction == null ? "" : keyAction; }
    public String de() { return deliverable == null ? "" : deliverable; }
    public String ts() { return timeStart == null || timeStart.isBlank() ? null : timeStart.trim(); }
    public String te() { return timeEnd == null || timeEnd.isBlank() ? null : timeEnd.trim(); }
}
