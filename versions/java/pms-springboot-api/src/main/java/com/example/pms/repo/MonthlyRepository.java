package com.example.pms.repo;

import com.example.pms.domain.MonthlyReview;
import com.example.pms.domain.SystemHealthCheck;
import com.example.pms.support.Views;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 每月重构清单数据访问：主表 + system_health_checks 明细，读时计算 avg_score。 */
@Repository
public class MonthlyRepository {

    private static final String SELECT = "SELECT * FROM monthly_reviews";

    private final JdbcTemplate jdbc;

    public MonthlyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<MonthlyReview> findAll(long uid) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY month DESC, id DESC", uid));
    }

    public Optional<MonthlyReview> findById(long id, long uid) {
        return jdbc.queryForList(SELECT + " WHERE id=? AND user_id=?", id, uid)
                .stream().findFirst().map(this::decorate);
    }

    public Optional<MonthlyReview> findByMonth(long uid, String month) {
        return jdbc.queryForList(SELECT + " WHERE user_id=? AND month=?", uid, month)
                .stream().findFirst().map(this::decorate);
    }

    public List<MonthlyReview> findRecent(long uid, int limit) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY month DESC, id DESC LIMIT ?", uid, limit));
    }

    public long count(long uid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM monthly_reviews WHERE user_id=?", Long.class, uid);
    }

    public boolean existsByMonth(long uid, String month, long excludeId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM monthly_reviews WHERE user_id=? AND month=? AND id<>?",
                Long.class, uid, month, excludeId);
        return n != null && n > 0;
    }

    @Transactional
    public long create(MonthlyReview m) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO monthly_reviews
                      (user_id, month, top_achievement, worth_continue, worth_reason,
                       most_valuable_input, biggest_opportunity_cost, keep_item, cut_item, experiment_item,
                       q_skill_portfolio, q_career_alignment, q_life_autonomy,
                       q_recovery_relations, q_longterm_decision)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            fill(params(m, false), ps);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        replaceHealthChecks(id, m.getHealthChecks());
        return id;
    }

    @Transactional
    public void update(MonthlyReview m) {
        jdbc.update("""
                UPDATE monthly_reviews SET
                  month=?, top_achievement=?, worth_continue=?, worth_reason=?,
                  most_valuable_input=?, biggest_opportunity_cost=?, keep_item=?, cut_item=?, experiment_item=?,
                  q_skill_portfolio=?, q_career_alignment=?, q_life_autonomy=?,
                  q_recovery_relations=?, q_longterm_decision=?,
                  updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND user_id=?
                """, withKeys(params(m, true), m.getId(), m.getUserId()));
        replaceHealthChecks(m.getId(), m.getHealthChecks());
    }

    @Transactional
    public void delete(long id, long uid) {
        jdbc.update("DELETE FROM monthly_reviews WHERE id=? AND user_id=?", id, uid);
    }

    @Transactional
    public void replaceHealthChecks(long reviewId, List<SystemHealthCheck> checks) {
        jdbc.update("DELETE FROM system_health_checks WHERE monthly_review_id=?", reviewId);
        if (checks == null) return;
        for (SystemHealthCheck c : checks) {
            if (c == null || !Views.in(c.getModule(), "direction", "taskload", "blockquality",
                    "timebox", "sleepenergy", "reviewquality")) continue;
            Integer score = c.getScore();
            if (score != null && (score < 1 || score > 5)) score = null;
            final Integer safeScore = score;
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement("""
                        INSERT INTO system_health_checks (monthly_review_id, module, score, needed_change)
                        VALUES (?,?,?,?)
                        """);
                ps.setLong(1, reviewId);
                ps.setString(2, c.getModule());
                if (safeScore == null) ps.setNull(3, Types.TINYINT); else ps.setInt(3, safeScore);
                ps.setString(4, c.getNeededChange());
                return ps;
            });
        }
    }

    private List<MonthlyReview> decorateAll(List<Map<String, Object>> rows) {
        return rows.stream().map(this::decorate).toList();
    }

    private MonthlyReview decorate(Map<String, Object> row) {
        MonthlyReview m = new MonthlyReview();
        m.setId(Views.number(row.get("id")));
        m.setUserId(Views.number(row.get("user_id")));
        m.setMonth(Views.str(row.get("month")));
        m.setTopAchievement(Views.str(row.get("top_achievement")));
        m.setWorthContinue(Views.str(row.get("worth_continue")));
        m.setWorthReason(Views.str(row.get("worth_reason")));
        m.setMostValuableInput(Views.str(row.get("most_valuable_input")));
        m.setBiggestOpportunityCost(Views.str(row.get("biggest_opportunity_cost")));
        m.setKeepItem(Views.str(row.get("keep_item")));
        m.setCutItem(Views.str(row.get("cut_item")));
        m.setExperimentItem(Views.str(row.get("experiment_item")));
        m.setQSkillPortfolio(Views.bool(row.get("q_skill_portfolio")));
        m.setQCareerAlignment(Views.bool(row.get("q_career_alignment")));
        m.setQLifeAutonomy(Views.bool(row.get("q_life_autonomy")));
        m.setQRecoveryRelations(Views.bool(row.get("q_recovery_relations")));
        m.setQLongtermDecision(Views.bool(row.get("q_longterm_decision")));
        m.setCreatedAt(Views.datetime(row.get("created_at")));
        m.setUpdatedAt(Views.datetime(row.get("updated_at")));

        List<SystemHealthCheck> checks = jdbc.queryForList(
                "SELECT * FROM system_health_checks WHERE monthly_review_id=? ORDER BY id", m.getId())
                .stream().map(r -> {
                    SystemHealthCheck c = new SystemHealthCheck();
                    c.setId(Views.number(r.get("id")));
                    c.setMonthlyReviewId(Views.number(r.get("monthly_review_id")));
                    c.setModule(Views.str(r.get("module")));
                    Object score = r.get("score");
                    c.setScore(score instanceof Number n ? n.intValue() : null);
                    c.setNeededChange(Views.str(r.get("needed_change")));
                    return c;
                }).toList();
        m.setHealthChecks(checks);
        m.setAvgScore(Views.avgScore(checks.stream().map(SystemHealthCheck::getScore).toList()));
        return m;
    }

    private Object[] params(MonthlyReview m, boolean includeKeys) {
        Object[] fields = new Object[]{
                m.getMonth(),
                m.getTopAchievement(), m.getWorthContinue(), m.getWorthReason(),
                m.getMostValuableInput(), m.getBiggestOpportunityCost(),
                m.getKeepItem(), m.getCutItem(), m.getExperimentItem(),
                b(m.isQSkillPortfolio()), b(m.isQCareerAlignment()), b(m.isQLifeAutonomy()),
                b(m.isQRecoveryRelations()), b(m.isQLongtermDecision())
        };
        if (includeKeys) return fields;
        Object[] all = new Object[fields.length + 1];
        all[0] = m.getUserId();
        System.arraycopy(fields, 0, all, 1, fields.length);
        return all;
    }

    private static Object[] withKeys(Object[] a, long id, long uid) {
        Object[] out = new Object[a.length + 2];
        System.arraycopy(a, 0, out, 0, a.length);
        out[a.length] = id;
        out[a.length + 1] = uid;
        return out;
    }

    private void fill(Object[] params, PreparedStatement ps) throws java.sql.SQLException {
        for (int i = 0; i < params.length; i++) {
            if (params[i] instanceof Boolean bool) {
                ps.setInt(i + 1, bool ? 1 : 0);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
    }

    private static int b(boolean value) { return value ? 1 : 0; }
}
