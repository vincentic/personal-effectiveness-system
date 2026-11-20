package com.example.pms.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 领域回顾，对应 weekly_domain_reviews；weekly.domains[] 元素。 */
public class WeeklyDomainReview {

    private long id;
    private long weeklyReviewId;
    private String domain = "work";
    private String facts = "";
    private String status = "smooth";

    @JsonProperty("id") public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    @JsonIgnore public long getWeeklyReviewId() { return weeklyReviewId; }
    public void setWeeklyReviewId(long v) { weeklyReviewId = v; }
    @JsonProperty("domain") public String getDomain() { return domain; }
    public void setDomain(String v) { domain = v == null ? "" : v; }
    @JsonProperty("facts") public String getFacts() { return facts; }
    public void setFacts(String v) { facts = v == null ? "" : v; }
    @JsonProperty("status") public String getStatus() { return status; }
    public void setStatus(String v) { status = v == null ? "" : v; }
}
