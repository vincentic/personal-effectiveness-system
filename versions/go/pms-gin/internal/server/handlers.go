package server

import (
	"fmt"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/database"
	"pms-gin/internal/models"
)

// ---------- 通用 helper ----------

func vstr(v any) string {
	if v == nil {
		return ""
	}
	return fmt.Sprint(v)
}

func parseID(c *gin.Context) (int64, bool) {
	n, err := strconv.ParseInt(c.Param("id"), 10, 64)
	return n, err == nil && n > 0
}

func userViews() []map[string]any {
	rows, err := models.Users()
	if err != nil {
		return []map[string]any{}
	}
	out := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		out = append(out, models.UserView(r))
	}
	return out
}

func formInt(c *gin.Context, key string) int {
	n, _ := strconv.Atoi(strings.TrimSpace(c.PostForm(key)))
	return n
}

func formBool(c *gin.Context, key string) bool { return c.PostForm(key) != "" }

func formFloatPtr(c *gin.Context, key string) *float64 {
	raw := strings.TrimSpace(c.PostForm(key))
	if raw == "" {
		return nil
	}
	f, err := strconv.ParseFloat(raw, 64)
	if err != nil {
		return nil
	}
	return &f
}

func formStringPtr(c *gin.Context, key string) *string {
	raw := strings.TrimSpace(c.PostForm(key))
	if raw == "" {
		return nil
	}
	return &raw
}

func pageErr(msg string) gin.H { return gin.H{"detail": msg} }

// ---------- 表单 → 请求体 ----------

func dailyFormIn(c *gin.Context) *models.DailyIn {
	in := &models.DailyIn{
		PlanDate:                strings.TrimSpace(c.PostForm("plan_date")),
		SleepHours:              formFloatPtr(c, "sleep_hours"),
		EnergyLevel:             c.PostForm("energy_level"),
		MIT:                     c.PostForm("mit"),
		PreKnowOutput:           formBool(c, "pre_know_output"),
		PreMaterialsReady:       formBool(c, "pre_materials_ready"),
		PrePhoneHandled:         formBool(c, "pre_phone_handled"),
		DuringOneBlock:          formBool(c, "during_one_block"),
		DuringShrinkStep:        formBool(c, "during_shrink_step"),
		DuringRecordDistraction: formBool(c, "during_record_distraction"),
		PostVisibleOutput:       formBool(c, "post_visible_output"),
		PostRecall:              formBool(c, "post_recall"),
		PostNextStep:            formBool(c, "post_next_step"),
		LifeWaterFood:           formBool(c, "life_water_food"),
		LifeWalkExercise:        formBool(c, "life_walk_exercise"),
		LifeCleanTidy:           formBool(c, "life_clean_tidy"),
		LifeSocialOutdoor:       formBool(c, "life_social_outdoor"),
		ReviewAccomplished:      c.PostForm("review_accomplished"),
		ReviewResistance:        c.PostForm("review_resistance"),
		ReviewTomorrowStart:     c.PostForm("review_tomorrow_start"),
		ReviewGratitude:         c.PostForm("review_gratitude"),
	}
	n := formInt(c, "block_count")
	if n < 0 {
		n = 0
	}
	if n > 50 {
		n = 50
	}
	in.Blocks = make([]models.TimeBlockIn, 0, n)
	for i := 0; i < n; i++ {
		p := fmt.Sprintf("blocks[%d].", i)
		in.Blocks = append(in.Blocks, models.TimeBlockIn{
			Seq: formInt(c, p+"seq"), BlockName: c.PostForm(p + "block_name"),
			InputDesc: c.PostForm(p + "input_desc"), KeyAction: c.PostForm(p + "key_action"),
			Deliverable: c.PostForm(p + "deliverable"),
			TimeStart:   formStringPtr(c, p+"time_start"), TimeEnd: formStringPtr(c, p+"time_end"),
			IsDone: formBool(c, p+"is_done"),
		})
	}
	return in
}

