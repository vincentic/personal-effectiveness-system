from django import forms
from django.forms import inlineformset_factory

from .models import (
    DailyPlan,
    MonthlyReview,
    SystemHealthCheck,
    TimeBlock,
    WeeklyDomainReview,
    WeeklyReview,
    WeeklyTaskFlow,
)


def _text(attrs=None, rows=None):
    attrs = dict(attrs or {})
    attrs.setdefault("class", "form-control")
    if rows:
        return forms.Textarea(attrs={**attrs, "rows": rows})
    return forms.TextInput(attrs=attrs)


class DailyPlanForm(forms.ModelForm):
    """每日执行清单主表单。"""

    class Meta:
        model = DailyPlan
        fields = [
            "plan_date", "sleep_hours", "energy_level", "mit",
            "pre_know_output", "pre_materials_ready", "pre_phone_handled",
            "during_one_block", "during_shrink_step", "during_record_distraction",
            "post_visible_output", "post_recall", "post_next_step",
            "life_water_food", "life_walk_exercise", "life_clean_tidy", "life_social_outdoor",
            "review_accomplished", "review_resistance", "review_tomorrow_start", "review_gratitude",
        ]
        widgets = {
            "plan_date": forms.DateInput(attrs={"class": "form-control", "type": "date"}),
            "sleep_hours": forms.NumberInput(attrs={"class": "form-control", "step": "0.1", "min": "0", "max": "24"}),
            "energy_level": forms.Select(attrs={"class": "form-select"}),
            "mit": forms.Textarea(attrs={"class": "form-control", "rows": 2, "placeholder": "今天最重要的一个成果……"}),
            "review_accomplished": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "review_resistance": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "review_tomorrow_start": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "review_gratitude": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
        }
        bool_widget = forms.CheckboxInput(attrs={"class": "form-check-input"})
        for _bool_field in (
            "pre_know_output", "pre_materials_ready", "pre_phone_handled",
            "during_one_block", "during_shrink_step", "during_record_distraction",
            "post_visible_output", "post_recall", "post_next_step",
            "life_water_food", "life_walk_exercise", "life_clean_tidy", "life_social_outdoor",
        ):
            widgets[_bool_field] = bool_widget


TimeBlockFormSet = inlineformset_factory(
    DailyPlan,
    TimeBlock,
    fields=["seq", "block_name", "input_desc", "key_action", "deliverable", "time_start", "time_end", "is_done"],
    extra=4,
    can_delete=True,
    widgets={
        "seq": forms.NumberInput(attrs={"class": "form-control", "min": "1"}),
        "block_name": _text({"placeholder": "组块/任务"}),
        "input_desc": _text({"placeholder": "输入"}),
        "key_action": _text({"placeholder": "关键动作"}),
        "deliverable": _text({"placeholder": "可验收输出"}),
        "time_start": forms.TimeInput(attrs={"class": "form-control", "type": "time"}),
        "time_end": forms.TimeInput(attrs={"class": "form-control", "type": "time"}),
        "is_done": forms.CheckboxInput(attrs={"class": "form-check-input"}),
    },
)


class WeeklyReviewForm(forms.ModelForm):
    """每周复盘清单主表单。"""

    class Meta:
        model = WeeklyReview
        fields = [
            "week_start",
            "next_mit_1", "next_mit_2", "next_mit_3",
            "deep_work_time", "prep_env_materials", "if_condition", "then_action",
            "q_skilled_block", "q_low_value_time", "q_estimate_gap", "q_rest_connection", "q_next_adjust",
        ]
        widgets = {
            "week_start": forms.DateInput(attrs={"class": "form-control", "type": "date"}),
            "next_mit_1": _text({"placeholder": "下周最重要成果 1"}),
            "next_mit_2": _text({"placeholder": "下周最重要成果 2"}),
            "next_mit_3": _text({"placeholder": "下周最重要成果 3"}),
            "deep_work_time": _text({"placeholder": "日期 / 时段"}),
            "prep_env_materials": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "if_condition": _text({"placeholder": "如果……"}),
            "then_action": _text({"placeholder": "那么我就……"}),
        }
        bool_widget = forms.CheckboxInput(attrs={"class": "form-check-input"})
        for _bool_field in (
            "q_skilled_block", "q_low_value_time", "q_estimate_gap", "q_rest_connection", "q_next_adjust",
        ):
            widgets[_bool_field] = bool_widget


DomainReviewFormSet = inlineformset_factory(
    WeeklyReview,
    WeeklyDomainReview,
    fields=["domain", "facts", "status"],
    extra=0,
    can_delete=False,
    widgets={
        "domain": forms.Select(attrs={"class": "form-select"}),
        "facts": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
        "status": forms.Select(attrs={"class": "form-select"}),
    },
)


TaskFlowFormSet = inlineformset_factory(
    WeeklyReview,
    WeeklyTaskFlow,
    fields=["bucket", "content", "seq"],
    extra=8,
    can_delete=True,
    widgets={
        "bucket": forms.Select(attrs={"class": "form-select"}),
        "content": _text({"placeholder": "任务内容"}),
        "seq": forms.NumberInput(attrs={"class": "form-control", "min": "0"}),
    },
)


class MonthlyReviewForm(forms.ModelForm):
    """每月重构清单主表单。"""

    class Meta:
        model = MonthlyReview
        fields = [
            "month",
            "top_achievement", "worth_continue", "worth_reason",
            "most_valuable_input", "biggest_opportunity_cost",
            "keep_item", "cut_item", "experiment_item",
            "q_skill_portfolio", "q_career_alignment", "q_life_autonomy",
            "q_recovery_relations", "q_longterm_decision",
        ]
        widgets = {
            "month": forms.TextInput(attrs={"class": "form-control", "placeholder": "YYYY-MM", "pattern": r"\d{4}-\d{2}"}),
            "top_achievement": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "worth_continue": forms.Select(attrs={"class": "form-select"}),
            "worth_reason": _text(),
            "most_valuable_input": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "biggest_opportunity_cost": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "keep_item": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "cut_item": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
            "experiment_item": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
        }
        bool_widget = forms.CheckboxInput(attrs={"class": "form-check-input"})
        for _bool_field in (
            "q_skill_portfolio", "q_career_alignment", "q_life_autonomy",
            "q_recovery_relations", "q_longterm_decision",
        ):
            widgets[_bool_field] = bool_widget

    def clean_month(self):
        month = self.cleaned_data["month"].strip()
        if len(month) != 7 or month[4] != "-" or not (month[:4] + month[5:]).isdigit():
            raise forms.ValidationError("月份格式应为 YYYY-MM，例如 2026-11")
        if not 1 <= int(month[5:]) <= 12:
            raise forms.ValidationError("月份必须在 01–12 之间")
        return month


HealthCheckFormSet = inlineformset_factory(
    MonthlyReview,
    SystemHealthCheck,
    fields=["module", "score", "needed_change"],
    extra=0,
    can_delete=False,
    widgets={
        "module": forms.Select(attrs={"class": "form-select"}),
        "score": forms.NumberInput(attrs={"class": "form-control", "min": "1", "max": "5"}),
        "needed_change": forms.Textarea(attrs={"class": "form-control", "rows": 2}),
    },
)
