// Package models 提供 v2「个人效能系统」的领域模型、请求体、输出视图与字段转换工具。
package models

import (
	"fmt"
	"html"
	"math"
	"strconv"
	"strings"
	"time"
)

// ---------- 通用清洗与转换 ----------

func str(v any) string {
	if v == nil {
		return ""
	}
	return fmt.Sprint(v)
}

// ToInt64 兼容 database/sql 返回的 int64 / float64 / []byte / string 等。
func ToInt64(v any) int64 {
	switch x := v.(type) {
	case nil:
		return 0
	case int64:
		return x
	case int:
		return int64(x)
	case int32:
		return int64(x)
	case float64:
		return int64(x)
	case float32:
		return int64(x)
	case bool:
		if x {
			return 1
		}
		return 0
	case string:
		n, _ := strconv.ParseInt(strings.TrimSpace(x), 10, 64)
		return n
	case []byte:
		n, _ := strconv.ParseInt(strings.TrimSpace(string(x)), 10, 64)
		return n
	default:
		return 0
	}
}

// DateOnly 把 DATE / DATETIME / time.Time / 字符串统一为 YYYY-MM-DD。
func DateOnly(v any) string {
	if v == nil {
		return ""
	}
	switch x := v.(type) {
	case time.Time:
		return x.Format("2006-01-02")
	case string:
		if len(x) >= 10 {
			return x[:10]
		}
		return x
	case []byte:
		s := string(x)
		if len(s) >= 10 {
			return s[:10]
		}
		return s
	default:
		return str(v)
	}
}

// timeHM 把 TIME 列归一化为 "HH:MM"；NULL / 空值返回 nil。
func timeHM(v any) *string {
	var s string
	switch x := v.(type) {
	case nil:
		return nil
	case time.Duration:
		s = fmt.Sprintf("%02d:%02d", int(x.Hours()), int(x.Minutes())%60)
	case time.Time:
		s = x.Format("15:04")
	case string:
		s = x
	case []byte:
		s = string(x)
	default:
		s = str(v)
	}
	s = strings.TrimSpace(s)
	if s == "" {
		return nil
	}
	if len(s) > 5 {
		s = s[:5]
	}
	return &s
}

// toBool 把 TINYINT(0/1) 转为 bool。
func toBool(v any) bool { return ToInt64(v) != 0 }

// b2i 把 bool 转为 TINYINT(0/1)。
func b2i(b bool) int {
	if b {
		return 1
	}
	return 0
}

func toInt(v any) int { return int(ToInt64(v)) }

func toFloatPtr(v any) *float64 {
	if v == nil {
		return nil
	}
	var f float64
	switch x := v.(type) {
	case float64:
		f = x
	case float32:
		f = float64(x)
	default:
		var err error
		f, err = strconv.ParseFloat(strings.TrimSpace(str(v)), 64)
		if err != nil {
			return nil
		}
	}
	return &f
}

func toIntPtr(v any) *int {
	if v == nil {
		return nil
	}
	n := toInt(v)
	return &n
}

// nullableStr 把空指针 / 空串转成 SQL NULL。
func nullableStr(p *string) any {
	if p == nil || strings.TrimSpace(*p) == "" {
		return nil
	}
	return strings.TrimSpace(*p)
}

// Nl2br 先 html.EscapeString，再把换行替换为 <br>，供 safe() 安全输出。
func Nl2br(v any) string {
	if v == nil {
		return ""
	}
	s := html.EscapeString(str(v))
	s = strings.ReplaceAll(s, "\r\n", "\n")
	s = strings.ReplaceAll(s, "\n", "<br>")
	return s
}

// Truncate 按 rune 截断并追加省略号（对齐中文字符）。
func Truncate(s string, n int) string {
	r := []rune(s)
	if len(r) <= n {
		return s
	}
	return string(r[:n]) + "…"
}

// Progress 读时计算完成百分比：done / total * 100，四舍五入。
func Progress(done, total int) int {
	if total == 0 {
		return 0
	}
	return int(math.Round(float64(done) * 100.0 / float64(total)))
}

// ParseDate 校验 YYYY-MM-DD；空串返回 nil（SQL NULL）。
func ParseDate(s string) (any, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	t, err := time.Parse("2006-01-02", s)
	if err != nil {
		return nil, fmt.Errorf("日期格式应为 YYYY-MM-DD")
	}
	return t.Format("2006-01-02"), nil
}

