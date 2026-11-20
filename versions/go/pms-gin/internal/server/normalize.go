package server

import (
	"strconv"
	"strings"

	"pms-gin/internal/models"
)

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
			continue
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
