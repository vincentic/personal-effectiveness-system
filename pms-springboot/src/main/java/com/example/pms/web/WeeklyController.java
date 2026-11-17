package com.example.pms.web;

import com.example.pms.domain.WeeklyDomainReview;
import com.example.pms.domain.WeeklyReview;
import com.example.pms.domain.WeeklyTaskFlow;
import com.example.pms.repo.WeeklyRepository;
import com.example.pms.support.CurrentUser;
import com.example.pms.support.Views;
import com.example.pms.web.dto.WeeklyForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

/** 每周复盘清单页面：列表 / 新建 / 编辑 / 删除（写操作带 CSRF 表单）。 */
@Controller
@RequestMapping("/weekly")
public class WeeklyController {

    private final WeeklyRepository weekly;
    private final CurrentUser me;

    public WeeklyController(WeeklyRepository weekly, CurrentUser me) {
        this.weekly = weekly;
        this.me = me;
    }

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("reviews", weekly.findAll(me.id()));
        return "weekly/list";
    }

    @GetMapping("/new/")
    public String createForm(Model model) {
        LocalDate today = LocalDate.now();
        String monday = today.minusDays(today.getDayOfWeek().getValue() - 1L).toString();
        model.addAttribute("form", WeeklyForm.blank(monday));
        return formView(model, "填写每周复盘清单", "/weekly/new/", "/weekly/");
    }

    @PostMapping("/new/")
    public String create(@ModelAttribute("form") WeeklyForm form, BindingResult errors, Model model) {
        validate(form, errors, 0);
        if (errors.hasErrors()) {
            return formView(model, "填写每周复盘清单", "/weekly/new/", "/weekly/");
        }
        weekly.create(toWeekly(form, me.id()));
        return "redirect:/weekly/";
    }

    @GetMapping("/{id}/edit/")
    public String editForm(@PathVariable long id, Model model) {
        WeeklyReview w = weekly.findById(id, me.id()).orElse(null);
        if (w == null) return "redirect:/weekly/";
        model.addAttribute("form", toForm(w));
        return formView(model, "编辑每周复盘清单", "/weekly/" + id + "/edit/", "/weekly/");
    }

    @PostMapping("/{id}/edit/")
    public String edit(@PathVariable long id, @ModelAttribute("form") WeeklyForm form,
                       BindingResult errors, Model model) {
        if (weekly.findById(id, me.id()).isEmpty()) return "redirect:/weekly/";
        validate(form, errors, id);
        if (errors.hasErrors()) {
            return formView(model, "编辑每周复盘清单", "/weekly/" + id + "/edit/", "/weekly/");
        }
        WeeklyReview w = toWeekly(form, me.id());
        w.setId(id);
        weekly.update(w);
        return "redirect:/weekly/";
    }

    @PostMapping("/{id}/delete/")
    public String delete(@PathVariable long id) {
        weekly.delete(id, me.id());
        return "redirect:/weekly/";
    }

    private String formView(Model model, String title, String action, String cancelUrl) {
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        model.addAttribute("cancelUrl", cancelUrl);
        return "weekly/form";
    }

    private void validate(WeeklyForm form, BindingResult errors, long excludeId) {
        try {
            Views.parseDate(form.getWeekStart());
        } catch (DateTimeParseException e) {
            errors.rejectValue("weekStart", "invalid", "日期格式应为 YYYY-MM-DD");
        }
        if (form.getWeekStart() != null && !form.getWeekStart().isBlank()
                && weekly.existsByWeek(me.id(), form.getWeekStart(), excludeId)) {
            errors.rejectValue("weekStart", "duplicate", "该周已有清单");
        }
        for (WeeklyForm.DomainRow d : form.getDomains()) {
            if (!Views.in(d.getDomain(), "work", "study", "body", "relations")) {
                errors.rejectValue("domains", "invalid", "领域值不合法");
            }
            if (!Views.in(d.getStatus(), "smooth", "blocked", "paused")) {
                errors.rejectValue("domains", "invalid", "领域状态值不合法");
            }
        }
        for (WeeklyForm.FlowRow f : form.getFlows()) {
            if (!Views.in(f.getBucket(), "todo", "doing", "done", "remove")) {
                errors.rejectValue("flows", "invalid", "任务流分栏值不合法");
            }
        }
    }

    private WeeklyReview toWeekly(WeeklyForm f, long uid) {
        WeeklyReview w = new WeeklyReview();
        w.setUserId(uid);
        w.setWeekStart(f.getWeekStart());
        w.setNextMit1(f.getNextMit1());
        w.setNextMit2(f.getNextMit2());
        w.setNextMit3(f.getNextMit3());
        w.setDeepWorkTime(f.getDeepWorkTime());
        w.setPrepEnvMaterials(f.getPrepEnvMaterials());
        w.setIfCondition(f.getIfCondition());
        w.setThenAction(f.getThenAction());
        w.setQSkilledBlock(f.isQSkilledBlock());
        w.setQLowValueTime(f.isQLowValueTime());
        w.setQEstimateGap(f.isQEstimateGap());
        w.setQRestConnection(f.isQRestConnection());
        w.setQNextAdjust(f.isQNextAdjust());
        for (WeeklyForm.DomainRow r : f.getDomains()) {
            WeeklyDomainReview d = new WeeklyDomainReview();
            d.setDomain(r.getDomain());
            d.setFacts(r.getFacts());
            d.setStatus(r.getStatus());
            w.getDomains().add(d);
        }
        for (WeeklyForm.FlowRow r : f.getFlows()) {
            if (r.getContent() == null || r.getContent().isBlank()) continue;
            WeeklyTaskFlow t = new WeeklyTaskFlow();
            t.setBucket(r.getBucket());
            t.setContent(r.getContent().trim());
            t.setSeq(r.getSeq());
            w.getFlows().add(t);
        }
        return w;
    }

    private WeeklyForm toForm(WeeklyReview w) {
        WeeklyForm f = new WeeklyForm();
        f.setWeekStart(w.getWeekStart());
        f.setNextMit1(w.getNextMit1());
        f.setNextMit2(w.getNextMit2());
        f.setNextMit3(w.getNextMit3());
        f.setDeepWorkTime(w.getDeepWorkTime());
        f.setPrepEnvMaterials(w.getPrepEnvMaterials());
        f.setIfCondition(w.getIfCondition());
        f.setThenAction(w.getThenAction());
        f.setQSkilledBlock(w.isQSkilledBlock());
        f.setQLowValueTime(w.isQLowValueTime());
        f.setQEstimateGap(w.isQEstimateGap());
        f.setQRestConnection(w.isQRestConnection());
        f.setQNextAdjust(w.isQNextAdjust());
        for (WeeklyDomainReview d : w.getDomains()) {
            WeeklyForm.DomainRow r = new WeeklyForm.DomainRow();
            r.setDomain(d.getDomain());
            r.setFacts(d.getFacts());
            r.setStatus(d.getStatus());
            f.getDomains().add(r);
        }
        for (String key : WeeklyForm.DOMAIN_KEYS) {
            if (f.getDomains().stream().noneMatch(r -> key.equals(r.getDomain()))) {
                WeeklyForm.DomainRow r = new WeeklyForm.DomainRow();
                r.setDomain(key);
                f.getDomains().add(r);
            }
        }
        for (WeeklyTaskFlow t : w.getFlows()) {
            WeeklyForm.FlowRow r = new WeeklyForm.FlowRow();
            r.setBucket(t.getBucket());
            r.setContent(t.getContent());
            r.setSeq(t.getSeq());
            f.getFlows().add(r);
        }
        while (f.getFlows().size() < 8) {
            WeeklyForm.FlowRow r = new WeeklyForm.FlowRow();
            r.setSeq(f.getFlows().size() + 1);
            r.setBucket("todo");
            f.getFlows().add(r);
        }
        return f;
    }
}
