import { useEffect, useState, type FormEvent } from "react";
import { api } from "../api";
import {
  Alert,
  CheckLine,
  EmptyState,
  InputField,
  PageHeading,
  SectionTitle,
  SelectField,
  TextAreaField,
} from "../components";
import type { HealthCheck, MonthlyInput, MonthlyRecord } from "../types";

type QuestionKey =
  | "q_skill_portfolio"
  | "q_career_alignment"
  | "q_life_autonomy"
  | "q_recovery_relations"
  | "q_longterm_decision";

const questions: { key: QuestionKey; label: string }[] = [
  { key: "q_skill_portfolio", label: "技能是否形成了可展示的作品？" },
  { key: "q_career_alignment", label: "工作是否更接近长期方向？" },
  { key: "q_life_autonomy", label: "生活是否更有自主性？" },
  { key: "q_recovery_relations", label: "恢复与关系投入是否足够？" },
  { key: "q_longterm_decision", label: "长期投资是否需要继续、转向或停止？" },
];

const modules = [
  { key: "direction", label: "方向清晰度" },
  { key: "taskload", label: "任务负荷 / 在制任务" },
  { key: "blockquality", label: "组块质量" },
  { key: "timebox", label: "时间盒现实程度" },
  { key: "sleepenergy", label: "睡眠 / 体能 / 情绪" },
  { key: "reviewquality", label: "复盘与反馈质量" },
] as const;