func weeklyFormIn(c *gin.Context) *models.WeeklyIn {
	in := &models.WeeklyIn{
		WeekStart: strings.TrimSpace(c.PostForm("week_start")),
		NextMIT1:  c.PostForm("next_mit_1"), NextMIT2: c.PostForm("next_mit_2"),
		NextMIT3: c.PostForm("next_mit_3"), DeepWorkTime: c.PostForm("deep_work_time"),
		PrepEnvMaterials: c.PostForm("prep_env_materials"),
		IfCondition:      c.PostForm("if_condition"), ThenAction: c.PostForm("then_action"),
		QSkilledBlock:   formBool(c, "q_skilled_block"),
		QLowValueTime:   formBool(c, "q_low_value_time"),
		QEstimateGap:    formBool(c, "q_estimate_gap"),
		QRestConnection: formBool(c, "q_rest_connection"),
		QNextAdjust:     formBool(c, "q_next_adjust"),
	}
	for _, key := range []string{"work", "study", "body", "relations"} {
		p := "domains[" + key + "]."
		in.Domains = append(in.Domains, models.WeeklyDomainIn{
			Domain: key, Facts: c.PostForm(p + "facts"), Status: c.PostForm(p + "status"),
		})
	}
	n := formInt(c, "flow_count")
	if n < 0 {
		n = 0
	}
	if n > 50 {
		n = 50
	}
	in.Flows = make([]models.WeeklyFlowIn, 0, n)
	for i := 0; i < n; i++ {
		p := fmt.Sprintf("flows[%d].", i)
		in.Flows = append(in.Flows, models.WeeklyFlowIn{
			Bucket: c.PostForm(p + "bucket"), Content: c.PostForm(p + "content"),
			Seq: formInt(c, p+"seq"),
		})
	}
	return in
}

func monthlyFormIn(c *gin.Context) *models.MonthlyIn {
	in := &models.MonthlyIn{
		Month:          strings.TrimSpace(c.PostForm("month")),
		TopAchievement: c.PostForm("top_achievement"),
		WorthContinue:  c.PostForm("worth_continue"), WorthReason: c.PostForm("worth_reason"),
		MostValuableInput:      c.PostForm("most_valuable_input"),
		BiggestOpportunityCost: c.PostForm("biggest_opportunity_cost"),
		KeepItem:               c.PostForm("keep_item"), CutItem: c.PostForm("cut_item"),
		ExperimentItem:     c.PostForm("experiment_item"),
		QSkillPortfolio:    formBool(c, "q_skill_portfolio"),
		QCareerAlignment:   formBool(c, "q_career_alignment"),
		QLifeAutonomy:      formBool(c, "q_life_autonomy"),
		QRecoveryRelations: formBool(c, "q_recovery_relations"),
		QLongtermDecision:  formBool(c, "q_longterm_decision"),
	}
	for _, m := range models.HealthModuleOptions {
		p := "checks[" + m.Code + "]."
		score := formFloatPtr(c, p+"score")
		var s *int
		if score != nil {
			n := int(*score)
			s = &n
		}
		in.HealthChecks = append(in.HealthChecks, models.HealthCheckIn{
			Module: m.Code, Score: s, NeededChange: c.PostForm(p + "needed_change"),
		})
	}
	return in
}

// ---------- 输出 → 表单 ----------

