package com.example.pms.web;

import com.example.pms.domain.*;
import com.example.pms.repo.DailyRepository;
import com.example.pms.repo.MonthlyRepository;
import com.example.pms.repo.UserRepository;
import com.example.pms.repo.WeeklyRepository;
import com.example.pms.support.ApiToken;
import com.example.pms.support.Views;
import com.example.pms.web.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 与 Gin / FastAPI 版共用 db/API.md 契约的 REST API（v2 个人效能系统）。
 * 页面仍走 Session + CSRF；/api/** 走 HMAC Token 并豁免 CSRF。
 */
@RestController
@RequestMapping("/api")
public class ApiController {

    private static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");

    private final UserRepository users;
    private final DailyRepository daily;
    private final WeeklyRepository weekly;
    private final MonthlyRepository monthly;
    private final ApiToken tokens;
    private final PasswordEncoder encoder;

    public ApiController(UserRepository users, DailyRepository daily, WeeklyRepository weekly,
                         MonthlyRepository monthly, ApiToken tokens, PasswordEncoder encoder) {
        this.users = users;
        this.daily = daily;
        this.weekly = weekly;
        this.monthly = monthly;
        this.tokens = tokens;
        this.encoder = encoder;
    }

    // ------------------------------------------------------------------ 认证 / 用户

    @PostMapping("/auth/login")
    public ResponseEntity<Object> login(@RequestBody(required = false) LoginIn body) {
        String username = body == null ? "" : body.u();
        String password = body == null ? "" : body.p();
        if (username.isEmpty() || password.isEmpty()) {
            return err(400, "用户名和密码不能为空");
        }
        return users.findByUsername(username)
                .filter(u -> encoder.matches(password, u.getPasswordHash()))
                .<ResponseEntity<Object>>map(u -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("token", tokens.issue(u.getId(), u.getUsername(), u.isStaff()));
                    out.put("user", users.findRowById(u.getId()).map(ApiController::userMap).orElse(Map.of()));
                    return ResponseEntity.ok(out);
                })
                .orElseGet(() -> err(401, "用户名或密码错误"));
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(HttpServletRequest request) {
        User u = require(request);
        return u == null ? unauthorized()
                : ResponseEntity.ok(users.findRowById(u.getId()).map(ApiController::userMap).orElse(Map.of()));
    }

