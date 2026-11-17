from django.contrib import admin

from .models import Project, Task


@admin.register(Project)
class ProjectAdmin(admin.ModelAdmin):
    list_display = ("id", "name", "status", "owner", "created_at")
    list_filter = ("status",)
    search_fields = ("name", "description")
    filter_horizontal = ("members",)


@admin.register(Task)
class TaskAdmin(admin.ModelAdmin):
    list_display = ("id", "title", "project", "assignee", "status", "priority", "due_date")
    list_filter = ("status", "priority")
    search_fields = ("title", "description")