func dailyFormFrom(o *models.DailyOut) *models.DailyIn {
	in := &models.DailyIn{
		PlanDate: o.PlanDate, SleepHours: o.SleepHours, EnergyLevel: o.EnergyLevel,
		MIT:           o.MIT,
		PreKnowOutput: o.PreKnowOutput, PreMaterialsReady: o.PreMaterialsReady,
		PrePhoneHandled: o.PrePhoneHandled, DuringOneBlock: o.DuringOneBlock,
		DuringShrinkStep: o.DuringShrinkStep, DuringRecordDistraction: o.DuringRecordDistraction,
		PostVisibleOutput: o.PostVisibleOutput, PostRecall: o.PostRecall,
		PostNextStep: o.PostNextStep, LifeWaterFood: o.LifeWaterFood,
		LifeWalkExercise: o.LifeWalkExercise, LifeCleanTidy: o.LifeCleanTidy,
		LifeSocialOutdoor:  o.LifeSocialOutdoor,
		ReviewAccomplished: o.ReviewAccomplished, ReviewResistance: o.ReviewResistance,
		ReviewTomorrowStart: o.ReviewTomorrowStart, ReviewGratitude: o.ReviewGratitude,
	}
	for _, b := range o.Blocks {
		in.Blocks = append(in.Blocks, models.TimeBlockIn{
			Seq: b.Seq, BlockName: b.BlockName, InputDesc: b.InputDesc,
			KeyAction: b.KeyAction, Deliverable: b.Deliverable,
			TimeStart: b.TimeStart, TimeEnd: b.TimeEnd, IsDone: b.IsDone,
		})
	}
	if len(in.Blocks) < 3 {
		blanks := make([]models.TimeBlockIn, 3-len(in.Blocks))
		in.Blocks = append(in.Blocks, blanks...)
	}
	return in
}

func weeklyFormFrom(o *models.WeeklyOut) *models.WeeklyIn {
	in := &models.WeeklyIn{
		WeekStart: o.WeekStart, NextMIT1: o.NextMIT1, NextMIT2: o.NextMIT2,
		NextMIT3: o.NextMIT3, DeepWorkTime: o.DeepWorkTime,
		PrepEnvMaterials: o.PrepEnvMaterials, IfCondition: o.IfCondition,
		ThenAction:    o.ThenAction,
		QSkilledBlock: o.QSkilledBlock, QLowValueTime: o.QLowValueTime,
		QEstimateGap: o.QEstimateGap, QRestConnection: o.QRestConnection,
		QNextAdjust: o.QNextAdjust,
	}
	byDomain := map[string]models.WeeklyDomainReview{}
	for _, d := range o.Domains {
		byDomain[d.Domain] = d
	}
	for _, opt := range models.DomainOptions {
		d := byDomain[opt.Code]
		status := d.Status
		if status == "" {
			status = "smooth"
		}
		in.Domains = append(in.Domains, models.WeeklyDomainIn{
			Domain: opt.Code, Facts: d.Facts, Status: status,
		})
	}
	for _, f := range o.Flows {
		in.Flows = append(in.Flows, models.WeeklyFlowIn{
			Bucket: f.Bucket, Content: f.Content, Seq: f.Seq,
		})
	}
	if len(in.Flows) < 3 {
		blanks := make([]models.WeeklyFlowIn, 3-len(in.Flows))
		for i := range blanks {
			blanks[i].Bucket = "todo"
		}
		in.Flows = append(in.Flows, blanks...)
	}
	return in
}

func monthlyFormFrom(o *models.MonthlyOut) *models.MonthlyIn {
	in := &models.MonthlyIn{
		Month: o.Month, TopAchievement: o.TopAchievement,
		WorthContinue: o.WorthContinue, WorthReason: o.WorthReason,
		MostValuableInput:      o.MostValuableInput,
		BiggestOpportunityCost: o.BiggestOpportunityCost,
		KeepItem:               o.KeepItem, CutItem: o.CutItem, ExperimentItem: o.ExperimentItem,
		QSkillPortfolio: o.QSkillPortfolio, QCareerAlignment: o.QCareerAlignment,
		QLifeAutonomy: o.QLifeAutonomy, QRecoveryRelations: o.QRecoveryRelations,
		QLongtermDecision: o.QLongtermDecision,
	}
	if in.WorthContinue == "" {
		in.WorthContinue = "yes"
	}
	byModule := map[string]models.SystemHealthCheck{}
	for _, h := range o.HealthChecks {
		byModule[h.Module] = h
	}
	for _, opt := range models.HealthModuleOptions {
		h := byModule[opt.Code]
		in.HealthChecks = append(in.HealthChecks, models.HealthCheckIn{
			Module: opt.Code, Score: h.Score, NeededChange: h.NeededChange,
		})
	}
	return in
}

