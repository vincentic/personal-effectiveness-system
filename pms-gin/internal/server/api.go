package server

import (
	"encoding/json"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"pms-gin/internal/database"
	"pms-gin/internal/models"
)

// ---------- API Token：base64url(json claims) + "." + hex(HMAC(secret, payload)) ----------

type apiClaims struct {
	UID      int64  `json:"uid"`
	Username string `json:"username"`
	Staff    bool   `json:"staff"`
	Exp      int64  `json:"exp"`
}

func (s *Server) issueToken(uid int64, username string, staff bool) string {
	claims := apiClaims{
		UID: uid, Username: username, Staff: staff,
		Exp: time.Now().Add(24 * time.Hour).Unix(),
	}
	raw, _ := json.Marshal(claims) // 紧凑序列化：分隔符为 , 与 :
	payload := b64encode(raw)
	return payload + "." + hmacHex(s.apiSecret, payload)
}

func (s *Server) verifyToken(token string) (*apiClaims, bool) {
	if token == "" {
		return nil, false
	}
	payload, sig, ok := strings.Cut(token, ".")
	if !ok || payload == "" || sig == "" {
		return nil, false
	}
	// 验签只覆盖收到的 payload 串，不重新序列化 claims，故字段顺序无关紧要。
	if !ctEq(hmacHex(s.apiSecret, payload), sig) {
		return nil, false
	}
	raw, err := b64decode(payload)
	if err != nil {
		return nil, false
	}
	var cl apiClaims
	if err = json.Unmarshal(raw, &cl); err != nil {
		return nil, false
	}
	if cl.Exp <= time.Now().Unix() || cl.UID <= 0 {
		return nil, false
	}
	return &cl, true
}

func apiFail(c *gin.Context, code int, detail string) {
	c.JSON(code, gin.H{"detail": detail})
}

func (s *Server) apiAuth(c *gin.Context) {
	auth := c.GetHeader("Authorization")
	if !strings.HasPrefix(auth, "Bearer ") {
		apiFail(c, http.StatusUnauthorized, "未认证或 Token 已过期")
		c.Abort()
		return
	}
	cl, ok := s.verifyToken(strings.TrimPrefix(auth, "Bearer "))
	if !ok {
		apiFail(c, http.StatusUnauthorized, "未认证或 Token 已过期")
		c.Abort()
		return
	}
	row, err := models.UserByID(cl.UID)
	if err != nil || row == nil {
		apiFail(c, http.StatusUnauthorized, "用户不存在")
		c.Abort()
		return
	}
	c.Set("apiUser", row)
	c.Next()
}

func apiUser(c *gin.Context) map[string]any {
	v, _ := c.Get("apiUser")
	row, _ := v.(map[string]any)
	return row
}

func apiUID(c *gin.Context) int64 { return models.ToInt64(apiUser(c)["id"]) }

func isStaff(row map[string]any) bool { return models.ToInt64(row["is_staff"]) != 0 }

// ---------- 请求体与校验 ----------

type loginIn struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type userIn struct {
	Username    string `json:"username"`
	Password    string `json:"password"`
	DisplayName string `json:"display_name"`
	IsStaff     bool   `json:"is_staff"`
}

func decodeJSON(c *gin.Context, dst any) bool {
	if err := json.NewDecoder(c.Request.Body).Decode(dst); err != nil {
		apiFail(c, http.StatusBadRequest, "请求数据格式错误")
		return false
	}
	return true
}

func apiID(c *gin.Context) (int64, bool) {
	n, err := strconv.ParseInt(c.Param("id"), 10, 64)
	return n, err == nil && n > 0
}

func apiNotFound(c *gin.Context) { apiFail(c, http.StatusNotFound, "记录不存在") }

// trimVarchar 归一化 VARCHAR(255) 字段并校验长度。
func trimVarchar(field, value string, max int) (string, string) {
	value = strings.TrimSpace(value)
	if len([]rune(value)) > max {
		return value, field + "不能超过 " + strconv.Itoa(max) + " 字"
	}
	return value, ""
}