function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}`;
}

function blankHealth(module: HealthCheck["module"]): HealthCheck {
  return { module, score: null, needed_change: "" };
}

function blankForm(): MonthlyInput {
  return {
    month: currentMonth(),
    top_achievement: "",
    worth_continue: "yes",
    worth_reason: "",
    most_valuable_input: "",
    biggest_opportunity_cost: "",
    keep_item: "",
    cut_item: "",
    experiment_item: "",
    q_skill_portfolio: false,
    q_career_alignment: false,
    q_life_autonomy: false,
    q_recovery_relations: false,
    q_longterm_decision: false,
    health_checks: modules.map(({ key }) => blankHealth(key)),
  };
}

export default function MonthlyView() {
  const [records, setRecords] = useState<MonthlyRecord[]>([]);
  const [form, setForm] = useState<MonthlyInput>(blankForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      setRecords(await api.monthly());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "每月重构加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function update<K extends keyof MonthlyInput>(
    key: K,
    value: MonthlyInput[K],
  ) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateHealth<K extends keyof HealthCheck>(
    index: number,
    key: K,
    value: HealthCheck[K],
  ) {
    setForm((current) => ({
      ...current,
      health_checks: current.health_checks.map((item, itemIndex) =>
        itemIndex === index ? { ...item, [key]: value } : item,
      ),
    }));
  }

  function edit(record: MonthlyRecord) {
    setEditingId(record.id);
    setForm({
      ...record,
      health_checks: modules.map(({ key }) => {
        const current = record.health_checks.find(
          (item) => item.module === key,
        );
        return current ? { ...current } : blankHealth(key);
      }),
    });
    setError("");
    setNotice("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function reset() {
    setEditingId(null);
    setForm(blankForm());
    setError("");
    setNotice("");
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const isEditing = editingId !== null;
      await api.saveMonthly(
        {
          ...form,
          health_checks: form.health_checks.map((item) => ({
            ...item,
            score: item.score === null ? null : Number(item.score),
          })),
        },
        editingId ?? undefined,
      );
      await load();
      reset();
      setNotice(isEditing ? "每月重构已更新" : "每月重构已保存");
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "保存失败，请检查输入后重试",
      );
    } finally {
      setSaving(false);
    }
  }

  async function remove(record: MonthlyRecord) {
    if (!window.confirm(`确定删除 ${record.month} 的每月重构吗？`)) return;
    setError("");
    try {
      await api.deleteMonthly(record.id);
      if (editingId === record.id) reset();
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "删除失败");
    }
  }

  return (
    <>
      <PageHeading
        eyebrow="MONTHLY RESET"
        title="每月重构"
        description="回到目标与价值，只保留能让系统更好的改变。"
      >
        <button className="button button-primary" onClick={reset}>
          ＋ 新建月重构
        </button>
      </PageHeading>
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <form className="editor panel" onSubmit={save}>
        <div className="panel-heading editor-heading">
          <div>
            <span className="eyebrow">
              {editingId ? "UPDATE YOUR RESET" : "REVIEW & REFOCUS"}
            </span>
            <h2>{editingId ? "编辑每月重构" : "目标与价值检查"}</h2>
          </div>
          <span className="editor-step">
            01 <i>/</i> 04
          </span>
        </div>
        <div className="form-grid form-grid-two">
          <InputField
            label="月份"
            type="month"
            required
            value={form.month}
            onValueChange={(value) => update("month", value)}
          />
          <InputField
            label="本月最重要的成果"
            placeholder="留下最值得记住的进展"
            value={form.top_achievement}
            onValueChange={(value) => update("top_achievement", value)}
          />
        </div>
        <div className="form-grid form-grid-three">
          <SelectField
            label="它是否仍值得投入？"
            value={form.worth_continue}
            options={[
              { value: "yes", label: "继续" },
              { value: "adjust", label: "调整" },
              { value: "stop", label: "停止" },
            ]}
            onValueChange={(value) =>
              update("worth_continue", value as MonthlyInput["worth_continue"])
            }
          />
          <InputField
            label="判断原因"
            placeholder="是什么让你这样判断"
            value={form.worth_reason}
            onValueChange={(value) => update("worth_reason", value)}
          />
          <InputField
            label="本月最有价值的投入"
            placeholder="时间 / 精力 / 资源"
            value={form.most_valuable_input}
            onValueChange={(value) => update("most_valuable_input", value)}
          />
        </div>
        <TextAreaField
          label="本月最大的机会成本"
          rows={2}
          value={form.biggest_opportunity_cost}
          onValueChange={(value) => update("biggest_opportunity_cost", value)}
        />

        <div className="section-divider">
          <SectionTitle
            number="02"
            title="系统健康检查"
            description="给六个模块打分，并写下一个值得尝试的改变。"
          />
        </div>
        <div className="health-grid">
          {form.health_checks.map((item, index) => (
            <article key={item.module} className="health-card">
              <div className="health-card-title">
                <span className="domain-number">0{index + 1}</span>
                <strong>{modules[index]?.label}</strong>
              </div>
              <SelectField
                label="自评（1–5）"
                value={item.score === null ? "" : String(item.score)}
                options={[
                  { value: "", label: "暂不评分" },
                  ...[1, 2, 3, 4, 5].map((score) => ({
                    value: String(score),
                    label: `${score} 分`,
                  })),
                ]}
                onValueChange={(value) =>
                  updateHealth(index, "score", value ? Number(value) : null)
                }
              />
              <InputField
                label="需要的改变"
                placeholder="写下最小的改善动作"
                value={item.needed_change}
                onValueChange={(value) =>
                  updateHealth(index, "needed_change", value)
                }
              />
            </article>
          ))}
        </div>

        <div className="section-divider">
          <SectionTitle
            number="03"
            title="下月三项改进"
            description="有效的保留，低价值的删减，新的小实验。"
          />
        </div>
        <div className="form-grid form-grid-three">
          <TextAreaField
            label="保留"
            rows={3}
            placeholder="什么做法值得延续？"
            value={form.keep_item}
            onValueChange={(value) => update("keep_item", value)}
          />
          <TextAreaField
            label="删减"
            rows={3}
            placeholder="什么可以减少或停止？"
            value={form.cut_item}
            onValueChange={(value) => update("cut_item", value)}
          />
          <TextAreaField
            label="实验"
            rows={3}
            placeholder="下月尝试的最小改变"
            value={form.experiment_item}
            onValueChange={(value) => update("experiment_item", value)}
          />
        </div>

        <div className="section-divider">
          <SectionTitle
            number="04"
            title="季度方向校准"
            description="选中与你当前情况相符的观察。"
          />
        </div>
        <div className="question-grid">
          {questions.map((question) => (
            <CheckLine
              key={question.key}
              checked={form[question.key]}
              label={question.label}
              onChange={(value) => update(question.key, value)}
            />
          ))}
        </div>
        <div className="form-actions">
          <button className="button button-primary" disabled={saving}>
            {saving
              ? "保存中…"
              : editingId
                ? "保存修改"
                : "保存每月重构"}
          </button>
          {editingId !== null && (
            <button
              className="button button-secondary"
              type="button"
              onClick={reset}
            >
              取消编辑
            </button>
          )}
        </div>
      </form>

      <section className="panel records-panel">
        <div className="panel-heading">
          <div>
            <span className="eyebrow">MONTHLY RESET LOG</span>
            <h2>历史重构</h2>
          </div>
          <span className="count-pill">{records.length} 条记录</span>
        </div>
        {loading && <div className="loading-card">正在加载每月重构…</div>}
        {!loading && !records.length && (
          <EmptyState
            icon="◫"
            title="还没有每月重构"
            description="每个月留一点时间，重新校准目标和系统。"
            compact
          />
        )}
        {!loading && records.length > 0 && (
          <div className="record-list">
            {records.map((record) => (
              <article key={record.id} className="record-row">
                <div className="record-date">
                  <strong>{record.month.slice(5)}</strong>
                  <small>{record.month.slice(0, 4)}</small>
                </div>
                <div className="record-main">
                  <strong>{record.top_achievement || "每月重构"}</strong>
                  <small>
                    {{
                      yes: "继续投入",
                      adjust: "调整方向",
                      stop: "停止投入",
                    }[record.worth_continue]}{" "}
                    · 健康均分 {record.avg_score ?? "—"}
                  </small>
                </div>
                <span
                  className={`status-pill decision-${record.worth_continue}`}
                >
                  {{
                    yes: "继续",
                    adjust: "调整",
                    stop: "停止",
                  }[record.worth_continue]}
                </span>
                <div className="record-actions">
                  <button className="text-button" onClick={() => edit(record)}>
                    编辑
                  </button>
                  <button
                    className="text-button danger-text"
                    onClick={() => void remove(record)}
                  >
                    删除
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </>
  );
}