func newDailyForm() *models.DailyIn {
	fresh := &models.DailyIn{PlanDate: models.CurrentDate(), EnergyLevel: "medium"}
	return dailyFormFrom(&models.DailyOut{DailyPlan: models.DailyPlan{
		PlanDate: fresh.PlanDate, EnergyLevel: fresh.EnergyLevel}})
}

func newWeeklyForm() *models.WeeklyIn {
	return weeklyFormFrom(&models.WeeklyOut{WeeklyReview: models.WeeklyReview{
		WeekStart: models.CurrentWeekStart()}})
}

func newMonthlyForm() *models.MonthlyIn {
	return monthlyFormFrom(&models.MonthlyOut{MonthlyReview: models.MonthlyReview{
		Month: models.CurrentMonth(), WorthContinue: "yes"}})
}

// ---------- 表单校验辅助 ----------

type userInput struct {
	Username, Password, DisplayName string
	IsStaff                         bool
}

func validateUser(c *gin.Context) (userInput, gin.H, gin.H) {
	username := strings.TrimSpace(c.PostForm("username"))
	password := c.PostForm("password")
	display := strings.TrimSpace(c.PostForm("display_name"))
	staff := formBool(c, "is_staff")
	errs := gin.H{}
	if username == "" {
		errs["username"] = "用户名不能为空"
	} else if len([]rune(username)) > 150 {
		errs["username"] = "用户名不能超过 150 字"
	}
	if len(password) < 8 {
		errs["password"] = "密码至少 8 位"
	} else if len(password) > 72 {
		errs["password"] = "密码不能超过 72 位"
	}
	if username != "" {
		if row, _ := models.UserByName(username); row != nil {
			errs["username"] = "用户名已存在"
		}
	}
	if display == "" {
		display = username
	}
	form := gin.H{"Username": username, "DisplayName": display, "IsStaff": staff, "Password": ""}
	return userInput{username, password, display, staff}, form, errs
}

func (s *Server) renderAdminUsers(c *gin.Context, form, errs gin.H, msg bool) {
	s.render(c, s.pageName(c, "admin_users.html"), "用户管理", gin.H{
		"Users": userViews(), "Form": form, "Errors": errs, "Msg": msg,
	})
}

// ---------- 登录 / 退出 ----------

func (s *Server) loginGET(c *gin.Context) {
	if s.currentSession(c) != nil {
		c.Redirect(http.StatusFound, "/")
		return
	}
	s.render(c, s.pageName(c, "login.html"), "登录", gin.H{"LoginError": false, "UsernameValue": ""})
}

func (s *Server) loginPOST(c *gin.Context) {
	if s.currentSession(c) != nil {
		c.Redirect(http.StatusFound, "/")
		return
	}
	username := strings.TrimSpace(c.PostForm("username"))
	password := c.PostForm("password")
	row, err := models.UserByName(username)
	if err != nil || row == nil || !database.VerifyPassword(password, vstr(row["password_hash"])) {
		s.render(c, s.pageName(c, "login.html"), "登录", gin.H{"LoginError": true, "UsernameValue": username})
		return
	}
	s.setSession(c, Session{
		UID:      models.ToInt64(row["id"]),
		Username: vstr(row["username"]),
		Staff:    models.ToInt64(row["is_staff"]) != 0,
	})
	c.Redirect(http.StatusFound, "/")
}

func (s *Server) logoutPOST(c *gin.Context) {
	s.clearSession(c)
	c.Redirect(http.StatusFound, "/login")
}

// ---------- 工作台 ----------