func normalizeDailyIn(b *models.DailyIn) string {
	b.PlanDate = strings.TrimSpace(b.PlanDate)
	if b.PlanDate == "" {
		return "日期不能为空"
	}
	if _, err := models.ParseDate(b.PlanDate); err != nil {
		return err.Error()
	}
	if b.EnergyLevel == "" {
		b.EnergyLevel = "medium"
	}
	if !models.ValidEnergy(b.EnergyLevel) {
		return "精力值非法"
	}
	if b.SleepHours != nil && (*b.SleepHours < 0 || *b.SleepHours > 24) {
		return "睡眠小时应在 0-24 之间"
	}
	b.MIT = strings.TrimSpace(b.MIT)
	for _, p := range []*string{&b.ReviewAccomplished, &b.ReviewResistance,
		&b.ReviewTomorrowStart, &b.ReviewGratitude} {
		*p = strings.TrimSpace(*p)
	}
	for i := range b.Blocks {
		blk := &b.Blocks[i]
		blk.BlockName = strings.TrimSpace(blk.BlockName)
		if blk.BlockName == "" {
			continue // 空行忽略
		}
		if len([]rune(blk.BlockName)) > 200 {
			return "组块名称不能超过 200 字"
		}
		for _, p := range []*string{&blk.InputDesc, &blk.KeyAction, &blk.Deliverable} {
			var msg string
			*p, msg = trimVarchar("组块字段", *p, 255)
			if msg != "" {
				return msg
			}
		}
		if blk.TimeStart != nil {
			v, err := models.ParseHM(*blk.TimeStart)
			if err != nil {
				return err.Error()
			}
			if v == nil {
				blk.TimeStart = nil
			}
		}
		if blk.TimeEnd != nil {
			v, err := models.ParseHM(*blk.TimeEnd)
			if err != nil {
				return err.Error()
			}
			if v == nil {
				blk.TimeEnd = nil
			}
		}
	}
	return ""
}

func normalizeWeeklyIn(b *models.WeeklyIn) string {
	b.WeekStart = strings.TrimSpace(b.WeekStart)
	if b.WeekStart == "" {
		return "周起始日期不能为空"
	}
	if _, err := models.ParseDate(b.WeekStart); err != nil {
		return err.Error()
	}
	for _, p := range []*string{&b.NextMIT1, &b.NextMIT2, &b.NextMIT3,
		&b.DeepWorkTime, &b.IfCondition, &b.ThenAction} {
		var msg string
		*p, msg = trimVarchar("字段", *p, 255)
		if msg != "" {
			return msg
		}
	}
	b.PrepEnvMaterials = strings.TrimSpace(b.PrepEnvMaterials)
	if len(b.Domains) > 4 {
		return "领域回顾最多 4 条"
	}
	seen := map[string]bool{}
	for i := range b.Domains {
		d := &b.Domains[i]
		if !models.ValidDomain(d.Domain) {
			return "领域值非法"
		}
		if seen[d.Domain] {
			return "领域不能重复"
		}
		seen[d.Domain] = true
		if d.Status == "" {
			d.Status = "smooth"
		}
		if !models.ValidDomainStatus(d.Status) {
			return "领域状态值非法"
		}
		d.Facts = strings.TrimSpace(d.Facts)
	}
	for i := range b.Flows {
		f := &b.Flows[i]
		if f.Bucket == "" {
			f.Bucket = "todo"
		}
		if !models.ValidBucket(f.Bucket) {
			return "任务流分栏值非法"
		}
		var msg string
		f.Content, msg = trimVarchar("任务流内容", f.Content, 255)
		if msg != "" {
			return msg
		}
		if f.Seq == 0 {
			f.Seq = i + 1
		}
	}
	return ""
}

