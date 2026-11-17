from django.conf import settings
from django.db import models


class Project(models.Model):
    """项目"""

    STATUS_CHOICES = [
        ("active", "进行中"),
        ("paused", "已暂停"),
        ("done", "已完成"),
    ]

    name = models.CharField("项目名称", max_length=100)
    description = models.TextField("项目描述", blank=True, default="")
    status = models.CharField("状态", max_length=20, choices=STATUS_CHOICES, default="active")
    start_date = models.DateField("开始日期", null=True, blank=True)
    end_date = models.DateField("结束日期", null=True, blank=True)
    owner = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        related_name="owned_projects",
        verbose_name="负责人",
    )
    members = models.ManyToManyField(
        settings.AUTH_USER_MODEL,
        related_name="joined_projects",
        blank=True,
        verbose_name="项目成员",
    )
    created_at = models.DateTimeField("创建时间", auto_now_add=True)
    updated_at = models.DateTimeField("更新时间", auto_now=True)

    class Meta:
        verbose_name = "项目"
        verbose_name_plural = "项目"
        ordering = ["-created_at"]

    def __str__(self):
        return self.name

    @property
    def task_count(self):
        return self.tasks.count()

    @property
    def done_task_count(self):
        return self.tasks.filter(status="done").count()

    @property
    def progress(self):
        """任务完成进度百分比"""
        total = self.task_count
        return round(self.done_task_count / total * 100) if total else 0


class Task(models.Model):
    """任务"""

    STATUS_CHOICES = [
        ("todo", "待处理"),
        ("doing", "进行中"),
        ("done", "已完成"),
    ]
    PRIORITY_CHOICES = [
        ("low", "低"),
        ("medium", "中"),
        ("high", "高"),
    ]

    project = models.ForeignKey(
        Project, on_delete=models.CASCADE, related_name="tasks", verbose_name="所属项目"
    )
    title = models.CharField("任务标题", max_length=200)
    description = models.TextField("任务描述", blank=True, default="")
    assignee = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name="assigned_tasks",
        verbose_name="执行人",
    )
    status = models.CharField("状态", max_length=20, choices=STATUS_CHOICES, default="todo")
    priority = models.CharField("优先级", max_length=20, choices=PRIORITY_CHOICES, default="medium")
    due_date = models.DateField("截止日期", null=True, blank=True)
    created_at = models.DateTimeField("创建时间", auto_now_add=True)
    updated_at = models.DateTimeField("更新时间", auto_now=True)

    class Meta:
        verbose_name = "任务"
        verbose_name_plural = "任务"
        ordering = ["status", "-priority", "due_date"]

    def __str__(self):
        return f"{self.project.name} - {self.title}"
