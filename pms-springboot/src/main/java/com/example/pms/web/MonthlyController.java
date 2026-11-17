package com.example.pms.web;

import com.example.pms.domain.MonthlyReview;
import com.example.pms.domain.SystemHealthCheck;
import com.example.pms.repo.MonthlyRepository;
import com.example.pms.support.CurrentUser;
import com.example.pms.support.Views;
import com.example.pms.web.dto.MonthlyForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 每月重构清单页面：列表 / 新建 / 编辑 / 删除（写操作带 CSRF 表单）。 */
@Controller
@RequestMapping("/monthly")
public class MonthlyController {

    private final MonthlyRepository monthly;
    private final CurrentUser me;

    public MonthlyController(MonthlyRepository monthly, CurrentUser me) {
        this.monthly = monthly;
        this.me = me;
    }

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("reviews", monthly.findAll(me.id()));
        return "monthly/list";
    }

    @GetMapping("/new/")
    public String createForm(Model model) {
        LocalDate today = LocalDate.now();
        String month = String.format("%04d-%02d", today.getYear(), today.getMonthValue());
        model.addAttribute("form", MonthlyForm.blank(month));
        return formView(model, "填写每月重构清单", "/monthly/new/", "/monthly/");
    }

    @PostMapping("/new/")
    public String create(@ModelAttribute("form") MonthlyForm form, BindingResult errors, Model model) {
        validate(form, errors, 0);
        if (errors.hasErrors()) {
            return formView(model, "填写每月重构清单", "/monthly/new/", "/monthly/");
        }
        monthly.create(toMonthly(form, me.id()));
        return "redirect:/monthly/";
    }

    @GetMapping("/{id}/edit/")
    public String editForm(@PathVariable long id, Model model) {
        MonthlyReview m = monthly.findById(id, me.id()).orElse(null);
        if (m == null) return "redirect:/monthly/";
        model.addAttribute("form", toForm(m));
        return formView(model, "编辑每月重构清单", "/monthly/" + id + "/edit/", "/monthly/");
    }

    @PostMapping("/{id}/edit/")
    public String edit(@PathVariable long id, @ModelAttribute("form") MonthlyForm form,
                       BindingResult errors, Model model) {
        if (monthly.findById(id, me.id()).isEmpty()) return "redirect:/monthly/";
        validate(form, errors, id);
        if (errors.hasErrors()) {
            return formView(model, "编辑每月重构清单", "/monthly/" + id + "/edit/", "/monthly/");
        }
        MonthlyReview m = toMonthly(form, me.id());
        m.setId(id);
        monthly.update(m);
        return "redirect:/monthly/";
    }

    @PostMapping("/{id}/delete/")
    public String delete(@PathVariable long id) {
        monthly.delete(id, me.id());
        return "redirect:/monthly/";
    }

    private String formView(Model model, String title, String action, String cancelUrl) {
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        model.addAttribute("cancelUrl", cancelUrl);
        return "monthly/form";
    }

    private void validate(MonthlyForm form, BindingResult errors, long excludeId) {
        try {
            Views.parseMonth(form.getMonth());
        } catch (DateTimeParseException e) {
            errors.rejectValue("month", "invalid", "月份格式应为 YYYY-MM");
        }
        if (form.getMonth() != null && !form.getMonth().isBlank()
                && monthly.existsByMonth(me.id(), form.getMonth(), excludeId)) {
            errors.rejectValue("month", "duplicate", "该月份已有清单");
        }
        if (!Views.in(form.getWorthContinue(), "yes", "adjust", "stop")) {
            errors.rejectValue("worthContinue", "invalid", "价值判断值不合法");
        }
        for (MonthlyForm.HealthRow h : form.getHealthChecks()) {
            if (!Views.in(h.getModule(), "direction", "taskload", "blockquality",
                    "timebox", "sleepenergy", "reviewquality")) {
                errors.rejectValue("healthChecks", "invalid", "健康检查模块值不合法");
                continue;
            }
            if (!h.getScore().isBlank()) {
                try {
                    int score = Integer.parseInt(h.getScore().trim());
                    if (score < 1 || score > 5) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    errors.rejectValue("healthChecks", "invalid", "自评分应为 1-5");
                }
            }
        }
    }

    private MonthlyReview toMonthly(MonthlyForm f, long uid) {
        MonthlyReview m = new MonthlyReview();
        m.setUserId(uid);
        m.setMonth(f.getMonth());
        m.setTopAchievement(f.getTopAchievement());
        m.setWorthContinue(f.getWorthContinue());
        m.setWorthReason(f.getWorthReason());
        m.setMostValuableInput(f.getMostValuableInput());
        m.setBiggestOpportunityCost(f.getBiggestOpportunityCost());
        m.setKeepItem(f.getKeepItem());
        m.setCutItem(f.getCutItem());
        m.setExperimentItem(f.getExperimentItem());
        m.setQSkillPortfolio(f.isQSkillPortfolio());
        m.setQCareerAlignment(f.isQCareerAlignment());
        m.setQLifeAutonomy(f.isQLifeAutonomy());
        m.setQRecoveryRelations(f.isQRecoveryRelations());
        m.setQLongtermDecision(f.isQLongtermDecision());
        for (MonthlyForm.HealthRow r : f.getHealthChecks()) {
            SystemHealthCheck h = new SystemHealthCheck();
            h.setModule(r.getModule());
            h.setScore(r.getScore().isBlank() ? null : Integer.parseInt(r.getScore().trim()));
            h.setNeededChange(r.getNeededChange());
            m.getHealthChecks().add(h);
        }
        return m;
    }

    private MonthlyForm toForm(MonthlyReview m) {
        MonthlyForm f = new MonthlyForm();
        f.setMonth(m.getMonth());
        f.setTopAchievement(m.getTopAchievement());
        f.setWorthContinue(m.getWorthContinue());
        f.setWorthReason(m.getWorthReason());
        f.setMostValuableInput(m.getMostValuableInput());
        f.setBiggestOpportunityCost(m.getBiggestOpportunityCost());
        f.setKeepItem(m.getKeepItem());
        f.setCutItem(m.getCutItem());
        f.setExperimentItem(m.getExperimentItem());
        f.setQSkillPortfolio(m.isQSkillPortfolio());
        f.setQCareerAlignment(m.isQCareerAlignment());
        f.setQLifeAutonomy(m.isQLifeAutonomy());
        f.setQRecoveryRelations(m.isQRecoveryRelations());
        f.setQLongtermDecision(m.isQLongtermDecision());
        for (SystemHealthCheck h : m.getHealthChecks()) {
            MonthlyForm.HealthRow r = new MonthlyForm.HealthRow();
            r.setModule(h.getModule());
            r.setScore(h.getScore() == null ? "" : String.valueOf(h.getScore()));
            r.setNeededChange(h.getNeededChange());
            f.getHealthChecks().add(r);
        }
        for (String key : MonthlyForm.MODULE_KEYS) {
            if (f.getHealthChecks().stream().noneMatch(r -> key.equals(r.getModule()))) {
                MonthlyForm.HealthRow r = new MonthlyForm.HealthRow();
                r.setModule(key);
                f.getHealthChecks().add(r);
            }
        }
        return f;
    }
}