func normalizeMonthlyIn(b *models.MonthlyIn) string {
	month, err := models.ParseMonth(b.Month)
	if err != nil {
		return err.Error()
	}
	b.Month = month
	if b.WorthContinue == "" {
		b.WorthContinue = "yes"
	}
	if !models.ValidWorth(b.WorthContinue) {
		return "投入判断值非法"
	}
	var msg string
	b.WorthReason, msg = trimVarchar("原因", b.WorthReason, 255)
	if msg != "" {
		return msg
	}
	for _, p := range []*string{&b.TopAchievement, &b.MostValuableInput,
		&b.BiggestOpportunityCost, &b.KeepItem, &b.CutItem, &b.ExperimentItem} {
		*p = strings.TrimSpace(*p)
	}
	if len(b.HealthChecks) > 6 {
		return "健康检查最多 6 条"
	}
	seen := map[string]bool{}
	for _, h := range b.HealthChecks {
		if !models.ValidHealthModule(h.Module) {
			return "健康检查模块值非法"
		}
		if seen[h.Module] {
			return "健康检查模块不能重复"
		}
		seen[h.Module] = true
		if h.Score != nil && (*h.Score < 1 || *h.Score > 5) {
			return "自评分应为 1-5"
		}
		h.NeededChange = strings.TrimSpace(h.NeededChange)
	}
	return ""
}

func normalizeUserIn(b *userIn) string {
	b.Username = strings.TrimSpace(b.Username)
	if b.Username == "" {
		return "用户名不能为空"
	}
	if len([]rune(b.Username)) > 150 {
		return "用户名不能超过 150 字"
	}
	if len(b.Password) < 8 {
		return "密码至少 8 位"
	}
	if len(b.Password) > 72 {
		return "密码不能超过 72 位"
	}
	if b.DisplayName == "" {
		b.DisplayName = b.Username
	}
	return ""
}

// ---------- 端点 ----------

func (s *Server) apiLogin(c *gin.Context) {
	var body loginIn
	if !decodeJSON(c, &body) {
		return
	}
	row, err := models.UserByName(body.Username)
	if err != nil || row == nil || !database.VerifyPassword(body.Password, vstr(row["password_hash"])) {
		apiFail(c, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"token": s.issueToken(models.ToInt64(row["id"]), vstr(row["username"]),
			models.ToInt64(row["is_staff"]) != 0),
		"user": models.UserOut(row),
	})
}

func (s *Server) apiMe(c *gin.Context) {
	c.JSON(http.StatusOK, models.UserOut(apiUser(c)))
}

func (s *Server) apiDashboard(c *gin.Context) {
	uid := apiUID(c)
	out := models.DashboardOut{}
	var err error
	if out.DailyCount, out.WeeklyCount, out.MonthlyCount, err = models.DashboardCounts(uid); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out.RecentDaily, err = models.DailyPlans(uid, 5); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out.RecentWeekly, err = models.WeeklyReviews(uid, 5); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out.RecentMonthly, err = models.MonthlyReviews(uid, 5); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out.BlockTotal, out.BlockDone, err = models.BlockTotals(uid); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if today := models.CurrentDate(); today != "" {
		out.TodayPlan, _ = models.DailyPlanByDate(uid, today)
	}
	if week := models.CurrentWeekStart(); week != "" {
		out.WeekReview, _ = models.WeeklyReviewByWeek(uid, week)
	}
	if month := models.CurrentMonth(); month != "" {
		out.MonthReview, _ = models.MonthlyReviewByMonth(uid, month)
	}
	c.JSON(http.StatusOK, out)
}

// ---------- 日清单 ----------

