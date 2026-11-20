package com.example.pms.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** 周复盘任务流写入体（weekly.flows[] 元素）。 */
public record FlowIn(
        @JsonProperty("bucket") String bucket,
        @JsonProperty("content") String content,
        @JsonProperty("seq") int seq
) {
    public String bu() { return bucket == null || bucket.isBlank() ? "todo" : bucket; }
    public String co() { return content == null ? "" : content; }
}
