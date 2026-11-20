from django.contrib.auth.views import LogoutView
from django.urls import path

from . import views

urlpatterns = [
    path("", views.dashboard, name="dashboard"),
    path("login/", views.login_view, name="login"),
    path("logout/", LogoutView.as_view(), name="logout"),
    path("view/<str:mode>/", views.switch_view, name="switch_view"),

    # 每日执行清单
    path("daily/", views.daily_list, name="daily_list"),
    path("daily/new/", views.daily_create, name="daily_create"),
    path("daily/<int:pk>/edit/", views.daily_update, name="daily_update"),
    path("daily/<int:pk>/delete/", views.daily_delete, name="daily_delete"),
    path("daily/blocks/<int:pk>/toggle/", views.daily_toggle_block, name="daily_toggle_block"),

    # 每周复盘清单
    path("weekly/", views.weekly_list, name="weekly_list"),
    path("weekly/new/", views.weekly_create, name="weekly_create"),
    path("weekly/<int:pk>/edit/", views.weekly_update, name="weekly_update"),
    path("weekly/<int:pk>/delete/", views.weekly_delete, name="weekly_delete"),

    # 每月重构清单
    path("monthly/", views.monthly_list, name="monthly_list"),
    path("monthly/new/", views.monthly_create, name="monthly_create"),
    path("monthly/<int:pk>/edit/", views.monthly_update, name="monthly_update"),
    path("monthly/<int:pk>/delete/", views.monthly_delete, name="monthly_delete"),
]