func (s *Server) dashboardGET(c *gin.Context) {
	uid := s.currentSession(c).UID
	data := gin.H{}
	dailyCount, weeklyCount, monthlyCount, err := models.DashboardCounts(uid)
	if err != nil {
		dailyCount, weeklyCount, monthlyCount = 0, 0, 0
	}
	blockTotal, blockDone, _ := models.BlockTotals(uid)
	data["DailyCount"], data["WeeklyCount"], data["MonthlyCount"] = dailyCount, weeklyCount, monthlyCount
	data["BlockTotal"], data["BlockDone"] = blockTotal, blockDone
	recentDaily, _ := models.DailyPlans(uid, 5)
	recentWeekly, _ := models.WeeklyReviews(uid, 5)
	recentMonthly, _ := models.MonthlyReviews(uid, 5)
	data["RecentDaily"], data["RecentWeekly"], data["RecentMonthly"] = recentDaily, recentWeekly, recentMonthly
	if today := models.CurrentDate(); today != "" {
		data["TodayPlan"], _ = models.DailyPlanByDate(uid, today)
	}
	if week := models.CurrentWeekStart(); week != "" {
		data["WeekReview"], _ = models.WeeklyReviewByWeek(uid, week)
	}
	if month := models.CurrentMonth(); month != "" {
		data["MonthReview"], _ = models.MonthlyReviewByMonth(uid, month)
	}
	s.render(c, s.pageName(c, "dashboard.html"), "工作台", data)
}

// ---------- 日清单页面 ----------

func (s *Server) dailyListGET(c *gin.Context) {
	plans, err := models.DailyPlans(s.currentSession(c).UID, 0)
	if err != nil {
		plans = []models.DailyOut{}
	}
	s.render(c, s.pageName(c, "daily_list.html"), "每日执行清单", gin.H{"Plans": plans,
		"EnergyOptions": models.EnergyOptions})
}

// 列表项编辑链接指向详情，详情提供编辑/删除入口。
func (s *Server) dailyEntryGET(c *gin.Context) {
	if c.Param("id") == "new" {
		s.dailyNewGET(c)
		return
	}
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	o, err := models.DailyPlanByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	s.render(c, s.pageName(c, "daily_detail.html"), "每日执行清单详情", gin.H{
		"Plan": o, "EnergyOptions": models.EnergyOptions,
	})
}

func (s *Server) dailyNewGET(c *gin.Context) {
	s.renderDailyForm(c, "新建每日执行清单", "/daily/new/", "/daily/", newDailyForm(), gin.H{})
}

func (s *Server) dailyNewPOST(c *gin.Context) {
	in := dailyFormIn(c)
	if msg := normalizeDailyIn(in); msg != "" {
		s.renderDailyForm(c, "新建每日执行清单", "/daily/new/", "/daily/", in, pageErr(msg))
		return
	}
	if _, err := models.InsertDaily(s.currentSession(c).UID, in); err != nil {
		s.renderDailyForm(c, "新建每日执行清单", "/daily/new/", "/daily/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/daily/")
}

func (s *Server) renderDailyForm(c *gin.Context, title, action, cancel string, form *models.DailyIn, errs gin.H) {
	s.render(c, s.pageName(c, "daily_form.html"), title, gin.H{
		"Action": action, "CancelURL": cancel, "Form": form, "Errors": errs,
		"EnergyOptions": models.EnergyOptions,
	})
}

func (s *Server) dailyEditGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	o, err := models.DailyPlanByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	s.renderDailyForm(c, "编辑每日执行清单", "/daily/"+c.Param("id")+"/edit/",
		"/daily/"+c.Param("id")+"/", dailyFormFrom(o), gin.H{})
}

func (s *Server) dailyEditPOST(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	o, err := models.DailyPlanByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	in := dailyFormIn(c)
	action := "/daily/" + c.Param("id") + "/edit/"
	if msg := normalizeDailyIn(in); msg != "" {
		s.renderDailyForm(c, "编辑每日执行清单", action, "/daily/"+c.Param("id")+"/", in, pageErr(msg))
		return
	}
	if err = models.UpdateDaily(id, s.currentSession(c).UID, in); err != nil {
		s.renderDailyForm(c, "编辑每日执行清单", action, "/daily/"+c.Param("id")+"/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/daily/"+c.Param("id")+"/")
}

func (s *Server) dailyDeleteGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/daily/")
		return
	}
	o, err := models.DailyPlanByID(id, s.currentSession(c).UID)
	if err == nil && o != nil {
		_ = models.DeleteDaily(id, s.currentSession(c).UID)
	}
	c.Redirect(http.StatusFound, "/daily/")
}

