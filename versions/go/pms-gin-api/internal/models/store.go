package models

import (
	"database/sql"
	"strings"

	"pms-gin-api/internal/database"
)

// 所有读写均按 user_id 过滤；明细子表随主表在同一事务内整体替换。
const dailyFields = `id, user_id, plan_date, sleep_hours, energy_level, mit,
 pre_know_output, pre_materials_ready, pre_phone_handled,
 during_one_block, during_shrink_step, during_record_distraction,
 post_visible_output, post_recall, post_next_step,
 life_water_food, life_walk_exercise, life_clean_tidy, life_social_outdoor,
 review_accomplished, review_resistance, review_tomorrow_start, review_gratitude,
 created_at`

const weeklyFields = `id, user_id, week_start, next_mit_1, next_mit_2, next_mit_3,
 deep_work_time, prep_env_materials, if_condition, then_action,
 q_skilled_block, q_low_value_time, q_estimate_gap, q_rest_connection, q_next_adjust,
 created_at`

const monthlyFields = `id, user_id, month, top_achievement, worth_continue, worth_reason,
 most_valuable_input, biggest_opportunity_cost, keep_item, cut_item, experiment_item,
 q_skill_portfolio, q_career_alignment, q_life_autonomy, q_recovery_relations,
 q_longterm_decision, created_at`

const userFields = "id, username, display_name, is_staff, created_at"

func placeholders(n int) string {
	if n <= 0 {
		return ""
	}
	return strings.TrimSuffix(strings.Repeat("?,", n), ",")
}

// ---------- 日清单 ----------

func blocksByPlan(planID int64) ([]TimeBlock, error) {
	rows, err := database.Rows(
		"SELECT id, daily_plan_id, seq, block_name, input_desc, key_action, deliverable, time_start, time_end, is_done "+
			"FROM time_blocks WHERE daily_plan_id = ? ORDER BY seq, id", planID)
	if err != nil {
		return nil, err
	}
	out := make([]TimeBlock, 0, len(rows))
	for _, r := range rows {
		out = append(out, blockFromRow(r))
	}
	return out, nil
}

// DailyPlans 返回当前用户的日清单（含时间盒明细），按日期倒序；limit<=0 表示不限。
func DailyPlans(uid int64, limit int) ([]DailyOut, error) {
	q := "SELECT " + dailyFields + " FROM daily_plans WHERE user_id = ? ORDER BY plan_date DESC, id DESC"
	args := []any{uid}
	if limit > 0 {
		q += " LIMIT ?"
		args = append(args, limit)
	}
	rows, err := database.Rows(q, args...)
	if err != nil {
		return nil, err
	}
	out := make([]DailyOut, 0, len(rows))
	for _, r := range rows {
		p := dailyFromRow(r)
		blocks, e := blocksByPlan(p.ID)
		if e != nil {
			return nil, e
		}
		out = append(out, DailyOutOf(p, blocks))
	}
	return out, nil
}

// DailyPlanByID 按 id + user_id 取详情，跨用户返回 (nil, nil)。
func DailyPlanByID(id, uid int64) (*DailyOut, error) {
	row, err := database.One("SELECT "+dailyFields+" FROM daily_plans WHERE id = ? AND user_id = ?", id, uid)
	if err != nil || row == nil {
		return nil, err
	}
	blocks, err := blocksByPlan(id)
	if err != nil {
		return nil, err
	}
	o := DailyOutOf(dailyFromRow(row), blocks)
	return &o, nil
}

// DailyPlanByDate 取指定日期的日清单（用于工作台「今日」）。
func DailyPlanByDate(uid int64, date string) (*DailyOut, error) {
	row, err := database.One("SELECT "+dailyFields+" FROM daily_plans WHERE user_id = ? AND plan_date = ?", uid, date)
	if err != nil || row == nil {
		return nil, err
	}
	blocks, err := blocksByPlan(ToInt64(row["id"]))
	if err != nil {
		return nil, err
	}
	o := DailyOutOf(dailyFromRow(row), blocks)
	return &o, nil
}

