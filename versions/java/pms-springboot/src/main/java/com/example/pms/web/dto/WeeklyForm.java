package com.example.pms.web.dto;

import java.util.ArrayList;
import java.util.List;

/** 每周复盘清单表单（页面），明细固定 4 领域 + 8 任务流。 */
public class WeeklyForm {

    private String weekStart = "";
    private String nextMit1 = "";
    private String nextMit2 = "";
    private String nextMit3 = "";
    private String deepWorkTime = "";
    private String prepEnvMaterials = "";
    private String ifCondition = "";
    private String thenAction = "";

    private boolean qSkilledBlock;
    private boolean qLowValueTime;
    private boolean qEstimateGap;
    private boolean qRestConnection;
    private boolean qNextAdjust;

    private List<DomainRow> domains = new ArrayList<>();
    private List<FlowRow> flows = new ArrayList<>();

    public static class DomainRow {
        private String domain = "work";
        private String facts = "";
        private String status = "smooth";
        public String getDomain() { return domain; }
        public void setDomain(String v) { domain = v == null ? "" : v; }
        public String getFacts() { return facts; }
        public void setFacts(String v) { facts = v == null ? "" : v; }
        public String getStatus() { return status; }
        public void setStatus(String v) { status = v == null ? "" : v; }
    }

    public static class FlowRow {
        private String bucket = "todo";
        private String content = "";
        private int seq;
        public String getBucket() { return bucket; }
        public void setBucket(String v) { bucket = v == null ? "" : v; }
        public String getContent() { return content; }
        public void setContent(String v) { content = v == null ? "" : v; }
        public int getSeq() { return seq; }
        public void setSeq(int seq) { this.seq = seq; }
    }

    public static final String[] DOMAIN_KEYS = {"work", "study", "body", "relations"};

    /** 新建时的空白明细：4 个领域 + 8 条任务流。 */
    public static WeeklyForm blank(String weekStart) {
        WeeklyForm f = new WeeklyForm();
        f.setWeekStart(weekStart);
        for (String key : DOMAIN_KEYS) {
            DomainRow r = new DomainRow();
            r.setDomain(key);
            f.getDomains().add(r);
        }
        for (int i = 1; i <= 8; i++) {
            FlowRow r = new FlowRow();
            r.setSeq(i);
            r.setBucket("todo");
            f.getFlows().add(r);
        }
        return f;
    }

    public String getWeekStart() { return weekStart; }
    public void setWeekStart(String v) { weekStart = v == null ? "" : v; }
    public String getNextMit1() { return nextMit1; }
    public void setNextMit1(String v) { nextMit1 = v == null ? "" : v; }
    public String getNextMit2() { return nextMit2; }
    public void setNextMit2(String v) { nextMit2 = v == null ? "" : v; }
    public String getNextMit3() { return nextMit3; }
    public void setNextMit3(String v) { nextMit3 = v == null ? "" : v; }
    public String getDeepWorkTime() { return deepWorkTime; }
    public void setDeepWorkTime(String v) { deepWorkTime = v == null ? "" : v; }
    public String getPrepEnvMaterials() { return prepEnvMaterials; }
    public void setPrepEnvMaterials(String v) { prepEnvMaterials = v == null ? "" : v; }
    public String getIfCondition() { return ifCondition; }
    public void setIfCondition(String v) { ifCondition = v == null ? "" : v; }
    public String getThenAction() { return thenAction; }
    public void setThenAction(String v) { thenAction = v == null ? "" : v; }

    public boolean isQSkilledBlock() { return qSkilledBlock; }
    public void setQSkilledBlock(boolean v) { qSkilledBlock = v; }
    public boolean isQLowValueTime() { return qLowValueTime; }
    public void setQLowValueTime(boolean v) { qLowValueTime = v; }
    public boolean isQEstimateGap() { return qEstimateGap; }
    public void setQEstimateGap(boolean v) { qEstimateGap = v; }
    public boolean isQRestConnection() { return qRestConnection; }
    public void setQRestConnection(boolean v) { qRestConnection = v; }
    public boolean isQNextAdjust() { return qNextAdjust; }
    public void setQNextAdjust(boolean v) { qNextAdjust = v; }

    public List<DomainRow> getDomains() { return domains; }
    public void setDomains(List<DomainRow> v) { domains = v == null ? new ArrayList<>() : v; }
    public List<FlowRow> getFlows() { return flows; }
    public void setFlows(List<FlowRow> v) { flows = v == null ? new ArrayList<>() : v; }
}