// ParseMonth 校验 YYYY-MM。
func ParseMonth(s string) (string, error) {
	s = strings.TrimSpace(s)
	t, err := time.Parse("2006-01", s)
	if err != nil {
		return "", fmt.Errorf("指标格式应为 YYYY-MM")
	}
	return t.Format("2006-01"), nil
}

// ParseHM 校验 HH:MM；空串返回 nil。
func ParseHM(s string) (any, error) {
	s = strings.TrimSpace(s)
	if s == "" {
		return nil, nil
	}
	if _, err := time.Parse("15:04", s); err != nil {
		return nil, fmt.Errorf("时间格式应为 HH:MM")
	}
	return s, nil
}

// ---------- 枚举白名单与选项 ----------

type Option struct{ Code, Label string }

var EnergyOptions = []Option{
	{"low", "低"}, {"medium", "中"}, {"high", "高"},
}
var DomainOptions = []Option{
	{"work", "工作 / 项目"}, {"study", "学习 / 技能"},
	{"body", "身体 / 精力"}, {"relations", "关系 / 生活"},
}
var DomainStatusOptions = []Option{
	{"smooth", "顺利"}, {"blocked", "受阻"}, {"paused", "暂停"},
}
var BucketOptions = []Option{
	{"todo", "待办"}, {"doing", "进行中"},
	{"done", "已完成"}, {"remove", "删除/委托/延后"},
}
var WorthOptions = []Option{
	{"yes", "是"}, {"adjust", "调整"}, {"stop", "停止"},
}
var HealthModuleOptions = []Option{
	{"direction", "方向清晰度"}, {"taskload", "任务负荷 / 在制任务"},
	{"blockquality", "组块质量 / 可独立调用"}, {"timebox", "时间盒现实程度"},
	{"sleepenergy", "睡眠 / 体能 / 情绪"}, {"reviewquality", "复盘与反馈质量"},
}

func inCodes(code string, opts []Option) bool {
	for _, o := range opts {
		if code == o.Code {
			return true
		}
	}
	return false
}

func ValidEnergy(s string) bool            { return inCodes(s, EnergyOptions) }
func ValidDomain(s string) bool            { return inCodes(s, DomainOptions) }
func ValidDomainStatus(s string) bool      { return inCodes(s, DomainStatusOptions) }
func ValidBucket(s string) bool            { return inCodes(s, BucketOptions) }
func ValidWorth(s string) bool             { return inCodes(s, WorthOptions) }
func ValidHealthModule(s string) bool      { return inCodes(s, HealthModuleOptions) }

func labelOf(code string, opts []Option) string {
	for _, o := range opts {
		if code == o.Code {
			return o.Label
		}
	}
	return code
}

func EnergyLabel(code string) string       { return labelOf(code, EnergyOptions) }
func DomainLabel(code string) string       { return labelOf(code, DomainOptions) }
func DomainStatusLabel(code string) string { return labelOf(code, DomainStatusOptions) }
func BucketLabel(code string) string       { return labelOf(code, BucketOptions) }
func WorthLabel(code string) string        { return labelOf(code, WorthOptions) }
func ModuleLabel(code string) string       { return labelOf(code, HealthModuleOptions) }

// ---------- 领域模型（与 db/schema.sql 列一一对应） ----------

// User 为安全用户视图；password_hash 仅在 UserByName 的内部行map中使用，绝不输出。
type User struct {
	ID          int64  `json:"id" db:"id"`
	Username    string `json:"username" db:"username"`
	DisplayName string `json:"display_name" db:"display_name"`
	IsStaff     bool   `json:"is_staff" db:"is_staff"`
	CreatedAt   string `json:"created_at" db:"created_at"`
}