func (s *Server) apiDailyList(c *gin.Context) {
	out, err := models.DailyPlans(apiUID(c), 0)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiDailyCreate(c *gin.Context) {
	var body models.DailyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeDailyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	id, err := models.InsertDaily(apiUID(c), &body)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.DailyPlanByID(id, apiUID(c))
	c.JSON(http.StatusCreated, out)
}

func (s *Server) apiDailyGet(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.DailyPlanByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiDailyUpdate(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	old, err := models.DailyPlanByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if old == nil {
		apiNotFound(c)
		return
	}
	var body models.DailyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeDailyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	if err = models.UpdateDaily(id, apiUID(c), &body); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.DailyPlanByID(id, apiUID(c))
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiDailyDelete(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.DailyPlanByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	if err = models.DeleteDaily(id, apiUID(c)); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.Status(http.StatusNoContent)
}

// ---------- 周清单 ----------

func (s *Server) apiWeeklyList(c *gin.Context) {
	out, err := models.WeeklyReviews(apiUID(c), 0)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiWeeklyCreate(c *gin.Context) {
	var body models.WeeklyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeWeeklyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	id, err := models.InsertWeekly(apiUID(c), &body)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.WeeklyReviewByID(id, apiUID(c))
	c.JSON(http.StatusCreated, out)
}

func (s *Server) apiWeeklyGet(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.WeeklyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiWeeklyUpdate(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	old, err := models.WeeklyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if old == nil {
		apiNotFound(c)
		return
	}
	var body models.WeeklyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeWeeklyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	if err = models.UpdateWeekly(id, apiUID(c), &body); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.WeeklyReviewByID(id, apiUID(c))
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiWeeklyDelete(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.WeeklyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	if err = models.DeleteWeekly(id, apiUID(c)); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.Status(http.StatusNoContent)
}

// ---------- 月清单 ----------

func (s *Server) apiMonthlyList(c *gin.Context) {
	out, err := models.MonthlyReviews(apiUID(c), 0)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiMonthlyCreate(c *gin.Context) {
	var body models.MonthlyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeMonthlyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	id, err := models.InsertMonthly(apiUID(c), &body)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.MonthlyReviewByID(id, apiUID(c))
	c.JSON(http.StatusCreated, out)
}

func (s *Server) apiMonthlyGet(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.MonthlyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiMonthlyUpdate(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	old, err := models.MonthlyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if old == nil {
		apiNotFound(c)
		return
	}
	var body models.MonthlyIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeMonthlyIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	if err = models.UpdateMonthly(id, apiUID(c), &body); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out, _ := models.MonthlyReviewByID(id, apiUID(c))
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiMonthlyDelete(c *gin.Context) {
	id, ok := apiID(c)
	if !ok {
		apiNotFound(c)
		return
	}
	out, err := models.MonthlyReviewByID(id, apiUID(c))
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	if out == nil {
		apiNotFound(c)
		return
	}
	if err = models.DeleteMonthly(id, apiUID(c)); err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	c.Status(http.StatusNoContent)
}

// ---------- 用户管理（仅管理员） ----------

func (s *Server) apiUsersList(c *gin.Context) {
	if !isStaff(apiUser(c)) {
		apiFail(c, http.StatusForbidden, "仅管理员可操作")
		return
	}
	rows, err := models.Users()
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	out := make([]map[string]any, 0, len(rows))
	for _, r := range rows {
		out = append(out, models.UserOut(r))
	}
	c.JSON(http.StatusOK, out)
}

func (s *Server) apiUsersCreate(c *gin.Context) {
	if !isStaff(apiUser(c)) {
		apiFail(c, http.StatusForbidden, "仅管理员可操作")
		return
	}
	var body userIn
	if !decodeJSON(c, &body) {
		return
	}
	if msg := normalizeUserIn(&body); msg != "" {
		apiFail(c, http.StatusBadRequest, msg)
		return
	}
	if row, _ := models.UserByName(body.Username); row != nil {
		apiFail(c, http.StatusBadRequest, "用户名已存在")
		return
	}
	hash, err := database.HashPassword(body.Password)
	if err != nil {
		apiFail(c, http.StatusBadRequest, err.Error())
		return
	}
	uid, err := models.InsertUser(body.Username, hash, body.DisplayName, body.IsStaff)
	if err != nil {
		apiFail(c, http.StatusBadRequest, "用户名已存在")
		return
	}
	row, _ := models.UserByID(uid)
	c.JSON(http.StatusCreated, models.UserOut(row))
}