// ---------- 周复盘页面 ----------

func (s *Server) weeklyListGET(c *gin.Context) {
	reviews, err := models.WeeklyReviews(s.currentSession(c).UID, 0)
	if err != nil {
		reviews = []models.WeeklyOut{}
	}
	s.render(c, s.pageName(c, "weekly_list.html"), "每周复盘清单", gin.H{"Reviews": reviews})
}

func (s *Server) weeklyEntryGET(c *gin.Context) {
	if c.Param("id") == "new" {
		s.weeklyNewGET(c)
		return
	}
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	o, err := models.WeeklyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	s.render(c, s.pageName(c, "weekly_detail.html"), "每周复盘清单详情", gin.H{"Review": o})
}

func (s *Server) renderWeeklyForm(c *gin.Context, title, action, cancel string, form *models.WeeklyIn, errs gin.H) {
	s.render(c, s.pageName(c, "weekly_form.html"), title, gin.H{
		"Action": action, "CancelURL": cancel, "Form": form, "Errors": errs,
		"DomainOptions": models.DomainOptions, "StatusOptions": models.DomainStatusOptions,
		"BucketOptions": models.BucketOptions,
	})
}

func (s *Server) weeklyNewGET(c *gin.Context) {
	s.renderWeeklyForm(c, "新建每周复盘清单", "/weekly/new/", "/weekly/", newWeeklyForm(), gin.H{})
}

func (s *Server) weeklyNewPOST(c *gin.Context) {
	in := weeklyFormIn(c)
	if msg := normalizeWeeklyIn(in); msg != "" {
		s.renderWeeklyForm(c, "新建每周复盘清单", "/weekly/new/", "/weekly/", in, pageErr(msg))
		return
	}
	if _, err := models.InsertWeekly(s.currentSession(c).UID, in); err != nil {
		s.renderWeeklyForm(c, "新建每周复盘清单", "/weekly/new/", "/weekly/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/weekly/")
}

func (s *Server) weeklyEditGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	o, err := models.WeeklyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	s.renderWeeklyForm(c, "编辑每周复盘清单", "/weekly/"+c.Param("id")+"/edit/",
		"/weekly/"+c.Param("id")+"/", weeklyFormFrom(o), gin.H{})
}

func (s *Server) weeklyEditPOST(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	o, err := models.WeeklyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	in := weeklyFormIn(c)
	action := "/weekly/" + c.Param("id") + "/edit/"
	if msg := normalizeWeeklyIn(in); msg != "" {
		s.renderWeeklyForm(c, "编辑每周复盘清单", action, "/weekly/"+c.Param("id")+"/", in, pageErr(msg))
		return
	}
	if err = models.UpdateWeekly(id, s.currentSession(c).UID, in); err != nil {
		s.renderWeeklyForm(c, "编辑每周复盘清单", action, "/weekly/"+c.Param("id")+"/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/weekly/"+c.Param("id")+"/")
}

func (s *Server) weeklyDeleteGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/weekly/")
		return
	}
	o, err := models.WeeklyReviewByID(id, s.currentSession(c).UID)
	if err == nil && o != nil {
		_ = models.DeleteWeekly(id, s.currentSession(c).UID)
	}
	c.Redirect(http.StatusFound, "/weekly/")
}

// ---------- 月重构页面 ----------

func (s *Server) monthlyListGET(c *gin.Context) {
	reviews, err := models.MonthlyReviews(s.currentSession(c).UID, 0)
	if err != nil {
		reviews = []models.MonthlyOut{}
	}
	s.render(c, s.pageName(c, "monthly_list.html"), "每月重构清单", gin.H{"Reviews": reviews})
}

func (s *Server) monthlyEntryGET(c *gin.Context) {
	if c.Param("id") == "new" {
		s.monthlyNewGET(c)
		return
	}
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	o, err := models.MonthlyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	s.render(c, s.pageName(c, "monthly_detail.html"), "每月重构清单详情", gin.H{
		"Review": o, "WorthLabel": models.WorthLabel,
	})
}

