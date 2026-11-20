package com.example.pms.web;

import com.example.pms.domain.DailyPlan;
import com.example.pms.domain.TimeBlock;
import com.example.pms.repo.DailyRepository;
import com.example.pms.support.CurrentUser;
import com.example.pms.support.Views;
import com.example.pms.web.dto.DailyForm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 每日执行清单页面：列表 / 新建 / 编辑 / 删除（写操作带 CSRF 表单）。 */
@Controller
@RequestMapping("/daily")
public class DailyController {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final DailyRepository daily;
    private final CurrentUser me;

    public DailyController(DailyRepository daily, CurrentUser me) {
        this.daily = daily;
        this.me = me;
    }

    @GetMapping("/")
    public String list(Model model) {
        model.addAttribute("plans", daily.findAll(me.id()));
        return Views.page("daily/list");
    }

    @GetMapping("/new/")
    public String createForm(Model model) {
        model.addAttribute("form", DailyForm.blank(LocalDate.now().toString()));
        return formView(model, "填写今日执行清单", "/daily/new/", "/daily/");
    }

    @PostMapping("/new/")
    public String create(@ModelAttribute("form") DailyForm form, BindingResult errors, Model model) {
        validate(form, errors, 0);
        if (errors.hasErrors()) {
            return formView(model, "填写今日执行清单", "/daily/new/", "/daily/");
        }
        daily.create(toPlan(form, me.id()));
        return "redirect:/daily/";
    }

    @GetMapping("/{id}/edit/")
    public String editForm(@PathVariable long id, Model model) {
        DailyPlan p = daily.findById(id, me.id()).orElse(null);
        if (p == null) return "redirect:/daily/";
        model.addAttribute("form", toForm(p));
        return formView(model, "编辑每日执行清单", "/daily/" + id + "/edit/", "/daily/");
    }

    @PostMapping("/{id}/edit/")
    public String edit(@PathVariable long id, @ModelAttribute("form") DailyForm form,
                       BindingResult errors, Model model) {
        if (daily.findById(id, me.id()).isEmpty()) return "redirect:/daily/";
        validate(form, errors, id);
        if (errors.hasErrors()) {
            return formView(model, "编辑每日执行清单", "/daily/" + id + "/edit/", "/daily/");
        }
        DailyPlan p = toPlan(form, me.id());
        p.setId(id);
        daily.update(p);
        return "redirect:/daily/";
    }

    @PostMapping("/{id}/delete/")
    public String delete(@PathVariable long id) {
        daily.delete(id, me.id());
        return "redirect:/daily/";
    }

    private String formView(Model model, String title, String action, String cancelUrl) {
        model.addAttribute("title", title);
        model.addAttribute("action", action);
        model.addAttribute("cancelUrl", cancelUrl);
        return Views.page("daily/form");
    }

    private void validate(DailyForm form, BindingResult errors, long excludeId) {
        try {
            Views.parseDate(form.getPlanDate());
        } catch (DateTimeParseException e) {
            errors.rejectValue("planDate", "invalid", "日期格式应为 YYYY-MM-DD");
        }
        if (form.getPlanDate() != null && !form.getPlanDate().isBlank()
                && daily.existsByDate(me.id(), form.getPlanDate(), excludeId)) {
            errors.rejectValue("planDate", "duplicate", "该日期已有清单");
        }
        if (!Views.in(form.getEnergyLevel(), "low", "medium", "high")) {
            errors.rejectValue("energyLevel", "invalid", "精力值不合法");
        }
        if (!form.getSleepHours().isBlank()) {
            try {
                new BigDecimal(form.getSleepHours().trim());
            } catch (NumberFormatException e) {
                errors.rejectValue("sleepHours", "invalid", "睡眠小时应为数字");
            }
        }
        for (DailyForm.BlockRow r : form.getBlocks()) {
            checkTime(errors, r.getTimeStart());
            checkTime(errors, r.getTimeEnd());
        }
    }

    private void checkTime(BindingResult errors, String value) {
        if (value == null || value.isBlank()) return;
        try {
            LocalTime.parse(value.trim(), HHMM);
        } catch (DateTimeParseException e) {
            errors.rejectValue("planDate", "invalid", "时间格式应为 HH:MM");
        }
    }

