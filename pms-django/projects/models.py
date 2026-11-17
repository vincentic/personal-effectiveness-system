from django.conf import settings
from django.core.validators import MaxValueValidator, MinValueValidator
from django.db import models


class DailyPlan(models.Model):
    """每日执行清单（PDF 第 1 页）：一人一天一条。"""

    ENERGY_CHOICES = [
        ("low", "低"),
        ("medium", "中"),
        ("high", "高"),
    ]

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="daily_plans",
        verbose_name="用户",
    )
    plan_date = models.DateField("日期")
    sleep_hours = models.DecimalField("睡眠（小时）", max_digits=4, decimal_places=1, null=True, blank=True)
    energy_level = models.CharField("精力", max_length=10, choices=ENERGY_CHOICES, default="medium")
    mit = models.TextField("今日最重要的成果（MIT）", blank=True, default="")

    # 执行前后检查：启动前 3 项
    pre_know_output = models.BooleanField("启动前：我知道本轮要交付什么", default=False)
    pre_materials_ready = models.BooleanField("启动前：材料已准备", default=False)
    pre_phone_handled = models.BooleanField("启动前：手机/通知已处理", default=False)
    # 执行中 3 项
    during_one_block = models.BooleanField("执行中：一次只做一个组块", default=False)
    during_shrink_step = models.BooleanField("执行中：卡住时先缩小下一步", default=False)
    during_record_distraction = models.BooleanField("执行中：必要时记录干扰", default=False)
    # 结束后 3 项
    post_visible_output = models.BooleanField("结束后：有可见产出", default=False)
    post_recall = models.BooleanField("结束后：做了主动回忆/复述", default=False)
    post_next_step = models.BooleanField("结束后：标记下一步", default=False)

    # 生活维护 4 项
    life_water_food = models.BooleanField("饮水/正常饮食", default=False)
    life_walk_exercise = models.BooleanField("走路/运动", default=False)
    life_clean_tidy = models.BooleanField("清洁/整理", default=False)
    life_social_outdoor = models.BooleanField("真实交流/户外", default=False)

    # 晚间 5 分钟复盘
    review_accomplished = models.TextField("今天真正完成了什么", blank=True, default="")
    review_resistance = models.TextField("今天最大的阻力是什么", blank=True, default="")
    review_tomorrow_start = models.TextField("明天最小的启动动作", blank=True, default="")
    review_gratitude = models.TextField("今天值得感谢/享受的一件事", blank=True, default="")

    created_at = models.DateTimeField("创建时间", auto_now_add=True)
    updated_at = models.DateTimeField("更新时间", auto_now=True)

    class Meta:
        verbose_name = "每日执行清单"
        verbose_name_plural = "每日执行清单"
        ordering = ["-plan_date", "-id"]
        constraints = [
            models.UniqueConstraint(fields=["user", "plan_date"], name="uk_daily_user_date"),
        ]

    def __str__(self):
        return f"{self.plan_date} · {self.user.username}"

    @property
    def blocks_total(self):
        return self.blocks.count()

    @property
    def blocks_done(self):
        return self.blocks.filter(is_done=True).count()

    @property
    def block_progress(self):
        total = self.blocks_total
        return round(self.blocks_done / total * 100) if total else 0

    @property
    def checklist_total(self):
        return 13  # 执行前后 9 项 + 生活维护 4 项

    @property
    def checklist_done(self):
        return sum(
            bool(getattr(self, field))
            for field in (
                "pre_know_output", "pre_materials_ready", "pre_phone_handled",
                "during_one_block", "during_shrink_step", "during_record_distraction",
                "post_visible_output", "post_recall", "post_next_step",
                "life_water_food", "life_walk_exercise", "life_clean_tidy", "life_social_outdoor",
            )
        )