    @GetMapping("/users")
    public ResponseEntity<Object> listUsers(HttpServletRequest request) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (!u.isStaff()) return err(403, "仅管理员可操作");
        return ResponseEntity.ok(users.findAllRows().stream().map(ApiController::userMap).toList());
    }

    @PostMapping("/users")
    public ResponseEntity<Object> createUser(HttpServletRequest request,
                                             @RequestBody(required = false) UserIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (!u.isStaff()) return err(403, "仅管理员可操作");
        if (body == null) return err(400, "用户名和密码不能为空");
        String username = body.u();
        String password = body.p();
        if (username.isEmpty() || password.isEmpty()) return err(400, "用户名和密码不能为空");
        if (username.length() > 150) return err(400, "用户名不能超过 150 字");
        if (password.length() < 8 || password.length() > 72) return err(400, "密码长度需为 8-72 位");
        if (users.existsByUsername(username)) return err(400, "用户名已存在");
        users.create(username, encoder.encode(password), body.dn(), body.isStaff());
        return ResponseEntity.status(201)
                .body(users.findRowByUsername(username).map(ApiController::userMap).orElse(Map.of()));
    }

    // ------------------------------------------------------------------ 工作台

    @GetMapping("/dashboard")
    public ResponseEntity<Object> dashboard(HttpServletRequest request) {
        User u = require(request);
        if (u == null) return unauthorized();
        long uid = u.getId();
        LocalDate today = LocalDate.now();
        String weekStart = today.minusDays(today.getDayOfWeek().getValue() - 1L).toString();
        String month = String.format("%04d-%02d", today.getYear(), today.getMonthValue());

        List<DailyPlan> lastWeek = daily.findRecent(uid, 7);
        int blockTotal = lastWeek.stream().mapToInt(DailyPlan::getBlocksTotal).sum();
        int blockDone = lastWeek.stream().mapToInt(DailyPlan::getBlocksDone).sum();

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("daily_count", daily.count(uid));
        out.put("weekly_count", weekly.count(uid));
        out.put("monthly_count", monthly.count(uid));
        out.put("today_plan", daily.findByDate(uid, today.toString()).orElse(null));
        out.put("week_review", weekly.findByWeek(uid, weekStart).orElse(null));
        out.put("month_review", monthly.findByMonth(uid, month).orElse(null));
        out.put("recent_daily", daily.findRecent(uid, 5));
        out.put("recent_weekly", weekly.findRecent(uid, 5));
        out.put("recent_monthly", monthly.findRecent(uid, 5));
        out.put("block_total", blockTotal);
        out.put("block_done", blockDone);
        return ResponseEntity.ok(out);
    }

    // ------------------------------------------------------------------ 每日执行清单

    @GetMapping("/daily")
    public ResponseEntity<Object> listDaily(HttpServletRequest request) {
        User u = require(request);
        return u == null ? unauthorized() : ResponseEntity.ok(daily.findAll(u.getId()));
    }

    @PostMapping("/daily")
    public ResponseEntity<Object> createDaily(HttpServletRequest request,
                                              @RequestBody(required = false) DailyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        DailyPlan p;
        try {
            p = toDaily(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (daily.existsByDate(u.getId(), p.getPlanDate(), 0)) {
            return err(400, "记录已存在");
        }
        long id = daily.create(p);
        return daily.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity.status(201)::body)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @GetMapping("/daily/{id}")
    public ResponseEntity<Object> getDaily(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        return daily.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @PutMapping("/daily/{id}")
    public ResponseEntity<Object> updateDaily(HttpServletRequest request, @PathVariable long id,
                                              @RequestBody(required = false) DailyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (daily.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        DailyPlan p;
        try {
            p = toDaily(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (daily.existsByDate(u.getId(), p.getPlanDate(), id)) return err(400, "记录已存在");
        p.setId(id);
        daily.update(p);
        return daily.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @DeleteMapping("/daily/{id}")
    public ResponseEntity<Object> deleteDaily(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (daily.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        daily.delete(id, u.getId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 每周复盘清单

    @GetMapping("/weekly")
    public ResponseEntity<Object> listWeekly(HttpServletRequest request) {
        User u = require(request);
        return u == null ? unauthorized() : ResponseEntity.ok(weekly.findAll(u.getId()));
    }

    @PostMapping("/weekly")
    public ResponseEntity<Object> createWeekly(HttpServletRequest request,
                                               @RequestBody(required = false) WeeklyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        WeeklyReview w;
        try {
            w = toWeekly(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (weekly.existsByWeek(u.getId(), w.getWeekStart(), 0)) return err(400, "记录已存在");
        long id = weekly.create(w);
        return weekly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity.status(201)::body)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @GetMapping("/weekly/{id}")
    public ResponseEntity<Object> getWeekly(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        return weekly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @PutMapping("/weekly/{id}")
    public ResponseEntity<Object> updateWeekly(HttpServletRequest request, @PathVariable long id,
                                                @RequestBody(required = false) WeeklyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (weekly.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        WeeklyReview w;
        try {
            w = toWeekly(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (weekly.existsByWeek(u.getId(), w.getWeekStart(), id)) return err(400, "记录已存在");
        w.setId(id);
        weekly.update(w);
        return weekly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @DeleteMapping("/weekly/{id}")
    public ResponseEntity<Object> deleteWeekly(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (weekly.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        weekly.delete(id, u.getId());
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 每月重构清单

    @GetMapping("/monthly")
    public ResponseEntity<Object> listMonthly(HttpServletRequest request) {
        User u = require(request);
        return u == null ? unauthorized() : ResponseEntity.ok(monthly.findAll(u.getId()));
    }

    @PostMapping("/monthly")
    public ResponseEntity<Object> createMonthly(HttpServletRequest request,
                                                @RequestBody(required = false) MonthlyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        MonthlyReview m;
        try {
            m = toMonthly(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (monthly.existsByMonth(u.getId(), m.getMonth(), 0)) return err(400, "记录已存在");
        long id = monthly.create(m);
        return monthly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity.status(201)::body)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @GetMapping("/monthly/{id}")
    public ResponseEntity<Object> getMonthly(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        return monthly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @PutMapping("/monthly/{id}")
    public ResponseEntity<Object> updateMonthly(HttpServletRequest request, @PathVariable long id,
                                                 @RequestBody(required = false) MonthlyIn body) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (monthly.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        MonthlyReview m;
        try {
            m = toMonthly(body, u.getId());
        } catch (IllegalArgumentException e) {
            return err(400, e.getMessage());
        }
        if (monthly.existsByMonth(u.getId(), m.getMonth(), id)) return err(400, "记录已存在");
        m.setId(id);
        monthly.update(m);
        return monthly.findById(id, u.getId())
                .<ResponseEntity<Object>>map(ResponseEntity::ok)
                .orElseGet(() -> err(404, "记录不存在"));
    }

    @DeleteMapping("/monthly/{id}")
    public ResponseEntity<Object> deleteMonthly(HttpServletRequest request, @PathVariable long id) {
        User u = require(request);
        if (u == null) return unauthorized();
        if (monthly.findById(id, u.getId()).isEmpty()) return err(404, "记录不存在");
        monthly.delete(id, u.getId());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Object> badJson() {
        return err(400, "请求体不是合法的 JSON 或字段类型错误");
    }

    // ------------------------------------------------------------------ In DTO → 实体

    private static DailyPlan toDaily(DailyIn in, long uid) {
        if (in == null) throw new IllegalArgumentException("请求体不能为空");
        DailyPlan p = new DailyPlan();
        p.setUserId(uid);
        p.setPlanDate(requireDate(in.planDate()));
        p.setSleepHours(in.sleepHours());
        String energy = in.energyLevel() == null || in.energyLevel().isBlank() ? "medium" : in.energyLevel().trim();
        if (!Views.in(energy, "low", "medium", "high")) throw new IllegalArgumentException("精力值非法");
        p.setEnergyLevel(energy);
        p.setMit(nvl(in.mit()));
        p.setPreKnowOutput(in.preKnowOutput());
        p.setPreMaterialsReady(in.preMaterialsReady());
        p.setPrePhoneHandled(in.prePhoneHandled());
        p.setDuringOneBlock(in.duringOneBlock());
        p.setDuringShrinkStep(in.duringShrinkStep());
        p.setDuringRecordDistraction(in.duringRecordDistraction());
        p.setPostVisibleOutput(in.postVisibleOutput());
        p.setPostRecall(in.postRecall());
        p.setPostNextStep(in.postNextStep());
        p.setLifeWaterFood(in.lifeWaterFood());
        p.setLifeWalkExercise(in.lifeWalkExercise());
        p.setLifeCleanTidy(in.lifeCleanTidy());
        p.setLifeSocialOutdoor(in.lifeSocialOutdoor());
        p.setReviewAccomplished(nvl(in.reviewAccomplished()));
        p.setReviewResistance(nvl(in.reviewResistance()));
        p.setReviewTomorrowStart(nvl(in.reviewTomorrowStart()));
        p.setReviewGratitude(nvl(in.reviewGratitude()));

        List<TimeBlock> blocks = new ArrayList<>();
        for (BlockIn b : in.safeBlocks()) {
            String name = b.bn();
            if (name.isEmpty()) throw new IllegalArgumentException("组块名称不能为空");
            if (name.length() > 200) throw new IllegalArgumentException("组块名称不能超过 200 字");
            TimeBlock t = new TimeBlock();
            t.setSeq(b.seq());
            t.setBlockName(name);
            t.setInputDesc(b.in());
            t.setKeyAction(b.ka());
            t.setDeliverable(b.de());
            t.setTimeStart(hhmm(b.ts()));
            t.setTimeEnd(hhmm(b.te()));
            t.setDone(b.isDone());
            blocks.add(t);
        }
        p.setBlocks(blocks);
        return p;
    }

    private static WeeklyReview toWeekly(WeeklyIn in, long uid) {
        if (in == null) throw new IllegalArgumentException("请求体不能为空");
        WeeklyReview w = new WeeklyReview();
        w.setUserId(uid);
        w.setWeekStart(requireDate(in.weekStart()));
        w.setNextMit1(nvl(in.nextMit1()));
        w.setNextMit2(nvl(in.nextMit2()));
        w.setNextMit3(nvl(in.nextMit3()));
        w.setDeepWorkTime(nvl(in.deepWorkTime()));
        w.setPrepEnvMaterials(nvl(in.prepEnvMaterials()));
        w.setIfCondition(nvl(in.ifCondition()));
        w.setThenAction(nvl(in.thenAction()));
        w.setQSkilledBlock(in.qSkilledBlock());
        w.setQLowValueTime(in.qLowValueTime());
        w.setQEstimateGap(in.qEstimateGap());
        w.setQRestConnection(in.qRestConnection());
        w.setQNextAdjust(in.qNextAdjust());

        List<WeeklyDomainReview> domains = new ArrayList<>();
        for (DomainIn d : in.safeDomains()) {
            if (!Views.in(d.domain(), "work", "study", "body", "relations")) {
                throw new IllegalArgumentException("领域值非法");
            }
            if (!Views.in(d.st(), "smooth", "blocked", "paused")) {
                throw new IllegalArgumentException("领域状态值非法");
            }
            if (domains.stream().anyMatch(x -> x.getDomain().equals(d.domain()))) {
                throw new IllegalArgumentException("领域不能重复");
            }
            WeeklyDomainReview row = new WeeklyDomainReview();
            row.setDomain(d.domain());
            row.setFacts(d.fa());
            row.setStatus(d.st());
            domains.add(row);
        }
        w.setDomains(domains);

        List<WeeklyTaskFlow> flows = new ArrayList<>();
        for (FlowIn f : in.safeFlows()) {
            if (!Views.in(f.bu(), "todo", "doing", "done", "remove")) {
                throw new IllegalArgumentException("任务流分栏值非法");
            }
            WeeklyTaskFlow row = new WeeklyTaskFlow();
            row.setBucket(f.bu());
            row.setContent(f.co());
            row.setSeq(f.seq());
            flows.add(row);
        }
        w.setFlows(flows);
        return w;
    }

    private static MonthlyReview toMonthly(MonthlyIn in, long uid) {
        if (in == null) throw new IllegalArgumentException("请求体不能为空");
        MonthlyReview m = new MonthlyReview();
        m.setUserId(uid);
        m.setMonth(requireMonth(in.month()));
        m.setTopAchievement(nvl(in.topAchievement()));
        String worth = in.wc();
        if (!Views.in(worth, "yes", "adjust", "stop")) throw new IllegalArgumentException("价值判断值非法");
        m.setWorthContinue(worth);
        m.setWorthReason(nvl(in.worthReason()));
        m.setMostValuableInput(nvl(in.mostValuableInput()));
        m.setBiggestOpportunityCost(nvl(in.biggestOpportunityCost()));
        m.setKeepItem(nvl(in.keepItem()));
        m.setCutItem(nvl(in.cutItem()));
        m.setExperimentItem(nvl(in.experimentItem()));
        m.setQSkillPortfolio(in.qSkillPortfolio());
        m.setQCareerAlignment(in.qCareerAlignment());
        m.setQLifeAutonomy(in.qLifeAutonomy());
        m.setQRecoveryRelations(in.qRecoveryRelations());
        m.setQLongtermDecision(in.qLongtermDecision());

        List<SystemHealthCheck> checks = new ArrayList<>();
        for (HealthIn h : in.safeHealthChecks()) {
            if (!Views.in(h.module(), "direction", "taskload", "blockquality",
                    "timebox", "sleepenergy", "reviewquality")) {
                throw new IllegalArgumentException("健康检查模块值非法");
            }
            if (checks.stream().anyMatch(x -> x.getModule().equals(h.module()))) {
                throw new IllegalArgumentException("健康检查模块不能重复");
            }
            Integer score = h.score();
            if (score != null && (score < 1 || score > 5)) {
                throw new IllegalArgumentException("自评分应为 1-5");
            }
            SystemHealthCheck row = new SystemHealthCheck();
            row.setModule(h.module());
            row.setScore(score);
            row.setNeededChange(h.nc());
            checks.add(row);
        }
        m.setHealthChecks(checks);
        return m;
    }

    // ------------------------------------------------------------------ 校验 / 辅助

    private static String requireDate(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD");
        try {
            return LocalDate.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("日期格式应为 YYYY-MM-DD");
        }
    }

    private static String requireMonth(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("指标格式应为 YYYY-MM");
        try {
            return YearMonth.parse(value.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("指标格式应为 YYYY-MM");
        }
    }

    private static String hhmm(String value) {
        if (value == null) return null;
        try {
            return LocalTime.parse(value, HHMM).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式应为 HH:MM");
        }
    }

    private static String nvl(String v) { return v == null ? "" : v; }

    private User require(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return null;
        Map<String, Object> claims = tokens.verify(header.substring(7).trim());
        if (claims == null || !(claims.get("uid") instanceof Number uid)) return null;
        return users.findById(uid.longValue()).orElse(null);
    }

    private static Map<String, Object> userMap(Map<String, Object> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", Views.number(row.get("id")));
        m.put("username", Views.str(row.get("username")));
        m.put("display_name", Views.str(row.get("display_name")));
        Object staff = row.get("is_staff");
        m.put("is_staff", staff instanceof Number n ? n.intValue() : 0);
        Object created = row.get("created_at");
        m.put("created_at", Views.str(created).isEmpty() ? null : Views.datetime(created));
        return m;
    }

    private static ResponseEntity<Object> unauthorized() {
        return err(401, "未认证或 Token 已过期");
    }

    private static ResponseEntity<Object> err(int status, String message) {
        return ResponseEntity.status(status).body(Map.of("detail", message));
    }
}
