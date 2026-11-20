package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 时间盒组块，对应 time_blocks；daily.blocks[] 元素。 */
public class TimeBlock {

    private long id;
    private long dailyPlanId;
    private int seq;
    private String blockName = "";
    private String inputDesc = "";
    private String keyAction = "";
    private String deliverable = "";
    private String timeStart;
    private String timeEnd;
    private boolean isDone;
    private String createdAt;
    private String updatedAt;

    @JsonProperty("id")
    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @JsonIgnore
    public long getDailyPlanId() { return dailyPlanId; }
    public void setDailyPlanId(long dailyPlanId) { this.dailyPlanId = dailyPlanId; }

    @JsonProperty("seq")
    public int getSeq() { return seq; }
    public void setSeq(int seq) { this.seq = seq; }

    @JsonProperty("block_name")
    public String getBlockName() { return blockName; }
    public void setBlockName(String blockName) { this.blockName = blockName == null ? "" : blockName; }

    @JsonProperty("input_desc")
    public String getInputDesc() { return inputDesc; }
    public void setInputDesc(String inputDesc) { this.inputDesc = inputDesc == null ? "" : inputDesc; }

    @JsonProperty("key_action")
    public String getKeyAction() { return keyAction; }
    public void setKeyAction(String keyAction) { this.keyAction = keyAction == null ? "" : keyAction; }

    @JsonProperty("deliverable")
    public String getDeliverable() { return deliverable; }
    public void setDeliverable(String deliverable) { this.deliverable = deliverable == null ? "" : deliverable; }

    @JsonProperty("time_start")
    public String getTimeStart() { return timeStart; }
    public void setTimeStart(String timeStart) { this.timeStart = timeStart; }

    @JsonProperty("time_end")
    public String getTimeEnd() { return timeEnd; }
    public void setTimeEnd(String timeEnd) { this.timeEnd = timeEnd; }

    @JsonProperty("is_done")
    public boolean isDone() { return isDone; }
    public void setDone(boolean done) { isDone = done; }

    @JsonIgnore
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    @JsonIgnore
    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    /** 只读展示：时间盒范围或「未设定」。 */
    @JsonIgnore
    public String getTimeBox() {
        return timeStart != null && timeEnd != null ? timeStart + "–" + timeEnd : "未设定";
    }
}
