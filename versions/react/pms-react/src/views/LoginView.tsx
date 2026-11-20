import { useState, type FormEvent } from "react";
import { api } from "../api";
import type { User } from "../types";

export default function LoginView({
  onSuccess,
}: {
  onSuccess: (payload: { token: string; user: User }) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (loading) return;
    setLoading(true);
    setError("");
    try {
      const result = await api.login(username.trim(), password);
      onSuccess(result);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "登录失败，请稍后重试");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-layout">
      <section className="login-story">
        <div className="login-brand">
          <span className="brand-mark">P</span>
          <strong>个人效能系统</strong>
        </div>
        <div className="story-content">
          <span className="eyebrow">PERSONAL EFFECTIVENESS SYSTEM</span>
          <h1>
            把时间还给
            <br />
            <em>真正重要的事。</em>
          </h1>
          <p>用日执行、周复盘、月重构，建立清晰而可持续的个人节奏。</p>
          <div className="story-points">
            <span>
              <i>01</i> 每天聚焦一个关键成果
            </span>
            <span>
              <i>02</i> 每周让任务流动起来
            </span>
            <span>
              <i>03</i> 每月调整值得投入的方向
            </span>
          </div>
        </div>
        <div className="story-foot">清晰一点，轻盈一点，持续一点。</div>
      </section>
      <section className="login-panel">
        <form className="login-card" onSubmit={submit}>
          <span className="eyebrow">WELCOME BACK</span>
          <h2>登录你的工作台</h2>
          <p className="login-subtitle">继续构建属于自己的效能系统。</p>
          <label className="field-label" htmlFor="username">
            用户名
          </label>
          <input
            id="username"
            autoComplete="username"
            autoFocus
            required
            placeholder="输入用户名"
            value={username}
            onChange={(event) => setUsername(event.target.value)}
          />
          <label className="field-label" htmlFor="password">
            密码
          </label>
          <input
            id="password"
            autoComplete="current-password"
            required
            type="password"
            placeholder="输入密码"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <button className="button button-primary login-button" disabled={loading}>
            {loading ? "正在登录…" : "登录工作台"} <span>→</span>
          </button>
          <p className="login-hint">使用所连接 API 配置的管理员账号或已创建的用户登录。</p>
        </form>
        <div className="login-foot">
          安全连接 <span>·</span> 数据按账号隔离
        </div>
      </section>
    </main>
  );
}
