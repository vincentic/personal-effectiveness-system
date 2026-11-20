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
import type { Domain, Flow, WeeklyInput, WeeklyRecord } from "../types";

type QuestionKey =
  | "q_skilled_block"
  | "q_low_value_time"
  | "q_estimate_gap"
  | "q_rest_connection"
  | "q_next_adjust";

const questions: { key: QuestionKey; label: string }[] = [
  { key: "q_skilled_block", label: "哪个组块本周变得更熟练？" },
  { key: "q_low_value_time", label: "哪件事耗时很多但价值较低？" },
  { key: "q_estimate_gap", label: "哪个时间盒的估算偏差最大？" },
  { key: "q_rest_connection", label: "本周有休息、快乐和真实连接吗？" },
  { key: "q_next_adjust", label: "下周要继续、调整或停止什么？" },
];

const domainLabels = [
  { domain: "work", label: "工作 / 项目" },
  { domain: "study", label: "学习 / 技能" },
  { domain: "body", label: "身体 / 精力" },
  { domain: "relations", label: "关系 / 生活" },
] as const;

const bucketOptions = [
  { value: "todo", label: "待办" },
  { value: "doing", label: "进行中" },
  { value: "done", label: "已完成" },
  { value: "remove", label: "删除 / 委托 / 延后" },
];

function currentMonday(): string {
  const date = new Date();
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10);
}

function blankFlow(seq: number): Flow {
  return { bucket: "todo", content: "", seq };
}

function blankForm(): WeeklyInput {
  return {
    week_start: currentMonday(),
    next_mit_1: "",
    next_mit_2: "",
    next_mit_3: "",
    deep_work_time: "",
    prep_env_materials: "",
    if_condition: "",
    then_action: "",
    q_skilled_block: false,
    q_low_value_time: false,
    q_estimate_gap: false,
    q_rest_connection: false,
    q_next_adjust: false,
    domains: domainLabels.map(({ domain }) => ({
      domain,
      facts: "",
      status: "smooth",
    })),
    flows: [blankFlow(1)],
  };
}