type DailyPlan struct {
	ID                        int64    `json:"id" db:"id"`
	UserID                    int64    `json:"user_id" db:"user_id"`
	PlanDate                  string   `json:"plan_date" db:"plan_date"`
	SleepHours                *float64 `json:"sleep_hours" db:"sleep_hours"`
	EnergyLevel               string   `json:"energy_level" db:"energy_level"`
	MIT                       string   `json:"mit" db:"mit"`
	PreKnowOutput             bool     `json:"pre_know_output" db:"pre_know_output"`
	PreMaterialsReady         bool     `json:"pre_materials_ready" db:"pre_materials_ready"`
	PrePhoneHandled           bool     `json:"pre_phone_handled" db:"pre_phone_handled"`
	DuringOneBlock            bool     `json:"during_one_block" db:"during_one_block"`
	DuringShrinkStep          bool     `json:"during_shrink_step" db:"during_shrink_step"`
	DuringRecordDistraction   bool     `json:"during_record_distraction" db:"during_record_distraction"`
	PostVisibleOutput         bool     `json:"post_visible_output" db:"post_visible_output"`
	PostRecall                bool     `json:"post_recall" db:"post_recall"`
	PostNextStep              bool     `json:"post_next_step" db:"post_next_step"`
	LifeWaterFood             bool     `json:"life_water_food" db:"life_water_food"`
	LifeWalkExercise          bool     `json:"life_walk_exercise" db:"life_walk_exercise"`
	LifeCleanTidy             bool     `json:"life_clean_tidy" db:"life_clean_tidy"`
	LifeSocialOutdoor         bool     `json:"life_social_outdoor" db:"life_social_outdoor"`
	ReviewAccomplished        string   `json:"review_accomplished" db:"review_accomplished"`
	ReviewResistance          string   `json:"review_resistance" db:"review_resistance"`
	ReviewTomorrowStart       string   `json:"review_tomorrow_start" db:"review_tomorrow_start"`
	ReviewGratitude           string   `json:"review_gratitude" db:"review_gratitude"`
	CreatedAt                 string   `json:"created_at" db:"created_at"`
}

type TimeBlock struct {
	ID          int64   `json:"id" db:"id"`
	DailyPlanID int64   `json:"daily_plan_id" db:"daily_plan_id"`
	Seq         int     `json:"seq" db:"seq"`
	BlockName   string  `json:"block_name" db:"block_name"`
	InputDesc   string  `json:"input_desc" db:"input_desc"`
	KeyAction   string  `json:"key_action" db:"key_action"`
	Deliverable string  `json:"deliverable" db:"deliverable"`
	TimeStart   *string `json:"time_start" db:"time_start"`
	TimeEnd     *string `json:"time_end" db:"time_end"`
	IsDone      bool    `json:"is_done" db:"is_done"`
}

type WeeklyReview struct {
	ID                int64  `json:"id" db:"id"`
	UserID            int64  `json:"user_id" db:"user_id"`
	WeekStart         string `json:"week_start" db:"week_start"`
	NextMIT1          string `json:"next_mit_1" db:"next_mit_1"`
	NextMIT2          string `json:"next_mit_2" db:"next_mit_2"`
	NextMIT3          string `json:"next_mit_3" db:"next_mit_3"`
	DeepWorkTime      string `json:"deep_work_time" db:"deep_work_time"`
	PrepEnvMaterials  string `json:"prep_env_materials" db:"prep_env_materials"`
	IfCondition       string `json:"if_condition" db:"if_condition"`
	ThenAction        string `json:"then_action" db:"then_action"`
	QSkilledBlock     bool   `json:"q_skilled_block" db:"q_skilled_block"`
	QLowValueTime     bool   `json:"q_low_value_time" db:"q_low_value_time"`
	QEstimateGap      bool   `json:"q_estimate_gap" db:"q_estimate_gap"`
	QRestConnection   bool   `json:"q_rest_connection" db:"q_rest_connection"`
	QNextAdjust       bool   `json:"q_next_adjust" db:"q_next_adjust"`
	CreatedAt         string `json:"created_at" db:"created_at"`
}

type WeeklyDomainReview struct {
	ID             int64  `json:"id" db:"id"`
	WeeklyReviewID int64  `json:"weekly_review_id" db:"weekly_review_id"`
	Domain         string `json:"domain" db:"domain"`
	Facts          string `json:"facts" db:"facts"`
	Status         string `json:"status" db:"status"`
}

type WeeklyTaskFlow struct {
	ID             int64  `json:"id" db:"id"`
	WeeklyReviewID int64  `json:"weekly_review_id" db:"weekly_review_id"`
	Bucket         string `json:"bucket" db:"bucket"`
	Content        string `json:"content" db:"content"`
	Seq            int    `json:"seq" db:"seq"`
}