class TimeBlock(models.Model):
    """今日组块与时间盒：输入 / 关键动作 / 可验收输出。"""

    daily_plan = models.ForeignKey(
        DailyPlan, on_delete=models.CASCADE, related_name="blocks", verbose_name="所属日清单"
    )
    seq = models.PositiveSmallIntegerField("序号", default=0)
    block_name = models.CharField("组块/任务", max_length=200)
    input_desc = models.CharField("输入", max_length=255, blank=True, default="")
    key_action = models.CharField("关键动作", max_length=255, blank=True, default="")
    deliverable = models.CharField("可验收输出", max_length=255, blank=True, default="")
    time_start = models.TimeField("时间盒开始", null=True, blank=True)
    time_end = models.TimeField("时间盒结束", null=True, blank=True)
    is_done = models.BooleanField("完成", default=False)

    class Meta:
        verbose_name = "时间盒组块"
        verbose_name_plural = "时间盒组块"
        ordering = ["seq", "id"]

    def __str__(self):
        return self.block_name

    @property
    def time_box(self):
        if self.time_start and self.time_end:
            return f"{self.time_start:%H:%M}–{self.time_end:%H:%M}"
        return "未设定"


class WeeklyReview(models.Model):
    """每周复盘清单（PDF 第 2 页）：一人一周一条。"""

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="weekly_reviews",
        verbose_name="用户",
    )
    week_start = models.DateField("本周起始日（周一）")

    # 下周设计：少而明确
    next_mit_1 = models.CharField("下周最重要成果 1", max_length=255, blank=True, default="")
    next_mit_2 = models.CharField("下周最重要成果 2", max_length=255, blank=True, default="")
    next_mit_3 = models.CharField("下周最重要成果 3", max_length=255, blank=True, default="")
    deep_work_time = models.CharField("要保护的深度时间", max_length=255, blank=True, default="")
    prep_env_materials = models.TextField("需要提前准备的环境/材料", blank=True, default="")
    if_condition = models.CharField("If-Then：如果……", max_length=255, blank=True, default="")
    then_action = models.CharField("If-Then：那么我就……", max_length=255, blank=True, default="")

    # 复盘问题
    q_skilled_block = models.BooleanField("哪个组块本周变得更熟练？", default=False)
    q_low_value_time = models.BooleanField("哪件事耗时很多但价值很低？", default=False)
    q_estimate_gap = models.BooleanField("哪个时间盒估算偏差最大？为什么？", default=False)
    q_rest_connection = models.BooleanField("本周有没有休息、快乐和真实连接？", default=False)
    q_next_adjust = models.BooleanField("下周要继续、调整、停止什么？", default=False)

    created_at = models.DateTimeField("创建时间", auto_now_add=True)
    updated_at = models.DateTimeField("更新时间", auto_now=True)

    class Meta:
        verbose_name = "每周复盘清单"
        verbose_name_plural = "每周复盘清单"
        ordering = ["-week_start", "-id"]
        constraints = [
            models.UniqueConstraint(fields=["user", "week_start"], name="uk_weekly_user_week"),
        ]

    def __str__(self):
        return f"{self.week_start} 当周 · {self.user.username}"

    @property
    def week_end(self):
        from datetime import timedelta

        return self.week_start + timedelta(days=6)


class WeeklyDomainReview(models.Model):
    """本周回顾：领域事实与状态。"""

    DOMAIN_CHOICES = [
        ("work", "工作 / 项目"),
        ("study", "学习 / 技能"),
        ("body", "身体 / 精力"),
        ("relations", "关系 / 生活"),
    ]
    STATUS_CHOICES = [
        ("smooth", "顺利"),
        ("blocked", "受阻"),
        ("paused", "暂停"),
    ]

    weekly_review = models.ForeignKey(
        WeeklyReview, on_delete=models.CASCADE, related_name="domains", verbose_name="所属周复盘"
    )
    domain = models.CharField("领域", max_length=20, choices=DOMAIN_CHOICES)
    facts = models.TextField("本周事实 / 产出", blank=True, default="")
    status = models.CharField("状态", max_length=10, choices=STATUS_CHOICES, default="smooth")

    class Meta:
        verbose_name = "领域回顾"
        verbose_name_plural = "领域回顾"
        ordering = ["id"]
        constraints = [
            models.UniqueConstraint(fields=["weekly_review", "domain"], name="uk_wdr_review_domain"),
        ]

    def __str__(self):
        return self.get_domain_display()


