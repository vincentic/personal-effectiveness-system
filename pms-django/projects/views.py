from django.contrib.auth import login
from django.contrib.auth.decorators import login_required
from django.contrib.auth.forms import AuthenticationForm
from django.shortcuts import get_object_or_404, redirect, render

from .forms import ProjectForm, TaskForm
from .models import Project, Task


def login_view(request):
    """登录页"""
    if request.user.is_authenticated:
        return redirect("dashboard")
    form = AuthenticationForm(request, data=request.POST or None)
    if request.method == "POST" and form.is_valid():
        login(request, form.get_user())
        return redirect("dashboard")
    return render(request, "registration/login.html", {"form": form})


@login_required
def dashboard(request):
    """工作台：统计概览 + 我的项目 + 我的任务"""
    context = {
        "project_count": Project.objects.count(),
        "task_count": Task.objects.count(),
        "todo_count": Task.objects.filter(status="todo").count(),
        "doing_count": Task.objects.filter(status="doing").count(),
        "done_count": Task.objects.filter(status="done").count(),
        "my_projects": Project.objects.filter(owner=request.user)[:5],
        "my_tasks": Task.objects.filter(assignee=request.user).exclude(status="done")[:10],
    }
    return render(request, "dashboard.html", context)


@login_required
def project_list(request):
    """项目列表（支持按状态、名称筛选）"""
    status = request.GET.get("status", "")
    keyword = request.GET.get("q", "")
    qs = Project.objects.all()
    if status:
        qs = qs.filter(status=status)
    if keyword:
        qs = qs.filter(name__icontains=keyword)
    return render(request, "projects/project_list.html", {"projects": qs, "status": status, "keyword": keyword})


@login_required
def project_create(request):
    """新建项目（创建者自动成为负责人）"""
    if request.method == "POST":
        form = ProjectForm(request.POST)
        if form.is_valid():
            project = form.save(commit=False)
            project.owner = request.user
            project.save()
            form.save_m2m()
            return redirect("project_detail", pk=project.pk)
    else:
        form = ProjectForm()
    return render(request, "projects/project_form.html", {"form": form, "title": "新建项目"})


@login_required
def project_detail(request, pk):
    """项目详情：信息 + 任务看板"""
    project = get_object_or_404(Project, pk=pk)
    tasks = project.tasks.select_related("assignee")
    return render(request, "projects/project_detail.html", {"project": project, "tasks": tasks})


@login_required
def project_update(request, pk):
    """编辑项目（仅负责人可操作）"""
    project = get_object_or_404(Project, pk=pk)
    if project.owner != request.user:
        return redirect("project_detail", pk=pk)
    if request.method == "POST":
        form = ProjectForm(request.POST, instance=project)
        if form.is_valid():
            form.save()
            return redirect("project_detail", pk=pk)
    else:
        form = ProjectForm(instance=project)
    return render(request, "projects/project_form.html", {"form": form, "title": "编辑项目"})


@login_required
def project_delete(request, pk):
    """删除项目（仅负责人可操作）"""
    project = get_object_or_404(Project, pk=pk)
    if project.owner == request.user:
        project.delete()
    return redirect("project_list")


@login_required
def task_create(request, project_pk):
    """新建任务"""
    project = get_object_or_404(Project, pk=project_pk)
    if request.method == "POST":
        form = TaskForm(request.POST)
        if form.is_valid():
            task = form.save(commit=False)
            task.project = project
            task.save()
            return redirect("project_detail", pk=project_pk)
    else:
        form = TaskForm()
    return render(
        request,
        "projects/task_form.html",
        {"form": form, "project": project, "title": "新建任务"},
    )


@login_required
def task_update(request, pk):
    """编辑任务"""
    task = get_object_or_404(Task, pk=pk)
    if request.method == "POST":
        form = TaskForm(request.POST, instance=task)
        if form.is_valid():
            form.save()
            return redirect("project_detail", pk=task.project.pk)
    else:
        form = TaskForm(instance=task)
    return render(
        request,
        "projects/task_form.html",
        {"form": form, "project": task.project, "title": "编辑任务"},
    )


@login_required
def task_delete(request, pk):
    """删除任务"""
    task = get_object_or_404(Task, pk=pk)
    project_pk = task.project.pk
    task.delete()
    return redirect("project_detail", pk=project_pk)
