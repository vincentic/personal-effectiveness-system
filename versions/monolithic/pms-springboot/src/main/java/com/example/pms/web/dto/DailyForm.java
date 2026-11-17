package com.example.pms.web.dto;

import java.util.ArrayList;
import java.util.List;

/** 每日执行清单表单（页面），明细固定 4 行时间盒。 */
public class DailyForm {

    private String planDate = "";
    private String sleepHours = "";
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

    private List<BlockRow> blocks = new ArrayList<>();

    public static class BlockRow {
        private int seq;
        private String blockName = "";
        private String inputDesc = "";
        private String keyAction = "";
        private String deliverable = "";
        private String timeStart = "";
        private String timeEnd = "";
        private boolean done;

        public int getSeq() { return seq; }
        public void setSeq(int seq) { this.seq = seq; }
        public String getBlockName() { return blockName; }
        public void setBlockName(String v) { blockName = v == null ? "" : v; }
        public String getInputDesc() { return inputDesc; }
        public void setInputDesc(String v) { inputDesc = v == null ? "" : v; }
        public String getKeyAction() { return keyAction; }
        public void setKeyAction(String v) { keyAction = v == null ? "" : v; }
        public String getDeliverable() { return deliverable; }
        public void setDeliverable(String v) { deliverable = v == null ? "" : v; }
        public String getTimeStart() { return timeStart; }
        public void setTimeStart(String v) { timeStart = v == null ? "" : v; }
        public String getTimeEnd() { return timeEnd; }
        public void setTimeEnd(String v) { timeEnd = v == null ? "" : v; }
        public boolean isDone() { return done; }
        public void setDone(boolean done) { this.done = done; }
    }

    /** 新建时的 4 行空白明细。 */
    public static DailyForm blank(String planDate) {
        DailyForm f = new DailyForm();
        f.setPlanDate(planDate);
        for (int i = 1; i <= 4; i++) {
            BlockRow r = new BlockRow();
            r.setSeq(i);
            f.getBlocks().add(r);
        }
        return f;
    }

    public String getPlanDate() { return planDate; }
    public void setPlanDate(String v) { planDate = v == null ? "" : v; }
    public String getSleepHours() { return sleepHours; }
    public void setSleepHours(String v) { sleepHours = v == null ? "" : v; }
    public String getEnergyLevel() { return energyLevel; }
    public void setEnergyLevel(String v) { energyLevel = v == null ? "" : v; }
    public String getMit() { return mit; }
    public void setMit(String v) { mit = v == null ? "" : v; }

    public boolean isPreKnowOutput() { return preKnowOutput; }
    public void setPreKnowOutput(boolean v) { preKnowOutput = v; }
    public boolean isPreMaterialsReady() { return preMaterialsReady; }
    public void setPreMaterialsReady(boolean v) { preMaterialsReady = v; }
    public boolean isPrePhoneHandled() { return prePhoneHandled; }
    public void setPrePhoneHandled(boolean v) { prePhoneHandled = v; }
    public boolean isDuringOneBlock() { return duringOneBlock; }
    public void setDuringOneBlock(boolean v) { duringOneBlock = v; }
    public boolean isDuringShrinkStep() { return duringShrinkStep; }
    public void setDuringShrinkStep(boolean v) { duringShrinkStep = v; }
    public boolean isDuringRecordDistraction() { return duringRecordDistraction; }
    public void setDuringRecordDistraction(boolean v) { duringRecordDistraction = v; }
    public boolean isPostVisibleOutput() { return postVisibleOutput; }
    public void setPostVisibleOutput(boolean v) { postVisibleOutput = v; }
    public boolean isPostRecall() { return postRecall; }
    public void setPostRecall(boolean v) { postRecall = v; }
    public boolean isPostNextStep() { return postNextStep; }
    public void setPostNextStep(boolean v) { postNextStep = v; }
    public boolean isLifeWaterFood() { return lifeWaterFood; }
    public void setLifeWaterFood(boolean v) { lifeWaterFood = v; }
    public boolean isLifeWalkExercise() { return lifeWalkExercise; }
    public void setLifeWalkExercise(boolean v) { lifeWalkExercise = v; }
    public boolean isLifeCleanTidy() { return lifeCleanTidy; }
    public void setLifeCleanTidy(boolean v) { lifeCleanTidy = v; }
    public boolean isLifeSocialOutdoor() { return lifeSocialOutdoor; }
    public void setLifeSocialOutdoor(boolean v) { lifeSocialOutdoor = v; }

    public String getReviewAccomplished() { return reviewAccomplished; }
    public void setReviewAccomplished(String v) { reviewAccomplished = v == null ? "" : v; }
    public String getReviewResistance() { return reviewResistance; }
    public void setReviewResistance(String v) { reviewResistance = v == null ? "" : v; }
    public String getReviewTomorrowStart() { return reviewTomorrowStart; }
    public void setReviewTomorrowStart(String v) { reviewTomorrowStart = v == null ? "" : v; }
    public String getReviewGratitude() { return reviewGratitude; }
    public void setReviewGratitude(String v) { reviewGratitude = v == null ? "" : v; }

    public List<BlockRow> getBlocks() { return blocks; }
    public void setBlocks(List<BlockRow> blocks) { this.blocks = blocks == null ? new ArrayList<>() : blocks; }
}