type MonthlyReview struct {
	ID                      int64  `json:"id" db:"id"`
	UserID                  int64  `json:"user_id" db:"user_id"`
	Month                   string `json:"month" db:"month"`
	TopAchievement          string `json:"top_achievement" db:"top_achievement"`
	WorthContinue           string `json:"worth_continue" db:"worth_continue"`
	WorthReason             string `json:"worth_reason" db:"worth_reason"`
	MostValuableInput       string `json:"most_valuable_input" db:"most_valuable_input"`
	BiggestOpportunityCost  string `json:"biggest_opportunity_cost" db:"biggest_opportunity_cost"`
	KeepItem                string `json:"keep_item" db:"keep_item"`
	CutItem                 string `json:"cut_item" db:"cut_item"`
	ExperimentItem          string `json:"experiment_item" db:"experiment_item"`
	QSkillPortfolio         bool   `json:"q_skill_portfolio" db:"q_skill_portfolio"`
	QCareerAlignment        bool   `json:"q_career_alignment" db:"q_career_alignment"`
	QLifeAutonomy           bool   `json:"q_life_autonomy" db:"q_life_autonomy"`
	QRecoveryRelations      bool   `json:"q_recovery_relations" db:"q_recovery_relations"`
	QLongtermDecision       bool   `json:"q_longterm_decision" db:"q_longterm_decision"`
	CreatedAt               string `json:"created_at" db:"created_at"`
}

type SystemHealthCheck struct {
	ID               int64  `json:"id" db:"id"`
	MonthlyReviewID  int64  `json:"monthly_review_id" db:"monthly_review_id"`
	Module           string `json:"module" db:"module"`
	Score            *int   `json:"score" db:"score"`
	NeededChange     string `json:"needed_change" db:"needed_change"`
}

// ---------- 写入请求体 ----------

type TimeBlockIn struct {
	Seq         int     `json:"seq"`
	BlockName   string  `json:"block_name"`
	InputDesc   string  `json:"input_desc"`
	KeyAction   string  `json:"key_action"`
	Deliverable string  `json:"deliverable"`
	TimeStart   *string `json:"time_start"`
	TimeEnd     *string `json:"time_end"`
	IsDone      bool    `json:"is_done"`
}

type DailyIn struct {
	PlanDate                string       `json:"plan_date"`
	SleepHours              *float64     `json:"sleep_hours"`
	EnergyLevel             string       `json:"energy_level"`
	MIT                     string       `json:"mit"`
	PreKnowOutput           bool         `json:"pre_know_output"`
	PreMaterialsReady       bool         `json:"pre_materials_ready"`
	PrePhoneHandled         bool         `json:"pre_phone_handled"`
	DuringOneBlock          bool         `json:"during_one_block"`
	DuringShrinkStep        bool         `json:"during_shrink_step"`
	DuringRecordDistraction bool         `json:"during_record_distraction"`
	PostVisibleOutput       bool         `json:"post_visible_output"`
	PostRecall              bool         `json:"post_recall"`
	PostNextStep            bool         `json:"post_next_step"`
	LifeWaterFood           bool         `json:"life_water_food"`
	LifeWalkExercise        bool         `json:"life_walk_exercise"`
	LifeCleanTidy           bool         `json:"life_clean_tidy"`
	LifeSocialOutdoor       bool         `json:"life_social_outdoor"`
	ReviewAccomplished      string       `json:"review_accomplished"`
	ReviewResistance        string       `json:"review_resistance"`
	ReviewTomorrowStart     string       `json:"review_tomorrow_start"`
	ReviewGratitude         string       `json:"review_gratitude"`
	Blocks                  []TimeBlockIn `json:"blocks"`
}

type WeeklyDomainIn struct {
	Domain string `json:"domain"`
	Facts  string `json:"facts"`
	Status string `json:"status"`
}

type WeeklyFlowIn struct {
	Bucket  string `json:"bucket"`
	Content string `json:"content"`
	Seq     int    `json:"seq"`
}

