import { useEffect, useState } from "react";
import { api } from "../api";
import { EmptyState, PageHeading } from "../components";
import type { Dashboard } from "../types";

type Page = "dashboard" | "daily" | "weekly" | "monthly";

function localDate(): string {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10);
}

export default function DashboardView({
  onNavigate,
}: {
  onNavigate: (page: Page) => void;
}) {
  const [data, setData] = useState<Dashboard | null>(null);
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    setError("");
    try {
      setData(await api.dashboard());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "工作台暂时无法加载");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  return (
    <>
      <PageHeading
        eyebrow="YOUR PERSONAL RHYTHM"
        title="今天，先做好一件重要的事。"
        description="把注意力放回当下，让进展清晰可见。"
      >
        <button
          className="button button-primary"
          onClick={() => onNavigate("daily")}
        >
          开始今日执行 <span>→</span>
        </button>
      </PageHeading>

      {error && (
        <div className="alert alert-error" role="alert">
          {error} <button onClick={() => void load()}>重试</button>
        </div>
      )}
      {loading && <div className="loading-card">正在汇总你的效能数据…</div>}
      {!loading && !error && data && (
        <>
          <section className="stat-grid">
            <article className="stat-card stat-highlight">
              <div className="stat-top">
                <span>本周时间盒</span>
                <span className="stat-symbol">◷</span>
              </div>
              <strong>
                {data.block_done}
                <small> / {data.block_total}</small>
              </strong>
              <div className="stat-progress">
                <span
                  style={{
                    width: `${
                      data.block_total
                        ? Math.round((data.block_done / data.block_total) * 100)
                        : 0
                    }%`,
                  }}
                />
              </div>
              <small className="stat-note">最近 7 条每日清单的完成情况</small>
            </article>
            <StatCard
              title="每日执行"
              symbol="▤"
              value={data.daily_count}
              note="持续积累的每日记录"
            />
            <StatCard
              title="每周复盘"
              symbol="↗"
              value={data.weekly_count}
              note="让任务流动起来"
            />
            <StatCard
              title="每月重构"
              symbol="◫"
              value={data.monthly_count}
              note="把精力投向值得的方向"
            />
          </section>

          <section className="dashboard-grid">
            <article className="panel today-panel">
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">TODAY</span>
                  <h2>今日重点</h2>
                </div>
                <span className="date-pill">{localDate()}</span>
              </div>
              {data.today_plan ? (
                <>
                  <div className="mit-box">
                    <span>最重要成果 · MIT</span>
                    <strong>
                      {data.today_plan.mit || "还没有填写今日成果"}
                    </strong>
                  </div>
                  <div className="inline-progress">
                    <div>
                      <span>组块进度</span>
                      <strong>
                        {data.today_plan.blocks_done} /{" "}
                        {data.today_plan.blocks_total}
                      </strong>
                    </div>
                    <div className="progress-track">
                      <span
                        style={{
                          width: `${data.today_plan.block_progress}%`,
                        }}
                      />
                    </div>
                  </div>
                  <button
                    className="button button-secondary"
                    onClick={() => onNavigate("daily")}
                  >
                    查看今日清单 <span>→</span>
                  </button>
                </>
              ) : (
                <>
                  <EmptyState
                    icon="✳"
                    title="今天还没有计划"
                    description="用几分钟写下 MIT 和时间盒，开始一个清晰的日子。"
                  />
                  <button
                    className="button button-primary"
                    onClick={() => onNavigate("daily")}
                  >
                    填写今日清单 <span>→</span>
                  </button>
                </>
              )}
            </article>

            <article className="panel rhythm-panel">
              <div className="panel-heading">
                <div>
                  <span className="eyebrow">KEEP THE LOOP</span>
                  <h2>保持复盘节奏</h2>
                </div>
              </div>
              <button
                className="rhythm-row"
                onClick={() => onNavigate("weekly")}
              >
                <span className="rhythm-icon weekly-icon">↗</span>
                <span>
                  <strong>每周复盘</strong>
                  <small>
                    {data.week_review
                      ? `本周已记录 · ${data.week_review.week_start}`
                      : "回顾事实，设计下周"}
                  </small>
                </span>
                <span className="rhythm-arrow">→</span>
              </button>
              <button
                className="rhythm-row"
                onClick={() => onNavigate("monthly")}
              >
                <span className="rhythm-icon monthly-icon">◫</span>
                <span>
                  <strong>每月重构</strong>
                  <small>
                    {data.month_review
                      ? `本月健康分 · ${data.month_review.avg_score ?? "—"}`
                      : "校准目标，调整系统"}
                  </small>
                </span>
                <span className="rhythm-arrow">→</span>
              </button>
              <div className="quote-box">
                “你不需要做更多，
                <br />
                只需要更清楚地做重要的事。”
              </div>
            </article>
          </section>

          <section className="panel recent-panel">
            <div className="panel-heading">
              <div>
                <span className="eyebrow">RECENT ACTIVITY</span>
                <h2>最近记录</h2>
              </div>
            </div>
            <div className="recent-grid">
              <RecentColumn
                title="每日执行"
                dotClass="daily-dot"
                empty="还没有每日记录"
                records={data.recent_daily.slice(0, 3).map((record) => ({
                  id: record.id,
                  date: record.plan_date,
                  title: record.mit || "每日执行清单",
                  detail: `${record.blocks_done}/${record.blocks_total} 组块`,
                }))}
                onClick={() => onNavigate("daily")}
              />
              <RecentColumn
                title="每周复盘"
                dotClass="weekly-dot"
                empty="还没有每周复盘"
                records={data.recent_weekly.slice(0, 3).map((record) => ({
                  id: record.id,
                  date: `${record.week_start} — ${record.week_end}`,
                  title: record.next_mit_1 || "每周复盘",
                  detail: "下周成果",
                }))}
                onClick={() => onNavigate("weekly")}
              />
              <RecentColumn
                title="每月重构"
                dotClass="monthly-dot"
                empty="还没有每月重构"
                records={data.recent_monthly.slice(0, 3).map((record) => ({
                  id: record.id,
                  date: record.month,
                  title: record.top_achievement || "每月重构",
                  detail: `健康均分 ${record.avg_score ?? "—"}`,
                }))}
                onClick={() => onNavigate("monthly")}
              />
            </div>
          </section>
        </>
      )}
    </>
  );
}

function StatCard({
  title,
  symbol,
  value,
  note,
}: {
  title: string;
  symbol: string;
  value: number;
  note: string;
}) {
  return (
    <article className="stat-card">
      <div className="stat-top">
        <span>{title}</span>
        <span className="stat-symbol">{symbol}</span>
      </div>
      <strong>{value}</strong>
      <small className="stat-note">{note}</small>
    </article>
  );
}

function RecentColumn({
  title,
  dotClass,
  empty,
  records,
  onClick,
}: {
  title: string;
  dotClass: string;
  empty: string;
  records: { id: number; date: string; title: string; detail: string }[];
  onClick: () => void;
}) {
  return (
    <div className="recent-column">
      <div className="recent-title">
        <span className={`recent-dot ${dotClass}`} />
        {title}
      </div>
      {records.map((record) => (
        <button
          key={record.id}
          className="recent-item"
          onClick={onClick}
        >
          <span>{record.date}</span>
          <strong>{record.title}</strong>
          <small>{record.detail}</small>
        </button>
      ))}
      {!records.length && <p className="muted-copy">{empty}</p>}
    </div>
  );
}
