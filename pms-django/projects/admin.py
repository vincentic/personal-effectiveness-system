from django.contrib import admin

from .models import (
    DailyPlan,
    MonthlyReview,
    SystemHealthCheck,
    TimeBlock,
    WeeklyDomainReview,
    WeeklyReview,
    WeeklyTaskFlow,
)


@admin.register(DailyPlan)
class DailyPlanAdmin(admin.ModelAdmin):
    list_display = ("id", "plan_date", "user", "energy_level", "mit", "created_at")
    list_filter = ("energy_level",)
    search_fields = ("mit", "user__username")


@admin.register(TimeBlock)
class TimeBlockAdmin(admin.ModelAdmin):
    list_display = ("id", "daily_plan", "seq", "block_name", "time_start", "time_end", "is_done")
    list_filter = ("is_done",)
    search_fields = ("block_name",)


@admin.register(WeeklyReview)
class WeeklyReviewAdmin(admin.ModelAdmin):
    list_display = ("id", "week_start", "user", "next_mit_1", "created_at")
    search_fields = ("user__username", "next_mit_1")


@admin.register(WeeklyDomainReview)
class WeeklyDomainReviewAdmin(admin.ModelAdmin):
    list_display = ("id", "weekly_review", "domain", "status")
    list_filter = ("domain", "status")


@admin.register(WeeklyTaskFlow)
class WeeklyTaskFlowAdmin(admin.ModelAdmin):
    list_display = ("id", "weekly_review", "bucket", "content", "seq")
    list_filter = ("bucket",)


@admin.register(MonthlyReview)
class MonthlyReviewAdmin(admin.ModelAdmin):
    list_display = ("id", "month", "user", "top_achievement", "created_at")
    search_fields = ("user__username", "top_achievement")


@admin.register(SystemHealthCheck)
class SystemHealthCheckAdmin(admin.ModelAdmin):
    list_display = ("id", "monthly_review", "module", "score")
    list_filter = ("module", "score")
