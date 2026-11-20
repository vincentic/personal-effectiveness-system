import { useCallback, useEffect, useState } from "react";
import { api, currentToken, setToken } from "./api";
import type { User } from "./types";
import DashboardView from "./views/DashboardView";
import DailyView from "./views/DailyView";
import LoginView from "./views/LoginView";
import MonthlyView from "./views/MonthlyView";
import WeeklyView from "./views/WeeklyView";

type Page = "dashboard" | "daily" | "weekly" | "monthly";

const pages: { id: Page; label: string; icon: string }[] = [
  { id: "dashboard", label: "工作台", icon: "⌂" },
  { id: "daily", label: "每日执行", icon: "◷" },
  { id: "weekly", label: "每周复盘", icon: "↗" },
  { id: "monthly", label: "每月重构", icon: "◫" },
];

function hashPage(): Page {
  const candidate = window.location.hash.slice(2);
  return pages.some((page) => page.id === candidate)
    ? (candidate as Page)
    : "dashboard";
}

function App() {
  const [activePage, setActivePage] = useState<Page>(hashPage);
  const [user, setUser] = useState<User | null>(null);
  const [checkingSession, setCheckingSession] = useState(true);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);

  const logout = useCallback(() => {
    setToken("");
    setUser(null);
    setActivePage("dashboard");
    setMobileMenuOpen(false);
    window.location.hash = "#/dashboard";
  }, []);

  const navigate = useCallback((page: Page) => {
    setActivePage(page);
    setMobileMenuOpen(false);
    window.location.hash = `#/${page}`;
  }, []);

  useEffect(() => {
    const onHashChange = () => setActivePage(hashPage());
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  useEffect(() => {
    window.addEventListener("pms:unauthorized", logout);
    return () => window.removeEventListener("pms:unauthorized", logout);
  }, [logout]);

  useEffect(() => {
    if (!currentToken()) {
      setCheckingSession(false);
      return;
    }
    let active = true;
    api
      .me()
      .then((currentUser) => {
        if (active) setUser(currentUser);
      })
      .catch(() => {
        setToken("");
      })
      .finally(() => {
        if (active) setCheckingSession(false);
      });
    return () => {
      active = false;
    };
  }, []);

  if (checkingSession) {
    return (
      <div className="boot-screen">
        <span className="brand-mark">P</span>
        <span>正在准备你的工作台…</span>
      </div>
    );
  }

  if (!user) {
    return (
      <LoginView
        onSuccess={(payload) => {
          setToken(payload.token);
          setUser(payload.user);
          navigate("dashboard");
        }}
      />
    );
  }

  const currentPage = pages.find((page) => page.id === activePage) ?? pages[0]!;
  const avatar = (user.display_name || user.username).slice(0, 1);

  return (
    <div className="app-shell">
      <aside className={`sidebar${mobileMenuOpen ? " sidebar-open" : ""}`}>
        <a className="brand" href="#/dashboard" onClick={() => navigate("dashboard")}>
          <span className="brand-mark">P</span>
          <span>
            <strong>个人效能</strong>
            <small>PERSONAL SYSTEM</small>
          </span>
        </a>
        <div className="nav-caption">工作空间</div>
        <nav className="main-nav" aria-label="主导航">
          {pages.map((page) => (
            <button
              key={page.id}
              className={`nav-item${activePage === page.id ? " active" : ""}`}
              onClick={() => navigate(page.id)}
            >
              <span className="nav-icon">{page.icon}</span>
              <span>{page.label}</span>
              {activePage === page.id && <span className="nav-indicator" />}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="method-card">
            <span className="method-icon">✳</span>
            <strong>日执行 · 周复盘 · 月重构</strong>
            <p>让重要的事持续推进，让系统本身保持轻盈。</p>
          </div>
          <button className="user-menu" onClick={logout}>
            <span className="avatar">{avatar}</span>
            <span className="user-copy">
              <strong>{user.display_name || user.username}</strong>
              <small>退出登录</small>
            </span>
            <span className="logout-icon">↗</span>
          </button>
        </div>
      </aside>

      {mobileMenuOpen && (
        <button
          className="sidebar-backdrop"
          aria-label="关闭导航菜单"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}
      <main className="main-area">
        <header className="topbar">
          <button
            className="mobile-menu-button"
            aria-label="打开菜单"
            aria-expanded={mobileMenuOpen}
            onClick={() => setMobileMenuOpen((open) => !open)}
          >
            ☰
          </button>
          <div className="breadcrumb">
            <span>个人效能</span>
            <span className="crumb-separator">/</span>
            <strong>{currentPage.label}</strong>
          </div>
          <div className="topbar-right">
            <span className="today-chip">
              {new Date().toLocaleDateString("zh-CN", {
                month: "long",
                day: "numeric",
                weekday: "long",
              })}
            </span>
            <span className="top-avatar">{avatar}</span>
          </div>
        </header>

        <section className="page-content">
          {activePage === "dashboard" && (
            <DashboardView onNavigate={navigate} />
          )}
          {activePage === "daily" && <DailyView />}
          {activePage === "weekly" && <WeeklyView />}
          {activePage === "monthly" && <MonthlyView />}
        </section>
        <footer className="app-footer">
          个人效能系统 <span>·</span> 每天进步一点点
        </footer>
      </main>
    </div>
  );
}

export default App;
