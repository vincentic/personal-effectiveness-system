package com.example.pms.repo;

import com.example.pms.domain.DailyPlan;
import com.example.pms.domain.TimeBlock;
import com.example.pms.support.Views;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** 每日执行清单数据访问：主表 + time_blocks 明细，读时计算统计字段。 */
@Repository
public class DailyRepository {

    private static final String SELECT = "SELECT * FROM daily_plans";

    private final JdbcTemplate jdbc;

    public DailyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ------------------------------- 查询

    public List<DailyPlan> findAll(long uid) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY plan_date DESC, id DESC", uid));
    }

    public Optional<DailyPlan> findById(long id, long uid) {
        List<Map<String, Object>> rows = jdbc.queryForList(SELECT + " WHERE id=? AND user_id=?", id, uid);
        return rows.stream().findFirst().map(this::decorate);
    }

    public Optional<DailyPlan> findByDate(long uid, String planDate) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                SELECT + " WHERE user_id=? AND plan_date=?", uid, toSqlDate(planDate));
        return rows.stream().findFirst().map(this::decorate);
    }

    public List<DailyPlan> findRecent(long uid, int limit) {
        return decorateAll(jdbc.queryForList(
                SELECT + " WHERE user_id=? ORDER BY plan_date DESC, id DESC LIMIT ?", uid, limit));
    }

    public long count(long uid) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM daily_plans WHERE user_id=?", Long.class, uid);
    }

    public boolean existsByDate(long uid, String planDate, long excludeId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM daily_plans WHERE user_id=? AND plan_date=? AND id<>?",
                Long.class, uid, toSqlDate(planDate), excludeId);
        return n != null && n > 0;
    }

    // ------------------------------- 写入

    @Transactional
    public long create(DailyPlan p) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    """
                    INSERT INTO daily_plans
                      (user_id, plan_date, sleep_hours, energy_level, mit,
                       pre_know_output, pre_materials_ready, pre_phone_handled,
                       during_one_block, during_shrink_step, during_record_distraction,
                       post_visible_output, post_recall, post_next_step,
                       life_water_food, life_walk_exercise, life_clean_tidy, life_social_outdoor,
                       review_accomplished, review_resistance, review_tomorrow_start, review_gratitude)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, Statement.RETURN_GENERATED_KEYS);
            fill(parentParams(p, false), ps);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        replaceBlocks(id, p.getBlocks());
        return id;
    }

    @Transactional
    public void update(DailyPlan p) {
        jdbc.update("""
                UPDATE daily_plans SET
                  plan_date=?, sleep_hours=?, energy_level=?, mit=?,
                  pre_know_output=?, pre_materials_ready=?, pre_phone_handled=?,
                  during_one_block=?, during_shrink_step=?, during_record_distraction=?,
                  post_visible_output=?, post_recall=?, post_next_step=?,
                  life_water_food=?, life_walk_exercise=?, life_clean_tidy=?, life_social_outdoor=?,
                  review_accomplished=?, review_resistance=?, review_tomorrow_start=?, review_gratitude=?,
                  updated_at=CURRENT_TIMESTAMP
                WHERE id=? AND user_id=?
                """, concat(parentParams(p, true), p.getId(), p.getUserId()));
        replaceBlocks(p.getId(), p.getBlocks());
    }

    @Transactional
    public void delete(long id, long uid) {
        jdbc.update("DELETE FROM daily_plans WHERE id=? AND user_id=?", id, uid);
    }

    /** 明细整体替换：同一次请求内先删旧行再插入新数组。 */
    @Transactional
    public void replaceBlocks(long planId, List<TimeBlock> blocks) {
        jdbc.update("DELETE FROM time_blocks WHERE daily_plan_id=?", planId);
        if (blocks == null) return;
        for (TimeBlock b : blocks) {
            if (b == null || b.getBlockName() == null || b.getBlockName().isBlank()) {
                continue; // 跳过表单空白行
            }
            insertBlock(planId, b);
        }
    }

    private void insertBlock(long planId, TimeBlock b) {
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO time_blocks
                      (daily_plan_id, seq, block_name, input_desc, key_action, deliverable,
                       time_start, time_end, is_done)
                    VALUES (?,?,?,?,?,?,?,?,?)
                    """);
            ps.setLong(1, planId);
            ps.setInt(2, b.getSeq());
            ps.setString(3, b.getBlockName().trim());
            ps.setString(4, b.getInputDesc());
            ps.setString(5, b.getKeyAction());
            ps.setString(6, b.getDeliverable());
            setTime(ps, 7, b.getTimeStart());
            setTime(ps, 8, b.getTimeEnd());
            ps.setInt(9, b.isDone() ? 1 : 0);
            return ps;
        });
    }

    // ------------------------------- 组装与读时计算

    private List<DailyPlan> decorateAll(List<Map<String, Object>> rows) {
        return rows.stream().map(this::decorate).toList();
    }

    private DailyPlan decorate(Map<String, Object> row) {
        DailyPlan p = new DailyPlan();
        p.setId(Views.number(row.get("id")));
        p.setUserId(Views.number(row.get("user_id")));
        p.setPlanDate(Views.date(row.get("plan_date")));
        p.setSleepHours(Views.decimal(row.get("sleep_hours")));
        p.setEnergyLevel(Views.str(row.get("energy_level")));
        p.setMit(Views.str(row.get("mit")));
        p.setPreKnowOutput(Views.bool(row.get("pre_know_output")));
        p.setPreMaterialsReady(Views.bool(row.get("pre_materials_ready")));
        p.setPrePhoneHandled(Views.bool(row.get("pre_phone_handled")));
        p.setDuringOneBlock(Views.bool(row.get("during_one_block")));
        p.setDuringShrinkStep(Views.bool(row.get("during_shrink_step")));
        p.setDuringRecordDistraction(Views.bool(row.get("during_record_distraction")));
        p.setPostVisibleOutput(Views.bool(row.get("post_visible_output")));
        p.setPostRecall(Views.bool(row.get("post_recall")));
        p.setPostNextStep(Views.bool(row.get("post_next_step")));
        p.setLifeWaterFood(Views.bool(row.get("life_water_food")));
        p.setLifeWalkExercise(Views.bool(row.get("life_walk_exercise")));
        p.setLifeCleanTidy(Views.bool(row.get("life_clean_tidy")));
        p.setLifeSocialOutdoor(Views.bool(row.get("life_social_outdoor")));
        p.setReviewAccomplished(Views.str(row.get("review_accomplished")));
        p.setReviewResistance(Views.str(row.get("review_resistance")));
        p.setReviewTomorrowStart(Views.str(row.get("review_tomorrow_start")));
        p.setReviewGratitude(Views.str(row.get("review_gratitude")));
        p.setCreatedAt(Views.datetime(row.get("created_at")));
        p.setUpdatedAt(Views.datetime(row.get("updated_at")));
        p.setBlocks(loadBlocks(p.getId()));

        int total = p.getBlocks().size();
        int done = (int) p.getBlocks().stream().filter(TimeBlock::isDone).count();
        p.setBlocksTotal(total);
        p.setBlocksDone(done);
        p.setBlockProgress(Views.blockProgress(done, total));
        p.setChecklistTotal(13);
        p.setChecklistDone(Views.checklistDone(
                p.isPreKnowOutput(), p.isPreMaterialsReady(), p.isPrePhoneHandled(),
                p.isDuringOneBlock(), p.isDuringShrinkStep(), p.isDuringRecordDistraction(),
                p.isPostVisibleOutput(), p.isPostRecall(), p.isPostNextStep(),
                p.isLifeWaterFood(), p.isLifeWalkExercise(), p.isLifeCleanTidy(), p.isLifeSocialOutdoor()));
        return p;
    }

    private List<TimeBlock> loadBlocks(long planId) {
        return jdbc.queryForList(
                "SELECT * FROM time_blocks WHERE daily_plan_id=? ORDER BY seq, id", planId)
                .stream().map(row -> {
                    TimeBlock b = new TimeBlock();
                    b.setId(Views.number(row.get("id")));
                    b.setDailyPlanId(Views.number(row.get("daily_plan_id")));
                    b.setSeq((int) Views.number(row.get("seq")));
                    b.setBlockName(Views.str(row.get("block_name")));
                    b.setInputDesc(Views.str(row.get("input_desc")));
                    b.setKeyAction(Views.str(row.get("key_action")));
                    b.setDeliverable(Views.str(row.get("deliverable")));
                    b.setTimeStart(Views.time(row.get("time_start")));
                    b.setTimeEnd(Views.time(row.get("time_end")));
                    b.setDone(Views.bool(row.get("is_done")));
                    b.setCreatedAt(Views.datetime(row.get("created_at")));
                    b.setUpdatedAt(Views.datetime(row.get("updated_at")));
                    return b;
                }).toList();
    }

    // ------------------------------- 参数辅助

    /** parentParams：前 21 个为字段值；includeKeys=false 需在最前面补 user_id。 */
    private Object[] parentParams(DailyPlan p, boolean includeKeys) {
        Object[] fields = new Object[]{
                toSqlDate(p.getPlanDate()),
                p.getSleepHours(),
                p.getEnergyLevel(),
                p.getMit(),
                b(p.isPreKnowOutput()), b(p.isPreMaterialsReady()), b(p.isPrePhoneHandled()),
                b(p.isDuringOneBlock()), b(p.isDuringShrinkStep()), b(p.isDuringRecordDistraction()),
                b(p.isPostVisibleOutput()), b(p.isPostRecall()), b(p.isPostNextStep()),
                b(p.isLifeWaterFood()), b(p.isLifeWalkExercise()), b(p.isLifeCleanTidy()), b(p.isLifeSocialOutdoor()),
                p.getReviewAccomplished(), p.getReviewResistance(), p.getReviewTomorrowStart(), p.getReviewGratitude()
        };
        if (includeKeys) return fields;
        Object[] all = new Object[fields.length + 1];
        all[0] = p.getUserId();
        System.arraycopy(fields, 0, all, 1, fields.length);
        return all;
    }

    private static Object[] concat(Object[] a, long id, long uid) {
        Object[] out = new Object[a.length + 2];
        System.arraycopy(a, 0, out, 0, a.length);
        out[a.length] = id;
        out[a.length + 1] = uid;
        return out;
    }

    private void fill(Object[] params, PreparedStatement ps) throws java.sql.SQLException {
        for (int i = 0; i < params.length; i++) {
            Object v = params[i];
            if (v instanceof Boolean bool) {
                ps.setInt(i + 1, bool ? 1 : 0);
            } else {
                ps.setObject(i + 1, v);
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

    private static void setTime(PreparedStatement ps, int index, String value) throws java.sql.SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.TIME);
        } else {
            ps.setString(index, value);
        }
    }
}
