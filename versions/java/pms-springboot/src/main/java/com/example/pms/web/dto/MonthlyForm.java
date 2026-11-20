package com.example.pms.web.dto;

import java.util.ArrayList;
import java.util.List;

/** 每月重构清单表单（页面），明细固定 6 个健康检查模块。 */
public class MonthlyForm {

    private String month = "";
    private String topAchievement = "";
    private String worthContinue = "yes";
    private String worthReason = "";
    private String mostValuableInput = "";
    private String biggestOpportunityCost = "";
    private String keepItem = "";
    private String cutItem = "";
    private String experimentItem = "";

    private boolean qSkillPortfolio;
    private boolean qCareerAlignment;
    private boolean qLifeAutonomy;
    private boolean qRecoveryRelations;
    private boolean qLongtermDecision;

    private List<HealthRow> healthChecks = new ArrayList<>();

    public static class HealthRow {
        private String module = "direction";
        private String score = "";
        private String neededChange = "";
        public String getModule() { return module; }
        public void setModule(String v) { module = v == null ? "" : v; }
        public String getScore() { return score; }
        public void setScore(String v) { score = v == null ? "" : v; }
        public String getNeededChange() { return neededChange; }
        public void setNeededChange(String v) { neededChange = v == null ? "" : v; }
    }

    public static final String[] MODULE_KEYS = {
            "direction", "taskload", "blockquality", "timebox", "sleepenergy", "reviewquality"
    };

    /** 新建时的空白明细：6 个健康检查模块。 */
    public static MonthlyForm blank(String month) {
        MonthlyForm f = new MonthlyForm();
        f.setMonth(month);
        for (String key : MODULE_KEYS) {
            HealthRow r = new HealthRow();
            r.setModule(key);
            f.getHealthChecks().add(r);
        }
        return f;
    }

    public String getMonth() { return month; }
    public void setMonth(String v) { month = v == null ? "" : v; }
    public String getTopAchievement() { return topAchievement; }
    public void setTopAchievement(String v) { topAchievement = v == null ? "" : v; }
    public String getWorthContinue() { return worthContinue; }
    public void setWorthContinue(String v) { worthContinue = v == null ? "" : v; }
    public String getWorthReason() { return worthReason; }
    public void setWorthReason(String v) { worthReason = v == null ? "" : v; }
    public String getMostValuableInput() { return mostValuableInput; }
    public void setMostValuableInput(String v) { mostValuableInput = v == null ? "" : v; }
    public String getBiggestOpportunityCost() { return biggestOpportunityCost; }
    public void setBiggestOpportunityCost(String v) { biggestOpportunityCost = v == null ? "" : v; }
    public String getKeepItem() { return keepItem; }
    public void setKeepItem(String v) { keepItem = v == null ? "" : v; }
    public String getCutItem() { return cutItem; }
    public void setCutItem(String v) { cutItem = v == null ? "" : v; }
    public String getExperimentItem() { return experimentItem; }
    public void setExperimentItem(String v) { experimentItem = v == null ? "" : v; }

    public boolean isQSkillPortfolio() { return qSkillPortfolio; }
    public void setQSkillPortfolio(boolean v) { qSkillPortfolio = v; }
    public boolean isQCareerAlignment() { return qCareerAlignment; }
    public void setQCareerAlignment(boolean v) { qCareerAlignment = v; }
    public boolean isQLifeAutonomy() { return qLifeAutonomy; }
    public void setQLifeAutonomy(boolean v) { qLifeAutonomy = v; }
    public boolean isQRecoveryRelations() { return qRecoveryRelations; }
    public void setQRecoveryRelations(boolean v) { qRecoveryRelations = v; }
    public boolean isQLongtermDecision() { return qLongtermDecision; }
    public void setQLongtermDecision(boolean v) { qLongtermDecision = v; }

    public List<HealthRow> getHealthChecks() { return healthChecks; }
    public void setHealthChecks(List<HealthRow> v) { healthChecks = v == null ? new ArrayList<>() : v; }
}