    private DailyPlan toPlan(DailyForm f, long uid) {
        DailyPlan p = new DailyPlan();
        p.setUserId(uid);
        p.setPlanDate(f.getPlanDate());
        p.setSleepHours(f.getSleepHours().isBlank() ? null : new BigDecimal(f.getSleepHours().trim()));
        p.setEnergyLevel(f.getEnergyLevel());
        p.setMit(f.getMit());
        p.setPreKnowOutput(f.isPreKnowOutput());
        p.setPreMaterialsReady(f.isPreMaterialsReady());
        p.setPrePhoneHandled(f.isPrePhoneHandled());
        p.setDuringOneBlock(f.isDuringOneBlock());
        p.setDuringShrinkStep(f.isDuringShrinkStep());
        p.setDuringRecordDistraction(f.isDuringRecordDistraction());
        p.setPostVisibleOutput(f.isPostVisibleOutput());
        p.setPostRecall(f.isPostRecall());
        p.setPostNextStep(f.isPostNextStep());
        p.setLifeWaterFood(f.isLifeWaterFood());
        p.setLifeWalkExercise(f.isLifeWalkExercise());
        p.setLifeCleanTidy(f.isLifeCleanTidy());
        p.setLifeSocialOutdoor(f.isLifeSocialOutdoor());
        p.setReviewAccomplished(f.getReviewAccomplished());
        p.setReviewResistance(f.getReviewResistance());
        p.setReviewTomorrowStart(f.getReviewTomorrowStart());
        p.setReviewGratitude(f.getReviewGratitude());
        for (DailyForm.BlockRow r : f.getBlocks()) {
            if (r.getBlockName() == null || r.getBlockName().isBlank()) continue;
            TimeBlock b = new TimeBlock();
            b.setSeq(r.getSeq());
            b.setBlockName(r.getBlockName().trim());
            b.setInputDesc(r.getInputDesc());
            b.setKeyAction(r.getKeyAction());
            b.setDeliverable(r.getDeliverable());
            b.setTimeStart(blankToNull(r.getTimeStart()));
            b.setTimeEnd(blankToNull(r.getTimeEnd()));
            b.setDone(r.isDone());
            p.getBlocks().add(b);
        }
        return p;
    }

    private DailyForm toForm(DailyPlan p) {
        DailyForm f = new DailyForm();
        f.setPlanDate(p.getPlanDate());
        f.setSleepHours(p.getSleepHours() == null ? "" : p.getSleepHours().toPlainString());
        f.setEnergyLevel(p.getEnergyLevel());
        f.setMit(p.getMit());
        f.setPreKnowOutput(p.isPreKnowOutput());
        f.setPreMaterialsReady(p.isPreMaterialsReady());
        f.setPrePhoneHandled(p.isPrePhoneHandled());
        f.setDuringOneBlock(p.isDuringOneBlock());
        f.setDuringShrinkStep(p.isDuringShrinkStep());
        f.setDuringRecordDistraction(p.isDuringRecordDistraction());
        f.setPostVisibleOutput(p.isPostVisibleOutput());
        f.setPostRecall(p.isPostRecall());
        f.setPostNextStep(p.isPostNextStep());
        f.setLifeWaterFood(p.isLifeWaterFood());
        f.setLifeWalkExercise(p.isLifeWalkExercise());
        f.setLifeCleanTidy(p.isLifeCleanTidy());
        f.setLifeSocialOutdoor(p.isLifeSocialOutdoor());
        f.setReviewAccomplished(p.getReviewAccomplished());
        f.setReviewResistance(p.getReviewResistance());
        f.setReviewTomorrowStart(p.getReviewTomorrowStart());
        f.setReviewGratitude(p.getReviewGratitude());
        int i = 1;
        for (TimeBlock b : p.getBlocks()) {
            DailyForm.BlockRow r = new DailyForm.BlockRow();
            r.setSeq(i++);
            r.setBlockName(b.getBlockName());
            r.setInputDesc(b.getInputDesc());
            r.setKeyAction(b.getKeyAction());
            r.setDeliverable(b.getDeliverable());
            r.setTimeStart(b.getTimeStart() == null ? "" : b.getTimeStart());
            r.setTimeEnd(b.getTimeEnd() == null ? "" : b.getTimeEnd());
            r.setDone(b.isDone());
            f.getBlocks().add(r);
        }
        while (f.getBlocks().size() < 4) {
            DailyForm.BlockRow r = new DailyForm.BlockRow();
            r.setSeq(f.getBlocks().size() + 1);
            f.getBlocks().add(r);
        }
        return f;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
