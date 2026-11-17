package com.example.pms.web;

import com.example.pms.domain.DailyPlan;
import com.example.pms.repo.DailyRepository;
import com.example.pms.repo.MonthlyRepository;
import com.example.pms.repo.WeeklyRepository;
import com.example.pms.support.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

/** 工作台页面：效能系统概览 + 今日 / 本周 / 本月入口。 */
@Controller
public class DashboardController {

    private final DailyRepository daily;
    private final WeeklyRepository weekly;
    private final MonthlyRepository monthly;
    private final CurrentUser me;

    public DashboardController(DailyRepository daily, WeeklyRepository weekly,
                               MonthlyRepository monthly, CurrentUser me) {
        this.daily = daily;
        this.weekly = weekly;
        this.monthly = monthly;
        this.me = me;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        long uid = me.id();
        LocalDate today = LocalDate.now();
        String weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L).toString();
        String month = String.format("%04d-%02d", today.getYear(), today.getMonthValue());

        List<DailyPlan> lastWeek = daily.findRecent(uid, 7);
        model.addAttribute("today", today.toString());
        model.addAttribute("todayPlan", daily.findByDate(uid, today.toString()).orElse(null));
        model.addAttribute("weekReview", weekly.findByWeek(uid, weekStart).orElse(null));
        model.addAttribute("monthReview", monthly.findByMonth(uid, month).orElse(null));
        model.addAttribute("dailyCount", daily.count(uid));
        model.addAttribute("weeklyCount", weekly.count(uid));
        model.addAttribute("monthlyCount", monthly.count(uid));
        model.addAttribute("recentDaily", daily.findRecent(uid, 5));
        model.addAttribute("recentWeekly", weekly.findRecent(uid, 5));
        model.addAttribute("recentMonthly", monthly.findRecent(uid, 5));
        model.addAttribute("blockTotal", lastWeek.stream().mapToInt(DailyPlan::getBlocksTotal).sum());
        model.addAttribute("blockDone", lastWeek.stream().mapToInt(DailyPlan::getBlocksDone).sum());
        return "dashboard";
    }
}