func (s *Server) renderMonthlyForm(c *gin.Context, title, action, cancel string, form *models.MonthlyIn, errs gin.H) {
	s.render(c, s.pageName(c, "monthly_form.html"), title, gin.H{
		"Action": action, "CancelURL": cancel, "Form": form, "Errors": errs,
		"WorthOptions": models.WorthOptions, "ModuleOptions": models.HealthModuleOptions,
	})
}

func (s *Server) monthlyNewGET(c *gin.Context) {
	s.renderMonthlyForm(c, "新建每月重构清单", "/monthly/new/", "/monthly/", newMonthlyForm(), gin.H{})
}

func (s *Server) monthlyNewPOST(c *gin.Context) {
	in := monthlyFormIn(c)
	if msg := normalizeMonthlyIn(in); msg != "" {
		s.renderMonthlyForm(c, "新建每月重构清单", "/monthly/new/", "/monthly/", in, pageErr(msg))
		return
	}
	if _, err := models.InsertMonthly(s.currentSession(c).UID, in); err != nil {
		s.renderMonthlyForm(c, "新建每月重构清单", "/monthly/new/", "/monthly/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/monthly/")
}

func (s *Server) monthlyEditGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	o, err := models.MonthlyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	s.renderMonthlyForm(c, "编辑每月重构清单", "/monthly/"+c.Param("id")+"/edit/",
		"/monthly/"+c.Param("id")+"/", monthlyFormFrom(o), gin.H{})
}

func (s *Server) monthlyEditPOST(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	o, err := models.MonthlyReviewByID(id, s.currentSession(c).UID)
	if err != nil || o == nil {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	in := monthlyFormIn(c)
	action := "/monthly/" + c.Param("id") + "/edit/"
	if msg := normalizeMonthlyIn(in); msg != "" {
		s.renderMonthlyForm(c, "编辑每月重构清单", action, "/monthly/"+c.Param("id")+"/", in, pageErr(msg))
		return
	}
	if err = models.UpdateMonthly(id, s.currentSession(c).UID, in); err != nil {
		s.renderMonthlyForm(c, "编辑每月重构清单", action, "/monthly/"+c.Param("id")+"/", in, pageErr("保存失败："+err.Error()))
		return
	}
	c.Redirect(http.StatusFound, "/monthly/"+c.Param("id")+"/")
}

func (s *Server) monthlyDeleteGET(c *gin.Context) {
	id, ok := parseID(c)
	if !ok {
		c.Redirect(http.StatusFound, "/monthly/")
		return
	}
	o, err := models.MonthlyReviewByID(id, s.currentSession(c).UID)
	if err == nil && o != nil {
		_ = models.DeleteMonthly(id, s.currentSession(c).UID)
	}
	c.Redirect(http.StatusFound, "/monthly/")
}

// ---------- 用户管理（仅管理员） ----------

func (s *Server) adminUsersGET(c *gin.Context) {
	if !s.currentSession(c).Staff {
		c.Redirect(http.StatusFound, "/")
		return
	}
	s.renderAdminUsers(c,
		gin.H{"Username": "", "DisplayName": "", "IsStaff": false, "Password": ""},
		gin.H{}, c.Query("created") == "1")
}

func (s *Server) adminUsersPOST(c *gin.Context) {
	if !s.currentSession(c).Staff {
		c.Redirect(http.StatusFound, "/")
		return
	}
	input, form, errs := validateUser(c)
	if len(errs) > 0 {
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	hash, err := database.HashPassword(input.Password)
	if err != nil {
		errs["password"] = "保存失败：" + err.Error()
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	if _, err = models.InsertUser(input.Username, hash, input.DisplayName, input.IsStaff); err != nil {
		errs["username"] = "用户名已存在"
		s.renderAdminUsers(c, form, errs, false)
		return
	}
	c.Redirect(http.StatusFound, "/admin/users/?created=1")
}
