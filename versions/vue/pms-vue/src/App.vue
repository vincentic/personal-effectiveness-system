<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from "vue";
import { api, currentToken, setToken } from "./api";
import type { User } from "./types";
import DashboardView from "./views/DashboardView.vue";
import DailyView from "./views/DailyView.vue";
import LoginView from "./views/LoginView.vue";
import MonthlyView from "./views/MonthlyView.vue";
import WeeklyView from "./views/WeeklyView.vue";

type Page = "dashboard" | "daily" | "weekly" | "monthly";

const pages: { id: Page; label: string; icon: string; title: string }[] = [
  { id: "dashboard", label: "工作台", icon: "⌂", title: "今天，先做好一件重要的事" },
  { id: "daily", label: "每日执行", icon: "◷", title: "每日执行" },
  { id: "weekly", label: "每周复盘", icon: "↗", title: "每周复盘" },
  { id: "monthly", label: "每月重构", icon: "◫", title: "每月重构" },
];

function hashPage(): Page {
  const candidate = window.location.hash.slice(2);
  return pages.some((page) => page.id === candidate) ? (candidate as Page) : "dashboard";
}

const activePage = ref<Page>(hashPage());
const user = ref<User | null>(null);
const checkingSession = ref(true);
const mobileMenuOpen = ref(false);

watch(activePage, (page) => {
  const hash = `#/${page}`;
  if (window.location.hash !== hash) window.location.hash = hash;
});

function onHashChange(): void {
  activePage.value = hashPage();
}

async function restoreSession(): Promise<void> {
  if (!currentToken()) {
    checkingSession.value = false;
    return;
  }
  try {
    user.value = await api.me();
  } catch {
    setToken("");
  } finally {
    checkingSession.value = false;
  }
}

function onLogin(payload: { token: string; user: User }): void {
  setToken(payload.token);
  user.value = payload.user;
  activePage.value = "dashboard";
}

function logout(): void {
  setToken("");
  user.value = null;
  activePage.value = "dashboard";
  window.location.hash = "";
}

onMounted(() => {
  window.addEventListener("hashchange", onHashChange);
  void restoreSession();
});

onUnmounted(() => window.removeEventListener("hashchange", onHashChange));
</script>

<template>
  <div v-if="checkingSession" class="boot-screen">
    <span class="brand-mark">P</span>
    <span>正在准备你的工作台…</span>
  </div>
  <LoginView v-else-if="!user" @success="onLogin" />
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ 'sidebar-open': mobileMenuOpen }">
      <a class="brand" href="#/dashboard" @click="activePage = 'dashboard'">
        <span class="brand-mark">P</span>
        <span><strong>个人效能</strong><small>PERSONAL SYSTEM</small></span>
      </a>
      <div class="nav-caption">工作空间</div>
      <nav class="main-nav" aria-label="主导航">
        <button
          v-for="page in pages"
          :key="page.id"
          class="nav-item"
          :class="{ active: activePage === page.id }"
          @click="activePage = page.id; mobileMenuOpen = false"
        >
          <span class="nav-icon">{{ page.icon }}</span>
          <span>{{ page.label }}</span>
          <span v-if="activePage === page.id" class="nav-indicator"></span>
        </button>
      </nav>
      <div class="sidebar-bottom">
        <div class="method-card">
          <span class="method-icon">✳</span>
          <strong>日执行 · 周复盘 · 月重构</strong>
          <p>让重要的事持续推进，让系统本身保持轻盈。</p>
        </div>
        <button class="user-menu" @click="logout">
          <span class="avatar">{{ (user.display_name || user.username).slice(0, 1) }}</span>
          <span class="user-copy"><strong>{{ user.display_name || user.username }}</strong><small>退出登录</small></span>
          <span class="logout-icon">↗</span>
        </button>
      </div>
    </aside>

    <div v-if="mobileMenuOpen" class="sidebar-backdrop" @click="mobileMenuOpen = false"></div>
    <main class="main-area">
      <header class="topbar">
        <button class="mobile-menu-button" aria-label="打开菜单" @click="mobileMenuOpen = !mobileMenuOpen">☰</button>
        <div class="breadcrumb"><span>个人效能</span><span class="crumb-separator">/</span><strong>{{ pages.find((item) => item.id === activePage)?.label }}</strong></div>
        <div class="topbar-right">
          <span class="today-chip">{{ new Date().toLocaleDateString("zh-CN", { month: "long", day: "numeric", weekday: "long" }) }}</span>
          <span class="top-avatar">{{ (user.display_name || user.username).slice(0, 1) }}</span>
        </div>
      </header>

      <section class="page-content">
        <DashboardView v-if="activePage === 'dashboard'" @navigate="activePage = $event" />
        <DailyView v-else-if="activePage === 'daily'" />
        <WeeklyView v-else-if="activePage === 'weekly'" />
        <MonthlyView v-else />
      </section>
      <footer class="app-footer">个人效能系统 <span>·</span> 每天进步一点点</footer>
    </main>
  </div>
</template>