// dailySetArgs 生成除 user_id 外、与 dailySet 列顺序一致的参数（21 个）。
func dailySetArgs(in *DailyIn) []any {
	var sleep any
	if in.SleepHours != nil {
		sleep = *in.SleepHours
	}
	args := []any{in.PlanDate, sleep, in.EnergyLevel, in.MIT,
		b2i(in.PreKnowOutput), b2i(in.PreMaterialsReady), b2i(in.PrePhoneHandled),
		b2i(in.DuringOneBlock), b2i(in.DuringShrinkStep), b2i(in.DuringRecordDistraction),
		b2i(in.PostVisibleOutput), b2i(in.PostRecall), b2i(in.PostNextStep),
		b2i(in.LifeWaterFood), b2i(in.LifeWalkExercise), b2i(in.LifeCleanTidy), b2i(in.LifeSocialOutdoor),
		in.ReviewAccomplished, in.ReviewResistance, in.ReviewTomorrowStart, in.ReviewGratitude}
	return args
}

const dailySet = `plan_date = ?, sleep_hours = ?, energy_level = ?, mit = ?,
 pre_know_output = ?, pre_materials_ready = ?, pre_phone_handled = ?,
 during_one_block = ?, during_shrink_step = ?, during_record_distraction = ?,
 post_visible_output = ?, post_recall = ?, post_next_step = ?,
 life_water_food = ?, life_walk_exercise = ?, life_clean_tidy = ?, life_social_outdoor = ?,
 review_accomplished = ?, review_resistance = ?, review_tomorrow_start = ?, review_gratitude = ?`

const dailyInsertCols = `user_id, plan_date, sleep_hours, energy_level, mit,
 pre_know_output, pre_materials_ready, pre_phone_handled,
 during_one_block, during_shrink_step, during_record_distraction,
 post_visible_output, post_recall, post_next_step,
 life_water_food, life_walk_exercise, life_clean_tidy, life_social_outdoor,
 review_accomplished, review_resistance, review_tomorrow_start, review_gratitude`