type WeeklyIn struct {
	WeekStart        string           `json:"week_start"`
	NextMIT1         string           `json:"next_mit_1"`
	NextMIT2         string           `json:"next_mit_2"`
	NextMIT3         string           `json:"next_mit_3"`
	DeepWorkTime     string           `json:"deep_work_time"`
	PrepEnvMaterials string           `json:"prep_env_materials"`
	IfCondition      string           `json:"if_condition"`
	ThenAction       string           `json:"then_action"`
	QSkilledBlock    bool             `json:"q_skilled_block"`
	QLowValueTime    bool             `json:"q_low_value_time"`
	QEstimateGap     bool             `json:"q_estimate_gap"`
	QRestConnection  bool             `json:"q_rest_connection"`
	QNextAdjust      bool             `json:"q_next_adjust"`
	Domains          []WeeklyDomainIn `json:"domains"`
	Flows            []WeeklyFlowIn   `json:"flows"`
}

type HealthCheckIn struct {
	Module       string `json:"module"`
	Score        *int   `json:"score"`
	NeededChange string `json:"needed_change"`
}

type MonthlyIn struct {
	Month                  string          `json:"month"`
	TopAchievement         string          `json:"top_achievement"`
	WorthContinue          string          `json:"worth_continue"`
	WorthReason            string          `json:"worth_reason"`
	MostValuableInput      string          `json:"most_valuable_input"`
	BiggestOpportunityCost string          `json:"biggest_opportunity_cost"`
	KeepItem               string          `json:"keep_item"`
	CutItem                string          `json:"cut_item"`
	ExperimentItem         string          `json:"experiment_item"`
	QSkillPortfolio        bool            `json:"q_skill_portfolio"`
	QCareerAlignment       bool            `json:"q_career_alignment"`
	QLifeAutonomy          bool            `json:"q_life_autonomy"`
	QRecoveryRelations     bool            `json:"q_recovery_relations"`
	QLongtermDecision      bool            `json:"q_longterm_decision"`
	HealthChecks           []HealthCheckIn `json:"health_checks"`
}

// ---------- 输出视图（含读时计算字段） ----------

type DailyOut struct {
	DailyPlan
	Blocks         []TimeBlock `json:"blocks"`
	BlocksTotal    int         `json:"blocks_total"`
	BlocksDone     int         `json:"blocks_done"`
	BlockProgress  int         `json:"block_progress"`
	ChecklistTotal int         `json:"checklist_total"`
	ChecklistDone  int         `json:"checklist_done"`
}

type WeeklyOut struct {
	WeeklyReview
	WeekEnd string               `json:"week_end"`
	Domains []WeeklyDomainReview `json:"domains"`
	Flows   []WeeklyTaskFlow     `json:"flows"`
}

type MonthlyOut struct {
	MonthlyReview
	HealthChecks []SystemHealthCheck `json:"health_checks"`
	AvgScore     *float64            `json:"avg_score"`
}

type DashboardOut struct {
	DailyCount    int64        `json:"daily_count"`
	WeeklyCount   int64        `json:"weekly_count"`
	MonthlyCount  int64        `json:"monthly_count"`
	TodayPlan     *DailyOut    `json:"today_plan"`
	WeekReview    *WeeklyOut   `json:"week_review"`
	MonthReview   *MonthlyOut  `json:"month_review"`
	RecentDaily   []DailyOut   `json:"recent_daily"`
	RecentWeekly  []WeeklyOut  `json:"recent_weekly"`
	RecentMonthly []MonthlyOut `json:"recent_monthly"`
	BlockTotal    int64        `json:"block_total"`
	BlockDone     int64        `json:"block_done"`
}

// checklistFields 为执行前后 9 项 + 生活维护 4 项，共 13 项。
var checklistFields = []func(DailyPlan) bool{
	func(p DailyPlan) bool { return p.PreKnowOutput },
	func(p DailyPlan) bool { return p.PreMaterialsReady },
	func(p DailyPlan) bool { return p.PrePhoneHandled },
	func(p DailyPlan) bool { return p.DuringOneBlock },
	func(p DailyPlan) bool { return p.DuringShrinkStep },
	func(p DailyPlan) bool { return p.DuringRecordDistraction },
	func(p DailyPlan) bool { return p.PostVisibleOutput },
	func(p DailyPlan) bool { return p.PostRecall },
	func(p DailyPlan) bool { return p.PostNextStep },
	func(p DailyPlan) bool { return p.LifeWaterFood },
	func(p DailyPlan) bool { return p.LifeWalkExercise },
	func(p DailyPlan) bool { return p.LifeCleanTidy },
	func(p DailyPlan) bool { return p.LifeSocialOutdoor },
}

