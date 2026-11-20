package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 任务流条目（GTD + Kanban），对应 weekly_task_flows；weekly.flows[] 元素。 */
public class WeeklyTaskFlow {

    private long id;
    private long weeklyReviewId;
    private String bucket = "todo";
    private String content = "";
    private int seq;

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @JsonIgnore public long getWeeklyReviewId() { return weeklyReviewId; }
    public void setWeeklyReviewId(long v) { weeklyReviewId = v; }
    @JsonProperty("bucket") public String getBucket() { return bucket; }
    public void setBucket(String v) { bucket = v == null ? "" : v; }
    @JsonProperty("content") public String getContent() { return content; }
    public void setContent(String v) { content = v == null ? "" : v; }
    @JsonProperty("seq") public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }
}