// replaceBlocks 先删后插，整体替换时间盒明细。
func replaceBlocks(tx *sql.Tx, planID int64, blocks []TimeBlockIn) error {
	if _, err := tx.Exec("DELETE FROM time_blocks WHERE daily_plan_id = ?", planID); err != nil {
		return err
	}
	for i, b := range blocks {
		if strings.TrimSpace(b.BlockName) == "" {
			continue // 跳过表单中的空行
		}
		seq := b.Seq
		if seq == 0 {
			seq = i + 1
		}
		var start, end any
		if b.TimeStart != nil {
			start = *b.TimeStart
		}
		if b.TimeEnd != nil {
			end = *b.TimeEnd
		}
		if _, err := tx.Exec(
			`INSERT INTO time_blocks (daily_plan_id, seq, block_name, input_desc, key_action, deliverable, time_start, time_end, is_done)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			planID, seq, strings.TrimSpace(b.BlockName), b.InputDesc, b.KeyAction, b.Deliverable,
			start, end, b2i(b.IsDone)); err != nil {
			return err
		}
	}
	return nil
}

// InsertDaily 在事务内创建主表 + 时间盒明细。
func InsertDaily(uid int64, in *DailyIn) (int64, error) {
	tx, err := database.DB.Begin()
	if err != nil {
		return 0, err
	}
	defer func() { _ = tx.Rollback() }()
	args := append([]any{uid}, dailySetArgs(in)...)
	res, err := tx.Exec("INSERT INTO daily_plans ("+dailyInsertCols+") VALUES ("+placeholders(len(args))+")", args...)
	if err != nil {
		return 0, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, err
	}
	if err = replaceBlocks(tx, id, in.Blocks); err != nil {
		return 0, err
	}
	if err = tx.Commit(); err != nil {
		return 0, err
	}
	return id, nil
}

// UpdateDaily 在事务内更新主表 + 整体替换时间盒明细。
func UpdateDaily(id, uid int64, in *DailyIn) error {
	tx, err := database.DB.Begin()
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()
	args := append(dailySetArgs(in), id, uid)
	_, err = tx.Exec("UPDATE daily_plans SET "+dailySet+" WHERE id = ? AND user_id = ?", args...)
	if err != nil {
		return err
	}
	if err = replaceBlocks(tx, id, in.Blocks); err != nil {
		return err
	}
	return tx.Commit()
}

func DeleteDaily(id, uid int64) error {
	_, err := database.Exec("DELETE FROM daily_plans WHERE id = ? AND user_id = ?", id, uid)
	return err
}

// ---------- 周清单 ----------

func domainsByReview(reviewID int64) ([]WeeklyDomainReview, error) {
	rows, err := database.Rows(
		"SELECT id, weekly_review_id, domain, facts, status FROM weekly_domain_reviews WHERE weekly_review_id = ? ORDER BY id", reviewID)
	if err != nil {
		return nil, err
	}
	out := make([]WeeklyDomainReview, 0, len(rows))
	for _, r := range rows {
		out = append(out, domainFromRow(r))
	}
	return out, nil
}

func flowsByReview(reviewID int64) ([]WeeklyTaskFlow, error) {
	rows, err := database.Rows(
		"SELECT id, weekly_review_id, bucket, content, seq FROM weekly_task_flows WHERE weekly_review_id = ? ORDER BY bucket, seq, id", reviewID)
	if err != nil {
		return nil, err
	}
	out := make([]WeeklyTaskFlow, 0, len(rows))
	for _, r := range rows {
		out = append(out, flowFromRow(r))
	}
	return out, nil
}

func weeklyWithChildren(w WeeklyReview) (WeeklyOut, error) {
	domains, err := domainsByReview(w.ID)
	if err != nil {
		return WeeklyOut{}, err
	}
	flows, err := flowsByReview(w.ID)
	if err != nil {
		return WeeklyOut{}, err
	}
	return WeeklyOutOf(w, domains, flows), nil
}

// WeeklyReviews 返回当前用户的周复盘（含 domains/flows），按周倒序。
func WeeklyReviews(uid int64, limit int) ([]WeeklyOut, error) {
	q := "SELECT " + weeklyFields + " FROM weekly_reviews WHERE user_id = ? ORDER BY week_start DESC, id DESC"
	args := []any{uid}
	if limit > 0 {
		q += " LIMIT ?"
		args = append(args, limit)
	}
	rows, err := database.Rows(q, args...)
	if err != nil {
		return nil, err
	}
	out := make([]WeeklyOut, 0, len(rows))
	for _, r := range rows {
		o, e := weeklyWithChildren(weeklyFromRow(r))
		if e != nil {
			return nil, e
		}
		out = append(out, o)
	}
	return out, nil
}

// WeeklyReviewByID 按 id + user_id 取详情，跨用户返回 (nil, nil)。
func WeeklyReviewByID(id, uid int64) (*WeeklyOut, error) {
	row, err := database.One("SELECT "+weeklyFields+" FROM weekly_reviews WHERE id = ? AND user_id = ?", id, uid)
	if err != nil || row == nil {
		return nil, err
	}
	o, err := weeklyWithChildren(weeklyFromRow(row))
	if err != nil {
		return nil, err
	}
	return &o, nil
}

// WeeklyReviewByWeek 取指定周起始日的周复盘（用于工作台「本周」）。
func WeeklyReviewByWeek(uid int64, weekStart string) (*WeeklyOut, error) {
	row, err := database.One("SELECT "+weeklyFields+" FROM weekly_reviews WHERE user_id = ? AND week_start = ?", uid, weekStart)
	if err != nil || row == nil {
		return nil, err
	}
	o, err := weeklyWithChildren(weeklyFromRow(row))
	if err != nil {
		return nil, err
	}
	return &o, nil
}

func weeklySetArgs(in *WeeklyIn) []any {
	return []any{in.WeekStart, in.NextMIT1, in.NextMIT2, in.NextMIT3, in.DeepWorkTime,
		in.PrepEnvMaterials, in.IfCondition, in.ThenAction,
		b2i(in.QSkilledBlock), b2i(in.QLowValueTime), b2i(in.QEstimateGap),
		b2i(in.QRestConnection), b2i(in.QNextAdjust)}
}

const weeklySet = `week_start = ?, next_mit_1 = ?, next_mit_2 = ?, next_mit_3 = ?, deep_work_time = ?,
 prep_env_materials = ?, if_condition = ?, then_action = ?,
 q_skilled_block = ?, q_low_value_time = ?, q_estimate_gap = ?, q_rest_connection = ?, q_next_adjust = ?`

const weeklyInsertCols = `user_id, week_start, next_mit_1, next_mit_2, next_mit_3, deep_work_time,
 prep_env_materials, if_condition, then_action,
 q_skilled_block, q_low_value_time, q_estimate_gap, q_rest_connection, q_next_adjust`

func replaceWeeklyChildren(tx *sql.Tx, reviewID int64, in *WeeklyIn) error {
	if _, err := tx.Exec("DELETE FROM weekly_domain_reviews WHERE weekly_review_id = ?", reviewID); err != nil {
		return err
	}
	for _, d := range in.Domains {
		if _, err := tx.Exec(
			"INSERT INTO weekly_domain_reviews (weekly_review_id, domain, facts, status) VALUES (?, ?, ?, ?)",
			reviewID, d.Domain, d.Facts, d.Status); err != nil {
			return err
		}
	}
	if _, err := tx.Exec("DELETE FROM weekly_task_flows WHERE weekly_review_id = ?", reviewID); err != nil {
		return err
	}
	for i, f := range in.Flows {
		if strings.TrimSpace(f.Content) == "" {
			continue
		}
		seq := f.Seq
		if seq == 0 {
			seq = i + 1
		}
		if _, err := tx.Exec(
			"INSERT INTO weekly_task_flows (weekly_review_id, bucket, content, seq) VALUES (?, ?, ?, ?)",
			reviewID, f.Bucket, strings.TrimSpace(f.Content), seq); err != nil {
			return err
		}
	}
	return nil
}

func InsertWeekly(uid int64, in *WeeklyIn) (int64, error) {
	tx, err := database.DB.Begin()
	if err != nil {
		return 0, err
	}
	defer func() { _ = tx.Rollback() }()
	args := append([]any{uid}, weeklySetArgs(in)...)
	res, err := tx.Exec("INSERT INTO weekly_reviews ("+weeklyInsertCols+") VALUES ("+placeholders(len(args))+")", args...)
	if err != nil {
		return 0, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, err
	}
	if err = replaceWeeklyChildren(tx, id, in); err != nil {
		return 0, err
	}
	if err = tx.Commit(); err != nil {
		return 0, err
	}
	return id, nil
}

func UpdateWeekly(id, uid int64, in *WeeklyIn) error {
	tx, err := database.DB.Begin()
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()
	args := append(weeklySetArgs(in), id, uid)
	_, err = tx.Exec("UPDATE weekly_reviews SET "+weeklySet+" WHERE id = ? AND user_id = ?", args...)
	if err != nil {
		return err
	}
	if err = replaceWeeklyChildren(tx, id, in); err != nil {
		return err
	}
	return tx.Commit()
}

func DeleteWeekly(id, uid int64) error {
	_, err := database.Exec("DELETE FROM weekly_reviews WHERE id = ? AND user_id = ?", id, uid)
	return err
}

// ---------- 月清单 ----------

func healthByReview(reviewID int64) ([]SystemHealthCheck, error) {
	rows, err := database.Rows(
		"SELECT id, monthly_review_id, module, score, needed_change FROM system_health_checks WHERE monthly_review_id = ? ORDER BY id", reviewID)
	if err != nil {
		return nil, err
	}
	out := make([]SystemHealthCheck, 0, len(rows))
	for _, r := range rows {
		out = append(out, healthFromRow(r))
	}
	return out, nil
}

func monthlyWithChildren(m MonthlyReview) (MonthlyOut, error) {
	checks, err := healthByReview(m.ID)
	if err != nil {
		return MonthlyOut{}, err
	}
	return MonthlyOutOf(m, checks), nil
}

// MonthlyReviews 返回当前用户的月重构（含 health_checks），按月份倒序。
func MonthlyReviews(uid int64, limit int) ([]MonthlyOut, error) {
	q := "SELECT " + monthlyFields + " FROM monthly_reviews WHERE user_id = ? ORDER BY month DESC, id DESC"
	args := []any{uid}
	if limit > 0 {
		q += " LIMIT ?"
		args = append(args, limit)
	}
	rows, err := database.Rows(q, args...)
	if err != nil {
		return nil, err
	}
	out := make([]MonthlyOut, 0, len(rows))
	for _, r := range rows {
		o, e := monthlyWithChildren(monthlyFromRow(r))
		if e != nil {
			return nil, e
		}
		out = append(out, o)
	}
	return out, nil
}

// MonthlyReviewByID 按 id + user_id 取详情，跨用户返回 (nil, nil)。
func MonthlyReviewByID(id, uid int64) (*MonthlyOut, error) {
	row, err := database.One("SELECT "+monthlyFields+" FROM monthly_reviews WHERE id = ? AND user_id = ?", id, uid)
	if err != nil || row == nil {
		return nil, err
	}
	o, err := monthlyWithChildren(monthlyFromRow(row))
	if err != nil {
		return nil, err
	}
	return &o, nil
}

// MonthlyReviewByMonth 取指定月份的月重构（用于工作台「本月」）。
func MonthlyReviewByMonth(uid int64, month string) (*MonthlyOut, error) {
	row, err := database.One("SELECT "+monthlyFields+" FROM monthly_reviews WHERE user_id = ? AND month = ?", uid, month)
	if err != nil || row == nil {
		return nil, err
	}
	o, err := monthlyWithChildren(monthlyFromRow(row))
	if err != nil {
		return nil, err
	}
	return &o, nil
}

func monthlySetArgs(in *MonthlyIn) []any {
	return []any{in.Month, in.TopAchievement, in.WorthContinue, in.WorthReason,
		in.MostValuableInput, in.BiggestOpportunityCost, in.KeepItem, in.CutItem, in.ExperimentItem,
		b2i(in.QSkillPortfolio), b2i(in.QCareerAlignment), b2i(in.QLifeAutonomy),
		b2i(in.QRecoveryRelations), b2i(in.QLongtermDecision)}
}

const monthlySet = `month = ?, top_achievement = ?, worth_continue = ?, worth_reason = ?,
 most_valuable_input = ?, biggest_opportunity_cost = ?, keep_item = ?, cut_item = ?, experiment_item = ?,
 q_skill_portfolio = ?, q_career_alignment = ?, q_life_autonomy = ?, q_recovery_relations = ?, q_longterm_decision = ?`

const monthlyInsertCols = `user_id, month, top_achievement, worth_continue, worth_reason,
 most_valuable_input, biggest_opportunity_cost, keep_item, cut_item, experiment_item,
 q_skill_portfolio, q_career_alignment, q_life_autonomy, q_recovery_relations, q_longterm_decision`

func replaceHealthChecks(tx *sql.Tx, reviewID int64, in *MonthlyIn) error {
	if _, err := tx.Exec("DELETE FROM system_health_checks WHERE monthly_review_id = ?", reviewID); err != nil {
		return err
	}
	for _, h := range in.HealthChecks {
		var score any
		if h.Score != nil {
			score = *h.Score
		}
		if _, err := tx.Exec(
			"INSERT INTO system_health_checks (monthly_review_id, module, score, needed_change) VALUES (?, ?, ?, ?)",
			reviewID, h.Module, score, h.NeededChange); err != nil {
			return err
		}
	}
	return nil
}

func InsertMonthly(uid int64, in *MonthlyIn) (int64, error) {
	tx, err := database.DB.Begin()
	if err != nil {
		return 0, err
	}
	defer func() { _ = tx.Rollback() }()
	args := append([]any{uid}, monthlySetArgs(in)...)
	res, err := tx.Exec("INSERT INTO monthly_reviews ("+monthlyInsertCols+") VALUES ("+placeholders(len(args))+")", args...)
	if err != nil {
		return 0, err
	}
	id, err := res.LastInsertId()
	if err != nil {
		return 0, err
	}
	if err = replaceHealthChecks(tx, id, in); err != nil {
		return 0, err
	}
	if err = tx.Commit(); err != nil {
		return 0, err
	}
	return id, nil
}

func UpdateMonthly(id, uid int64, in *MonthlyIn) error {
	tx, err := database.DB.Begin()
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback() }()
	args := append(monthlySetArgs(in), id, uid)
	_, err = tx.Exec("UPDATE monthly_reviews SET "+monthlySet+" WHERE id = ? AND user_id = ?", args...)
	if err != nil {
		return err
	}
	if err = replaceHealthChecks(tx, id, in); err != nil {
		return err
	}
	return tx.Commit()
}

func DeleteMonthly(id, uid int64) error {
	_, err := database.Exec("DELETE FROM monthly_reviews WHERE id = ? AND user_id = ?", id, uid)
	return err
}

// ---------- 用户 ----------

func Users() ([]map[string]any, error) {
	return database.Rows("SELECT " + userFields + " FROM users ORDER BY id")
}

func UserByID(id int64) (map[string]any, error) {
	return database.One("SELECT "+userFields+" FROM users WHERE id = ?", id)
}

// UserByName 返回含 password_hash 的完整行，仅用于登录校验。
func UserByName(username string) (map[string]any, error) {
	return database.One("SELECT * FROM users WHERE username = ?", username)
}

func CountUsers() (int64, error) {
	row, err := database.One("SELECT COUNT(*) AS n FROM users")
	if err != nil || row == nil {
		return 0, err
	}
	return ToInt64(row["n"]), nil
}

func InsertUser(username, passwordHash, displayName string, staff bool) (int64, error) {
	return database.InsertID(
		"INSERT INTO users (username, password_hash, display_name, is_staff) VALUES (?, ?, ?, ?)",
		username, passwordHash, displayName, b2i(staff))
}

// ---------- 工作台 ----------

// DashboardCounts 返回当前用户的三类清单总数。
func DashboardCounts(uid int64) (daily, weekly, monthly int64, err error) {
	row, err := database.One(`SELECT
        (SELECT COUNT(*) FROM daily_plans WHERE user_id = ?) AS daily_count,
        (SELECT COUNT(*) FROM weekly_reviews WHERE user_id = ?) AS weekly_count,
        (SELECT COUNT(*) FROM monthly_reviews WHERE user_id = ?) AS monthly_count`, uid, uid, uid)
	if err != nil || row == nil {
		return 0, 0, 0, err
	}
	return ToInt64(row["daily_count"]), ToInt64(row["weekly_count"]), ToInt64(row["monthly_count"]), nil
}

// BlockTotals 统计最近 7 日（含今日）时间盒总数与完成数。
func BlockTotals(uid int64) (total, done int64, err error) {
	row, err := database.One(`SELECT COUNT(*) AS total, COALESCE(SUM(tb.is_done), 0) AS done
        FROM time_blocks tb JOIN daily_plans d ON d.id = tb.daily_plan_id
        WHERE d.user_id = ? AND d.plan_date >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)`, uid)
	if err != nil || row == nil {
		return 0, 0, err
	}
	return ToInt64(row["total"]), ToInt64(row["done"]), nil
}

// CurrentWeekStart / CurrentMonth 由数据库时钟计算，避免应用与 DB 时区不一致。
func CurrentWeekStart() string {
	row, err := database.One("SELECT DATE_SUB(CURDATE(), INTERVAL WEEKDAY(CURDATE()) DAY) AS d")
	if err != nil || row == nil {
		return ""
	}
	return DateOnly(row["d"])
}

func CurrentMonth() string {
	row, err := database.One("SELECT DATE_FORMAT(CURDATE(), '%Y-%m') AS m")
	if err != nil || row == nil {
		return ""
	}
	return str(row["m"])
}

func CurrentDate() string {
	row, err := database.One("SELECT CURDATE() AS d")
	if err != nil || row == nil {
		return ""
	}
	return DateOnly(row["d"])
}