func (p DailyPlan) countChecklist() int {
	n := 0
	for _, f := range checklistFields {
		if f(p) {
			n++
		}
	}
	return n
}

// DailyOutOf 组装日清单输出并计算 blocks_total / blocks_done / block_progress /
// checklist_total(13) / checklist_done。
func DailyOutOf(p DailyPlan, blocks []TimeBlock) DailyOut {
	if blocks == nil {
		blocks = []TimeBlock{}
	}
	done := 0
	for _, b := range blocks {
		if b.IsDone {
			done++
		}
	}
	return DailyOut{
		DailyPlan: p, Blocks: blocks,
		BlocksTotal: len(blocks), BlocksDone: done,
		BlockProgress: Progress(done, len(blocks)),
		ChecklistTotal: len(checklistFields), ChecklistDone: p.countChecklist(),
	}
}

// WeekEndOf 读时计算周末日期 = week_start + 6 天。
func WeekEndOf(weekStart string) string {
	t, err := time.Parse("2006-01-02", weekStart)
	if err != nil {
		return ""
	}
	return t.AddDate(0, 0, 6).Format("2006-01-02")
}

func WeeklyOutOf(w WeeklyReview, domains []WeeklyDomainReview, flows []WeeklyTaskFlow) WeeklyOut {
	if domains == nil {
		domains = []WeeklyDomainReview{}
	}
	if flows == nil {
		flows = []WeeklyTaskFlow{}
	}
	return WeeklyOut{WeeklyReview: w, WeekEnd: WeekEndOf(w.WeekStart), Domains: domains, Flows: flows}
}

// AvgScoreOf 读时计算系统健康检查平均分（忽略 NULL，保留 1 位小数）。
func AvgScoreOf(checks []SystemHealthCheck) *float64 {
	sum, n := 0.0, 0
	for _, c := range checks {
		if c.Score != nil {
			sum += float64(*c.Score)
			n++
		}
	}
	if n == 0 {
		return nil
	}
	avg := math.Round(sum/float64(n)*10) / 10
	return &avg
}

func MonthlyOutOf(m MonthlyReview, checks []SystemHealthCheck) MonthlyOut {
	if checks == nil {
		checks = []SystemHealthCheck{}
	}
	return MonthlyOut{MonthlyReview: m, HealthChecks: checks, AvgScore: AvgScoreOf(checks)}
}

// ---------- 行 → 模型 ----------

func UserOut(row map[string]any) map[string]any {
	if row == nil {
		return nil
	}
	return map[string]any{
		"id":           row["id"],
		"username":     row["username"],
		"display_name": row["display_name"],
		"is_staff":     toBool(row["is_staff"]),
		"created_at":   row["created_at"],
	}
}

func UserView(row map[string]any) map[string]any {
	staff := toBool(row["is_staff"])
	label, badge := "普通用户", "bg-secondary"
	if staff {
		label, badge = "管理员", "bg-primary"
	}
	return map[string]any{
		"ID": ToInt64(row["id"]), "Username": str(row["username"]),
		"DisplayName": str(row["display_name"]), "IsStaff": staff,
		"StaffLabel": label, "StaffBadge": badge,
		"CreatedDate": DateOnly(row["created_at"]),
	}
}

func dailyFromRow(row map[string]any) DailyPlan {
	return DailyPlan{
		ID: ToInt64(row["id"]), UserID: ToInt64(row["user_id"]),
		PlanDate: DateOnly(row["plan_date"]), SleepHours: toFloatPtr(row["sleep_hours"]),
		EnergyLevel: str(row["energy_level"]), MIT: str(row["mit"]),
		PreKnowOutput: toBool(row["pre_know_output"]),
		PreMaterialsReady: toBool(row["pre_materials_ready"]),
		PrePhoneHandled: toBool(row["pre_phone_handled"]),
		DuringOneBlock: toBool(row["during_one_block"]),
		DuringShrinkStep: toBool(row["during_shrink_step"]),
		DuringRecordDistraction: toBool(row["during_record_distraction"]),
		PostVisibleOutput: toBool(row["post_visible_output"]),
		PostRecall: toBool(row["post_recall"]),
		PostNextStep: toBool(row["post_next_step"]),
		LifeWaterFood: toBool(row["life_water_food"]),
		LifeWalkExercise: toBool(row["life_walk_exercise"]),
		LifeCleanTidy: toBool(row["life_clean_tidy"]),
		LifeSocialOutdoor: toBool(row["life_social_outdoor"]),
		ReviewAccomplished: str(row["review_accomplished"]),
		ReviewResistance: str(row["review_resistance"]),
		ReviewTomorrowStart: str(row["review_tomorrow_start"]),
		ReviewGratitude: str(row["review_gratitude"]),
		CreatedAt: str(row["created_at"]),
	}
}