class WeeklyTaskFlow(models.Model):
    """任务流清理（GTD + Kanban）。"""

    BUCKET_CHOICES = [
        ("todo", "待办"),
        ("doing", "进行中"),
        ("done", "已完成"),
        ("remove", "删除/委托/延后"),
    ]

    weekly_review = models.ForeignKey(
        WeeklyReview, on_delete=models.CASCADE, related_name="flows", verbose_name="所属周复盘"
    )
    bucket = models.CharField("分栏", max_length=20, choices=BUCKET_CHOICES, default="todo")
    content = models.CharField("内容", max_length=255, blank=True, default="")
    seq = models.PositiveSmallIntegerField("序号", default=0)

    class Meta:
        verbose_name = "任务流条目"
        verbose_name_plural = "任务流条目"
        ordering = ["bucket", "seq", "id"]

    def __str__(self):
        return f"{self.get_bucket_display()}: {self.content}"


class MonthlyReview(models.Model):
    """每月重构清单（PDF 第 3 页）：一人一月一条。"""

    WORTH_CHOICES = [
        ("yes", "是"),
        ("adjust", "调整"),
        ("stop", "停止"),
    ]

    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="monthly_reviews",
        verbose_name="用户",
    )
    month = models.CharField("月份（YYYY-MM）", max_length=7)

    # 目标与价值检查
    top_achievement = models.TextField("本月最重要的成果", blank=True, default="")
    worth_continue = models.CharField("是否仍值得投入", max_length=10, choices=WORTH_CHOICES, default="yes")
    worth_reason = models.CharField("原因", max_length=255, blank=True, default="")
    most_valuable_input = models.TextField("本月最有价值的投入", blank=True, default="")
    biggest_opportunity_cost = models.TextField("本月最大的机会成本", blank=True, default="")

    # 下月只做三项系统改进
    keep_item = models.TextField("保留（有效的）", blank=True, default="")
    cut_item = models.TextField("删减（低价值的）", blank=True, default="")
    experiment_item = models.TextField("实验（新的最小改变）", blank=True, default="")

    # 季度方向校准（可选）
    q_skill_portfolio = models.BooleanField("技能是否形成可展示作品？", default=False)
    q_career_alignment = models.BooleanField("工作是否更接近长期方向？", default=False)
    q_life_autonomy = models.BooleanField("生活是否更有自主性？", default=False)
    q_recovery_relations = models.BooleanField("是否有足够的恢复与关系投入？", default=False)
    q_longterm_decision = models.BooleanField("是否应该继续、转向或停止某项长期投资？", default=False)

    created_at = models.DateTimeField("创建时间", auto_now_add=True)
    updated_at = models.DateTimeField("更新时间", auto_now=True)

    class Meta:
        verbose_name = "每月重构清单"
        verbose_name_plural = "每月重构清单"
        ordering = ["-month", "-id"]
        constraints = [
            models.UniqueConstraint(fields=["user", "month"], name="uk_monthly_user_month"),
        ]

    def __str__(self):
        return f"{self.month} · {self.user.username}"

    @property
    def avg_score(self):
        scores = [c.score for c in self.health_checks.all() if c.score is not None]
        return round(sum(scores) / len(scores), 1) if scores else None


class SystemHealthCheck(models.Model):
    """系统健康检查：6 个模块，自评 1–5 分。"""

    MODULE_CHOICES = [
        ("direction", "方向清晰度"),
        ("taskload", "任务负荷 / 在制任务"),
        ("blockquality", "组块质量 / 可独立调用"),
        ("timebox", "时间盒现实程度"),
        ("sleepenergy", "睡眠 / 体能 / 情绪"),
        ("reviewquality", "复盘与反馈质量"),
    ]

    monthly_review = models.ForeignKey(
        MonthlyReview, on_delete=models.CASCADE, related_name="health_checks", verbose_name="所属月重构"
    )
    module = models.CharField("模块", max_length=30, choices=MODULE_CHOICES)
    score = models.PositiveSmallIntegerField(
        "自评（1-5）", null=True, blank=True, validators=[MinValueValidator(1), MaxValueValidator(5)]
    )
    needed_change = models.TextField("需要的改变", blank=True, default="")

    class Meta:
        verbose_name = "系统健康检查"
        verbose_name_plural = "系统健康检查"
        ordering = ["id"]
        constraints = [
            models.UniqueConstraint(fields=["monthly_review", "module"], name="uk_shc_review_module"),
        ]

    def __str__(self):
        return f"{self.get_module_display()}: {self.score or '—'}"