export default function WeeklyView() {
  const [records, setRecords] = useState<WeeklyRecord[]>([]);
  const [form, setForm] = useState<WeeklyInput>(blankForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      setRecords(await api.weekly());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "每周复盘加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function update<K extends keyof WeeklyInput>(key: K, value: WeeklyInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateDomain<K extends "facts" | "status">(
    index: number,
    key: K,
    value: Domain[K],
  ) {
    setForm((current) => ({
      ...current,
      domains: current.domains.map((domain, domainIndex) =>
        domainIndex === index ? { ...domain, [key]: value } : domain,
      ),
    }));
  }

  function updateFlow<K extends keyof Flow>(
    index: number,
    key: K,
    value: Flow[K],
  ) {
    setForm((current) => ({
      ...current,
      flows: current.flows.map((flow, flowIndex) =>
        flowIndex === index ? { ...flow, [key]: value } : flow,
      ),
    }));
  }

  function edit(record: WeeklyRecord) {
    setEditingId(record.id);
    setForm({
      ...record,
      domains: domainLabels.map(({ domain }) => {
        const current = record.domains.find((item) => item.domain === domain);
        return {
          domain,
          facts: current?.facts ?? "",
          status: current?.status ?? "smooth",
        };
      }),
      flows: record.flows.length
        ? record.flows.map((flow, index) => ({ ...flow, seq: index + 1 }))
        : [blankFlow(1)],
    });
    setNotice("");
    setError("");
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function reset() {
    setEditingId(null);
    setForm(blankForm());
    setError("");
    setNotice("");
  }

  function addFlow() {
    setForm((current) => ({
      ...current,
      flows: [...current.flows, blankFlow(current.flows.length + 1)],
    }));
  }

  function removeFlow(index: number) {
    setForm((current) => ({
      ...current,
      flows: current.flows
        .filter((_, flowIndex) => flowIndex !== index)
        .map((flow, flowIndex) => ({ ...flow, seq: flowIndex + 1 })),
    }));
  }

  async function save(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (saving) return;
    setSaving(true);
    setError("");
    setNotice("");
    try {
      const isEditing = editingId !== null;
      await api.saveWeekly(
        {
          ...form,
          flows: form.flows.map((flow, index) => ({
            ...flow,
            seq: index + 1,
          })),
        },
        editingId ?? undefined,
      );
      await load();
      reset();
      setNotice(isEditing ? "每周复盘已更新" : "每周复盘已保存");
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "保存失败，请检查输入后重试",
      );
    } finally {
      setSaving(false);
    }
  }

  async function remove(record: WeeklyRecord) {
    if (!window.confirm(`确定删除 ${record.week_start} 的每周复盘吗？`)) return;
    setError("");
    try {
      await api.deleteWeekly(record.id);
      if (editingId === record.id) reset();
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "删除失败");
    }
  }

  return (
    <>
      <PageHeading
        eyebrow="WEEKLY REVIEW"
        title="每周复盘"
        description="用事实回看这一周，为下一周留出更清晰的空间。"
      >
        <button className="button button-primary" onClick={reset}>
          ＋ 新建周复盘
        </button>
      </PageHeading>
      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <form className="editor panel" onSubmit={save}>
        <div className="panel-heading editor-heading">
          <div>
            <span className="eyebrow">
              {editingId ? "UPDATE YOUR REVIEW" : "LOOK BACK, MOVE FORWARD"}
            </span>
            <h2>{editingId ? "编辑每周复盘" : "本周回顾"}</h2>
          </div>
          <span className="editor-step">
            01 <i>/</i> 04
          </span>
        </div>
        <div className="form-grid form-grid-two">
          <InputField
            label="本周起始日（周一）"
            type="date"
            required
            value={form.week_start}
            onValueChange={(value) => update("week_start", value)}
          />
          <InputField
            label="要保护的深度时间"
            placeholder="例如：周二、周四上午"
            value={form.deep_work_time}
            onValueChange={(value) => update("deep_work_time", value)}
          />
        </div>

        <div className="section-title section-title-alone">
          <SectionTitle
            number="01"
            title="四个领域的事实"
            description="记录具体产出，不用评价自己。"
          />
        </div>
        <div className="domain-grid">
          {form.domains.map((domain, index) => (
            <article key={domain.domain} className="domain-card">
              <div className="domain-card-head">
                <span className="domain-number">
                  0{index + 1}
                </span>
                <strong>{domainLabels[index]?.label}</strong>
              </div>
              <TextAreaField
                label="本周事实 / 产出"
                rows={3}
                placeholder="记录发生了什么"
                value={domain.facts}
                onValueChange={(value) =>
                  updateDomain(index, "facts", value)
                }
              />
              <SelectField
                label="状态"
                value={domain.status}
                options={[
                  { value: "smooth", label: "顺利" },
                  { value: "blocked", label: "受阻" },
                  { value: "paused", label: "暂停" },
                ]}
                onValueChange={(value) =>
                  updateDomain(
                    index,
                    "status",
                    value as Domain["status"],
                  )
                }
              />
            </article>
          ))}
        </div>

        <div className="section-divider">
          <SectionTitle
            number="02"
            title="任务流整理"
            description="为任务设置出口，不让清单只增不减。"
          />
          <button
            className="button button-light button-small"
            type="button"
            onClick={addFlow}
          >
            ＋ 添加任务
          </button>
        </div>
        {!form.flows.length && (
          <div className="inline-empty">
            暂无任务。你可以添加一条下周需要推进的事项。
          </div>
        )}
        {form.flows.map((flow, index) => (
          <div key={index} className="flow-row">
            <select
              aria-label="任务状态"
              value={flow.bucket}
              onChange={(event) =>
                updateFlow(
                  index,
                  "bucket",
                  event.target.value as Flow["bucket"],
                )
              }
            >
              {bucketOptions.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <input
              value={flow.content}
              placeholder="任务或事项"
              onChange={(event) =>
                updateFlow(index, "content", event.target.value)
              }
            />
            <button
              className="text-button danger-text"
              type="button"
              aria-label="移除任务"
              onClick={() => removeFlow(index)}
            >
              ×
            </button>
          </div>
        ))}

        <div className="section-divider">
          <SectionTitle
            number="03"
            title="设计下周"
            description="只承诺少数真正重要的成果。"
          />
        </div>
        <div className="form-grid form-grid-three">
          <InputField
            label="下周成果 1"
            placeholder="最重要的一项"
            value={form.next_mit_1}
            onValueChange={(value) => update("next_mit_1", value)}
          />
          <InputField
            label="下周成果 2"
            placeholder="可选"
            value={form.next_mit_2}
            onValueChange={(value) => update("next_mit_2", value)}
          />
          <InputField
            label="下周成果 3"
            placeholder="可选"
            value={form.next_mit_3}
            onValueChange={(value) => update("next_mit_3", value)}
          />
        </div>
        <div className="form-grid form-grid-two">
          <TextAreaField
            label="提前准备的环境 / 材料"
            rows={2}
            value={form.prep_env_materials}
            onValueChange={(value) => update("prep_env_materials", value)}
          />
          <div className="if-then">
            <InputField
              label="如果……"
              placeholder="遇到什么情况"
              value={form.if_condition}
              onValueChange={(value) => update("if_condition", value)}
            />
            <InputField
              label="那么我就……"
              placeholder="采取什么行动"
              value={form.then_action}
              onValueChange={(value) => update("then_action", value)}
            />
          </div>
        </div>

        <div className="section-divider">
          <SectionTitle
            number="04"
            title="复盘问题"
            description="为下周带走一个有用的发现。"
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
                : "保存每周复盘"}
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
            <span className="eyebrow">WEEKLY NOTES</span>
            <h2>历史复盘</h2>
          </div>
          <span className="count-pill">{records.length} 条记录</span>
        </div>
        {loading && <div className="loading-card">正在加载每周复盘…</div>}
        {!loading && !records.length && (
          <EmptyState
            icon="↗"
            title="还没有每周复盘"
            description="花一点时间回顾事实，为下一周做轻量设计。"
            compact
          />
        )}
        {!loading && records.length > 0 && (
          <div className="record-list">
            {records.map((record) => (
              <article key={record.id} className="record-row">
                <div className="record-date">
                  <strong>{record.week_start.slice(5)}</strong>
                  <small>至 {record.week_end.slice(5)}</small>
                </div>
                <div className="record-main">
                  <strong>{record.next_mit_1 || "每周复盘"}</strong>
                  <small>
                    {record.week_start} — {record.week_end} ·{" "}
                    {record.flows.length} 条任务流
                  </small>
                </div>
                <span className="status-pill">
                  {record.domains.length} 个领域
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
