from datetime import timedelta

from django.contrib.auth import login
from django.contrib.auth.decorators import login_required
from django.contrib.auth.forms import AuthenticationForm
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone

from .forms import (
    DailyPlanForm,
    DomainReviewFormSet,
    HealthCheckFormSet,
    MonthlyReviewForm,
    TaskFlowFormSet,
    TimeBlockFormSet,
    WeeklyReviewForm,
)
from .models import DailyPlan, MonthlyReview, TimeBlock, WeeklyReview
from .viewmode import render_page, switch_view  # noqa: F401  switch_view 供 urls.py 使用


DOMAIN_INITIAL = [
    {"domain": "work"},
    {"domain": "study"},
    {"domain": "body"},
    {"domain": "relations"},
]

HEALTH_INITIAL = [
    {"module": "direction"},
    {"module": "taskload"},
    {"module": "blockquality"},
    {"module": "timebox"},
    {"module": "sleepenergy"},
    {"module": "reviewquality"},
]


def _monday(d):
    return d - timedelta(days=d.weekday())


def login_view(request):
    """登录页"""
    if request.user.is_authenticated:
        return redirect("dashboard")
    form = AuthenticationForm(request, data=request.POST or None)
    if request.method == "POST" and form.is_valid():
        login(request, form.get_user())
        return redirect("dashboard")
    return render_page(request, "registration/login.html", {"form": form})


@login_required
def dashboard(request):
    """工作台：效能系统概览 + 今日入口"""
    today = timezone.localdate()
    monday = _monday(today)
    month = today.strftime("%Y-%m")

    daily_qs = DailyPlan.objects.filter(user=request.user)
    weekly_qs = WeeklyReview.objects.filter(user=request.user)
    monthly_qs = MonthlyReview.objects.filter(user=request.user)

    today_plan = daily_qs.filter(plan_date=today).first()
    week_review = weekly_qs.filter(week_start=monday).first()
    month_review = monthly_qs.filter(month=month).first()

    context = {
        "today": today,
        "today_plan": today_plan,
        "week_review": week_review,
        "month_review": month_review,
        "daily_count": daily_qs.count(),
        "weekly_count": weekly_qs.count(),
        "monthly_count": monthly_qs.count(),
        "recent_daily": daily_qs[:5],
        "recent_weekly": weekly_qs[:5],
        "recent_monthly": monthly_qs[:5],
        "block_total": sum(p.blocks_total for p in daily_qs[:7]),
        "block_done": sum(p.blocks_done for p in daily_qs[:7]),
    }
    return render_page(request, "dashboard.html", context)


# ---------------------------------------------------------------- 每日执行清单

@login_required
def daily_list(request):
    plans = DailyPlan.objects.filter(user=request.user)
    return render_page(request, "daily/daily_list.html", {"plans": plans})


@login_required
def daily_create(request):
    if request.method == "POST":
        form = DailyPlanForm(request.POST)
        formset = TimeBlockFormSet(request.POST, prefix="blocks")
        if form.is_valid() and formset.is_valid():
            plan = form.save(commit=False)
            plan.user = request.user
            plan.save()
            formset.instance = plan
            formset.save()
            return redirect("daily_list")
    else:
        form = DailyPlanForm(initial={"plan_date": timezone.localdate()})
        formset = TimeBlockFormSet(prefix="blocks", initial=[{"seq": i} for i in range(1, 5)])
    return render_page(request, "daily/daily_form.html", {"form": form, "formset": formset, "title": "填写今日执行清单"})


@login_required
def daily_update(request, pk):
    plan = get_object_or_404(DailyPlan, pk=pk, user=request.user)
    if request.method == "POST":
        form = DailyPlanForm(request.POST, instance=plan)
        formset = TimeBlockFormSet(request.POST, instance=plan, prefix="blocks")
        if form.is_valid() and formset.is_valid():
            form.save()
            formset.save()
            return redirect("daily_list")
    else:
        form = DailyPlanForm(instance=plan)
        formset = TimeBlockFormSet(instance=plan, prefix="blocks")
    return render_page(request, "daily/daily_form.html", {"form": form, "formset": formset, "title": "编辑每日执行清单", "plan": plan})


@login_required
def daily_delete(request, pk):
    plan = get_object_or_404(DailyPlan, pk=pk, user=request.user)
    if request.method == "POST":
        plan.delete()
    return redirect("daily_list")


