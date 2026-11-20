package com.example.pms.repo;

import com.example.pms.domain.WeeklyDomainReview;
import com.example.pms.domain.WeeklyReview;
import com.example.pms.domain.WeeklyTaskFlow;
import com.example.pms.support.Views;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 每周复盘清单数据访问：主表 + domains/flows 明细，读时计算 week_end。 */
@Repository
public class WeeklyRepository {

    private static final String SELECT = "SELECT * FROM weekly_reviews";

    private final JdbcTemplate jdbc;

    public WeeklyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<WeeklyReview> findAll(long uid) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY week_start DESC, id DESC", uid));
    }

    public Optional<WeeklyReview> findById(long id, long uid) {
        return jdbc.queryForList(SELECT + " WHERE id=? AND user_id=?", id, uid)
                .stream().findFirst().map(this::decorate);
    }

    public Optional<WeeklyReview> findByWeek(long uid, String weekStart) {
        return jdbc.queryForList(SELECT + " WHERE user_id=? AND week_start=?", uid, toSqlDate(weekStart))
                .stream().findFirst().map(this::decorate);
    }

    public List<WeeklyReview> findRecent(long uid, int limit) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY week_start DESC, id DESC LIMIT ?", uid, limit));
    }

    public long count(long uid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM weekly_reviews WHERE user_id=?", Long.class, uid);
    }

    public boolean existsByWeek(long uid, String weekStart, long excludeId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM weekly_reviews WHERE user_id=? AND week_start=? AND id<>?",
                Long.class, uid, toSqlDate(weekStart), excludeId);
        return n != null && n > 0;
    }

    @Transactional
    public long create(WeeklyReview w) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO weekly_reviews
                      (user_id, week_start, next_mit_1, next_mit_2, next_mit_3,
                       deep_work_time, prep_env_materials, if_condition, then_action,
                       q_skilled_block, q_low_value_time, q_estimate_gap, q_rest_connection, q_next_adjust)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            Object[] p = params(w, false);
            fill(p, ps);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        replaceChildren(id, w.getDomains(), w.getFlows());
        return id;
    }

    @Transactional
    public void update(WeeklyReview w) {
        jdbc.update("""
                UPDATE weekly_reviews SET
                  week_start=?, next_mit_1=?, next_mit_2=?, next_mit_3=?,
                  deep_work_time=?, prep_env_materials=?, if_condition=?, then_action=?,
                  q_skilled_block=?, q_low_value_time=?, q_estimate_gap=?, q_rest_connection=?, q_next_adjust=?,
                  updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND user_id=?
                """, withKeys(params(w, true), w.getId(), w.getUserId()));
        replaceChildren(w.getId(), w.getDomains(), w.getFlows());
    }

    @Transactional
    public void delete(long id, long uid) {
        jdbc.update("DELETE FROM weekly_reviews WHERE id=? AND user_id=?", id, uid);
    }

    /** domains / flows 随主表整体替换，保持原子性。 */
    @Transactional
    public void replaceChildren(long reviewId, List<WeeklyDomainReview> domains, List<WeeklyTaskFlow> flows) {
        jdbc.update("DELETE FROM weekly_domain_reviews WHERE weekly_review_id=?", reviewId);
        jdbc.update("DELETE FROM weekly_task_flows WHERE weekly_review_id=?", reviewId);
        if (domains != null) {
            for (WeeklyDomainReview d : domains) {
                if (d == null || !Views.in(d.getDomain(), "work", "study", "body", "relations")) continue;
                jdbc.update("""
                        INSERT INTO weekly_domain_reviews (weekly_review_id, domain, facts, status)
                        VALUES (?,?,?,?)
                        """, reviewId, d.getDomain(), d.getFacts(),
                        Views.in(d.getStatus(), "smooth", "blocked", "paused") ? d.getStatus() : "smooth");
            }
        }
        if (flows != null) {
            for (WeeklyTaskFlow f : flows) {
                if (f == null) continue;
                String bucket = Views.in(f.getBucket(), "todo", "doing", "done", "remove") ? f.getBucket() : "todo";
                if (f.getContent() == null || f.getContent().isBlank()) continue;
                jdbc.update("""
                        INSERT INTO weekly_task_flows (weekly_review_id, bucket, content, seq)
                        VALUES (?,?,?,?)
                        """, reviewId, bucket, f.getContent(), f.getSeq());
            }
        }
    }

    private List<WeeklyReview> decorateAll(List<Map<String, Object>> rows) {
        return rows.stream().map(this::decorate).toList();
    }

    private WeeklyReview decorate(Map<String, Object> row) {
        WeeklyReview w = new WeeklyReview();
        w.setId(Views.number(row.get("id")));
        w.setUserId(Views.number(row.get("user_id")));
        w.setWeekStart(Views.date(row.get("week_start")));
        w.setNextMit1(Views.str(row.get("next_mit_1")));
        w.setNextMit2(Views.str(row.get("next_mit_2")));
        w.setNextMit3(Views.str(row.get("next_mit_3")));
        w.setDeepWorkTime(Views.str(row.get("deep_work_time")));
        w.setPrepEnvMaterials(Views.str(row.get("prep_env_materials")));
        w.setIfCondition(Views.str(row.get("if_condition")));
        w.setThenAction(Views.str(row.get("then_action")));
        w.setQSkilledBlock(Views.bool(row.get("q_skilled_block")));
        w.setQLowValueTime(Views.bool(row.get("q_low_value_time")));
        w.setQEstimateGap(Views.bool(row.get("q_estimate_gap")));
        w.setQRestConnection(Views.bool(row.get("q_rest_connection")));
        w.setQNextAdjust(Views.bool(row.get("q_next_adjust")));
        w.setCreatedAt(Views.datetime(row.get("created_at")));
        w.setUpdatedAt(Views.datetime(row.get("updated_at")));
        w.setWeekEnd(Views.weekEnd(w.getWeekStart()));

        w.setDomains(jdbc.queryForList(
                "SELECT * FROM weekly_domain_reviews WHERE weekly_review_id=? ORDER BY id", w.getId())
                .stream().map(r -> {
                    WeeklyDomainReview d = new WeeklyDomainReview();
                    d.setId(Views.number(r.get("id")));
                    d.setWeeklyReviewId(Views.number(r.get("weekly_review_id")));
                    d.setDomain(Views.str(r.get("domain")));
                    d.setFacts(Views.str(r.get("facts")));
                    d.setStatus(Views.str(r.get("status")));
                    return d;
                }).toList());
        w.setFlows(jdbc.queryForList(
                "SELECT * FROM weekly_task_flows WHERE weekly_review_id=? ORDER BY bucket, seq, id", w.getId())
                .stream().map(r -> {
                    WeeklyTaskFlow f = new WeeklyTaskFlow();
                    f.setId(Views.number(r.get("id")));
                    f.setWeeklyReviewId(Views.number(r.get("weekly_review_id")));
                    f.setBucket(Views.str(r.get("bucket")));
                    f.setContent(Views.str(r.get("content")));
                    f.setSeq((int) Views.number(r.get("seq")));
                    return f;
                }).toList());
        return w;
    }

    /** includeKeys=false 时首参为 user_id；true 时不包含 user_id（UPDATE 用 WHERE 传）。 */
    private Object[] params(WeeklyReview w, boolean includeKeys) {
        Object[] fields = new Object[]{
                toSqlDate(w.getWeekStart()),
                w.getNextMit1(), w.getNextMit2(), w.getNextMit3(),
                w.getDeepWorkTime(), w.getPrepEnvMaterials(), w.getIfCondition(), w.getThenAction(),
                b(w.isQSkilledBlock()), b(w.isQLowValueTime()), b(w.isQEstimateGap()),
                b(w.isQRestConnection()), b(w.isQNextAdjust())
        };
        if (includeKeys) return fields;
        Object[] all = new Object[fields.length + 1];
        all[0] = w.getUserId();
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

    private static Date toSqlDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Date.valueOf(java.time.LocalDate.parse(value.trim()));
        } catch (Exception e) {
            return null;
        }
    }
}
