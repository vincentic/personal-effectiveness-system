package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 每日执行清单，对应 daily_plans；含 blocks 明细与读时计算字段。 */
public class DailyPlan {

    private long id;
    private long userId;
    private String planDate = "";
    private BigDecimal sleepHours;
    private String energyLevel = "medium";
    private String mit = "";

    private boolean preKnowOutput;
    private boolean preMaterialsReady;
    private boolean prePhoneHandled;
    private boolean duringOneBlock;
    private boolean duringShrinkStep;
    private boolean duringRecordDistraction;
    private boolean postVisibleOutput;
    private boolean postRecall;
    private boolean postNextStep;

    private boolean lifeWaterFood;
    private boolean lifeWalkExercise;
    private boolean lifeCleanTidy;
    private boolean lifeSocialOutdoor;

    private String reviewAccomplished = "";
    private String reviewResistance = "";
    private String reviewTomorrowStart = "";
    private String reviewGratitude = "";

    private String createdAt;
    private String updatedAt;
    private List<TimeBlock> blocks = new ArrayList<>();

    // 读时计算字段（由 repo 组装时写入）
    private int blocksTotal;
    private int blocksDone;
    private int blockProgress;
    private int checklistTotal = 13;
    private int checklistDone;

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    @JsonProperty("user_id") public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }

    @JsonProperty("plan_date") public String getPlanDate() { return planDate; }
    public void setPlanDate(String planDate) { this.planDate = planDate == null ? "" : planDate; }

    @JsonProperty("sleep_hours") public BigDecimal getSleepHours() { return sleepHours; }
    public void setSleepHours(BigDecimal sleepHours) { this.sleepHours = sleepHours; }

    @JsonProperty("energy_level") public String getEnergyLevel() { return energyLevel; }
    public void setEnergyLevel(String energyLevel) { this.energyLevel = energyLevel; }

    @JsonProperty("mit") public String getMit() { return mit; }
    public void setMit(String mit) { this.mit = mit == null ? "" : mit; }

    @JsonProperty("pre_know_output") public boolean isPreKnowOutput() { return preKnowOutput; }
    public void setPreKnowOutput(boolean v) { preKnowOutput = v; }
    @JsonProperty("pre_materials_ready") public boolean isPreMaterialsReady() { return preMaterialsReady; }
    public void setPreMaterialsReady(boolean v) { preMaterialsReady = v; }
    @JsonProperty("pre_phone_handled") public boolean isPrePhoneHandled() { return prePhoneHandled; }
    public void setPrePhoneHandled(boolean v) { prePhoneHandled = v; }
    @JsonProperty("during_one_block") public boolean isDuringOneBlock() { return duringOneBlock; }
    public void setDuringOneBlock(boolean v) { duringOneBlock = v; }
    @JsonProperty("during_shrink_step") public boolean isDuringShrinkStep() { return duringShrinkStep; }
    public void setDuringShrinkStep(boolean v) { duringShrinkStep = v; }
    @JsonProperty("during_record_distraction") public boolean isDuringRecordDistraction() { return duringRecordDistraction; }
    public void setDuringRecordDistraction(boolean v) { duringRecordDistraction = v; }
    @JsonProperty("post_visible_output") public boolean isPostVisibleOutput() { return postVisibleOutput; }
    public void setPostVisibleOutput(boolean v) { postVisibleOutput = v; }
    @JsonProperty("post_recall") public boolean isPostRecall() { return postRecall; }
    public void setPostRecall(boolean v) { postRecall = v; }
    @JsonProperty("post_next_step") public boolean isPostNextStep() { return postNextStep; }
    public void setPostNextStep(boolean v) { postNextStep = v; }
    @JsonProperty("life_water_food") public boolean isLifeWaterFood() { return lifeWaterFood; }
    public void setLifeWaterFood(boolean v) { lifeWaterFood = v; }
    @JsonProperty("life_walk_exercise") public boolean isLifeWalkExercise() { return lifeWalkExercise; }
    public void setLifeWalkExercise(boolean v) { lifeWalkExercise = v; }
    @JsonProperty("life_clean_tidy") public boolean isLifeCleanTidy() { return lifeCleanTidy; }
    public void setLifeCleanTidy(boolean v) { lifeCleanTidy = v; }
    @JsonProperty("life_social_outdoor") public boolean isLifeSocialOutdoor() { return lifeSocialOutdoor; }
    public void setLifeSocialOutdoor(boolean v) { lifeSocialOutdoor = v; }

    @JsonProperty("review_accomplished") public String getReviewAccomplished() { return reviewAccomplished; }
    public void setReviewAccomplished(String v) { reviewAccomplished = v == null ? "" : v; }
    @JsonProperty("review_resistance") public String getReviewResistance() { return reviewResistance; }
    public void setReviewResistance(String v) { reviewResistance = v == null ? "" : v; }
    @JsonProperty("review_tomorrow_start") public String getReviewTomorrowStart() { return reviewTomorrowStart; }
    public void setReviewTomorrowStart(String v) { reviewTomorrowStart = v == null ? "" : v; }
    @JsonProperty("review_gratitude") public String getReviewGratitude() { return reviewGratitude; }
    public void setReviewGratitude(String v) { reviewGratitude = v == null ? "" : v; }

    @JsonProperty("created_at") public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    @JsonProperty("updated_at") public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }

    @JsonProperty("blocks") public List<TimeBlock> getBlocks() { return blocks; }
    public void setBlocks(List<TimeBlock> blocks) { this.blocks = blocks == null ? new ArrayList<>() : blocks; }

    @JsonProperty("blocks_total") public int getBlocksTotal() { return blocksTotal; }
    public void setBlocksTotal(int v) { blocksTotal = v; }
    @JsonProperty("blocks_done") public int getBlocksDone() { return blocksDone; }
    public void setBlocksDone(int v) { blocksDone = v; }
    @JsonProperty("block_progress") public int getBlockProgress() { return blockProgress; }
    public void setBlockProgress(int v) { blockProgress = v; }
    @JsonProperty("checklist_total") public int getChecklistTotal() { return checklistTotal; }
    public void setChecklistTotal(int v) { checklistTotal = v; }
    @JsonProperty("checklist_done") public int getChecklistDone() { return checklistDone; }
    public void setChecklistDone(int v) { checklistDone = v; }
}