@login_required
def daily_toggle_block(request, pk):
    """看板式快速勾选某个时间盒组块"""
    block = get_object_or_404(TimeBlock, pk=pk, daily_plan__user=request.user)
    if request.method == "POST":
        block.is_done = not block.is_done
        block.save(update_fields=["is_done"])
    return redirect("daily_list")


# ---------------------------------------------------------------- 每周复盘清单

@login_required
def weekly_list(request):
    reviews = WeeklyReview.objects.filter(user=request.user)
    return render_page(request, "weekly/weekly_list.html", {"reviews": reviews})


@login_required
def weekly_create(request):
    if request.method == "GET":
        form = WeeklyReviewForm(initial={"week_start": _monday(timezone.localdate())})
        domains = DomainReviewFormSet(prefix="domains", initial=DOMAIN_INITIAL)
        flows = TaskFlowFormSet(prefix="flows")
    else:
        form = WeeklyReviewForm(request.POST)
        domains = DomainReviewFormSet(request.POST, prefix="domains")
        flows = TaskFlowFormSet(request.POST, prefix="flows")
        if form.is_valid() and domains.is_valid() and flows.is_valid():
            review = form.save(commit=False)
            review.user = request.user
            review.save()
            domains.instance = review
            domains.save()
            flows.instance = review
            flows.save()
            return redirect("weekly_list")
    return render_page(request, "weekly/weekly_form.html", {
        "form": form, "domains": domains, "flows": flows, "title": "填写每周复盘清单",
    })


@login_required
def weekly_update(request, pk):
    review = get_object_or_404(WeeklyReview, pk=pk, user=request.user)
    if request.method == "POST":
        form = WeeklyReviewForm(request.POST, instance=review)
        domains = DomainReviewFormSet(request.POST, instance=review, prefix="domains")
        flows = TaskFlowFormSet(request.POST, instance=review, prefix="flows")
        if form.is_valid() and domains.is_valid() and flows.is_valid():
            form.save()
            domains.save()
            flows.save()
            return redirect("weekly_list")
    else:
        form = WeeklyReviewForm(instance=review)
        domains = DomainReviewFormSet(instance=review, prefix="domains")
        flows = TaskFlowFormSet(instance=review, prefix="flows")
    return render_page(request, "weekly/weekly_form.html", {
        "form": form, "domains": domains, "flows": flows, "title": "编辑每周复盘清单", "review": review,
    })


@login_required
def weekly_delete(request, pk):
    review = get_object_or_404(WeeklyReview, pk=pk, user=request.user)
    if request.method == "POST":
        review.delete()
    return redirect("weekly_list")


# ---------------------------------------------------------------- 每月重构清单

@login_required
def monthly_list(request):
    reviews = MonthlyReview.objects.filter(user=request.user)
    return render_page(request, "monthly/monthly_list.html", {"reviews": reviews})


@login_required
def monthly_create(request):
    if request.method == "GET":
        form = MonthlyReviewForm(initial={"month": timezone.localdate().strftime("%Y-%m")})
        health = HealthCheckFormSet(prefix="health", initial=HEALTH_INITIAL)
    else:
        form = MonthlyReviewForm(request.POST)
        health = HealthCheckFormSet(request.POST, prefix="health")
        if form.is_valid() and health.is_valid():
            review = form.save(commit=False)
            review.user = request.user
            review.save()
            health.instance = review
            health.save()
            return redirect("monthly_list")
    return render_page(request, "monthly/monthly_form.html", {
        "form": form, "health": health, "title": "填写每月重构清单",
    })


@login_required
def monthly_update(request, pk):
    review = get_object_or_404(MonthlyReview, pk=pk, user=request.user)
    if request.method == "POST":
        form = MonthlyReviewForm(request.POST, instance=review)
        health = HealthCheckFormSet(request.POST, instance=review, prefix="health")
        if form.is_valid() and health.is_valid():
            form.save()
            health.save()
            return redirect("monthly_list")
    else:
        form = MonthlyReviewForm(instance=review)
        health = HealthCheckFormSet(instance=review, prefix="health")
    return render_page(request, "monthly/monthly_form.html", {
        "form": form, "health": health, "title": "编辑每月重构清单", "review": review,
    })


@login_required
def monthly_delete(request, pk):
    review = get_object_or_404(MonthlyReview, pk=pk, user=request.user)
    if request.method == "POST":
        review.delete()
    return redirect("monthly_list")