func blockFromRow(row map[string]any) TimeBlock {
	return TimeBlock{
		ID: ToInt64(row["id"]), DailyPlanID: ToInt64(row["daily_plan_id"]),
		Seq: toInt(row["seq"]), BlockName: str(row["block_name"]),
		InputDesc: str(row["input_desc"]), KeyAction: str(row["key_action"]),
		Deliverable: str(row["deliverable"]),
		TimeStart: timeHM(row["time_start"]), TimeEnd: timeHM(row["time_end"]),
		IsDone: toBool(row["is_done"]),
	}
}

func weeklyFromRow(row map[string]any) WeeklyReview {
	return WeeklyReview{
		ID: ToInt64(row["id"]), UserID: ToInt64(row["user_id"]),
		WeekStart: DateOnly(row["week_start"]),
		NextMIT1: str(row["next_mit_1"]), NextMIT2: str(row["next_mit_2"]),
		NextMIT3: str(row["next_mit_3"]), DeepWorkTime: str(row["deep_work_time"]),
		PrepEnvMaterials: str(row["prep_env_materials"]),
		IfCondition: str(row["if_condition"]), ThenAction: str(row["then_action"]),
		QSkilledBlock: toBool(row["q_skilled_block"]),
		QLowValueTime: toBool(row["q_low_value_time"]),
		QEstimateGap: toBool(row["q_estimate_gap"]),
		QRestConnection: toBool(row["q_rest_connection"]),
		QNextAdjust: toBool(row["q_next_adjust"]),
		CreatedAt: str(row["created_at"]),
	}
}

func domainFromRow(row map[string]any) WeeklyDomainReview {
	return WeeklyDomainReview{
		ID: ToInt64(row["id"]), WeeklyReviewID: ToInt64(row["weekly_review_id"]),
		Domain: str(row["domain"]), Facts: str(row["facts"]), Status: str(row["status"]),
	}
}

func flowFromRow(row map[string]any) WeeklyTaskFlow {
	return WeeklyTaskFlow{
		ID: ToInt64(row["id"]), WeeklyReviewID: ToInt64(row["weekly_review_id"]),
		Bucket: str(row["bucket"]), Content: str(row["content"]), Seq: toInt(row["seq"]),
	}
}

func monthlyFromRow(row map[string]any) MonthlyReview {
	return MonthlyReview{
		ID: ToInt64(row["id"]), UserID: ToInt64(row["user_id"]), Month: str(row["month"]),
		TopAchievement: str(row["top_achievement"]),
		WorthContinue: str(row["worth_continue"]), WorthReason: str(row["worth_reason"]),
		MostValuableInput: str(row["most_valuable_input"]),
		BiggestOpportunityCost: str(row["biggest_opportunity_cost"]),
		KeepItem: str(row["keep_item"]), CutItem: str(row["cut_item"]),
		ExperimentItem: str(row["experiment_item"]),
		QSkillPortfolio: toBool(row["q_skill_portfolio"]),
		QCareerAlignment: toBool(row["q_career_alignment"]),
		QLifeAutonomy: toBool(row["q_life_autonomy"]),
		QRecoveryRelations: toBool(row["q_recovery_relations"]),
		QLongtermDecision: toBool(row["q_longterm_decision"]),
		CreatedAt: str(row["created_at"]),
	}
}

func healthFromRow(row map[string]any) SystemHealthCheck {
	return SystemHealthCheck{
		ID: ToInt64(row["id"]), MonthlyReviewID: ToInt64(row["monthly_review_id"]),
		Module: str(row["module"]), Score: toIntPtr(row["score"]),
		NeededChange: str(row["needed_change"]),
	}
}
