import { useEffect, useState, type FormEvent } from "react";
import { api } from "../api";
import {
  Alert,
  CheckLine,
  EmptyState,
  InputField,
  PageHeading,
  SectionTitle,
  TextAreaField,
} from "../components";
import type { Block, DailyInput, DailyRecord } from "../types";

type CheckKey =
  | "pre_know_output"
  | "pre_materials_ready"
  | "pre_phone_handled"
  | "during_one_block"
  | "during_shrink_step"
  | "during_record_distraction"
  | "post_visible_output"
  | "post_recall"
  | "post_next_step"
  | "life_water_food"
  | "life_walk_exercise"
  | "life_clean_tidy"
  | "life_social_outdoor";

const checkGroups: {
  title: string;
  items: { key: CheckKey; label: string }[];
}[] = [
  {
    title: "开始前",
    items: [
      { key: "pre_know_output", label: "明确本轮交付成果" },
      { key: "pre_materials_ready", label: "材料已准备" },
      { key: "pre_phone_handled", label: "手机与通知已处理" },
    ],
  },
  {
    title: "执行中",
    items: [
      { key: "during_one_block", label: "一次只做一个组块" },
      { key: "during_shrink_step", label: "卡住时缩小下一步" },
      { key: "during_record_distraction", label: "记录干扰" },
    ],
  },
  {
    title: "结束后",
    items: [
      { key: "post_visible_output", label: "留下可见产出" },
      { key: "post_recall", label: "主动回忆或复述" },
      { key: "post_next_step", label: "标记下一步" },
    ],
  },
  {
    title: "生活维护",
    items: [
      { key: "life_water_food", label: "饮水与正常饮食" },
      { key: "life_walk_exercise", label: "走路或运动" },
      { key: "life_clean_tidy", label: "清洁或整理" },
      { key: "life_social_outdoor", label: "真实交流或户外" },
    ],
  },
];

function today(): string {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 10);
}

function blankBlock(seq: number): Block {
  return {
    seq,
    block_name: "",
    input_desc: "",
    key_action: "",
    deliverable: "",
    time_start: null,
    time_end: null,
    is_done: false,
  };
}

function blankForm(): DailyInput {
  return {
    plan_date: today(),
    sleep_hours: null,
    energy_level: "medium",
    mit: "",
    pre_know_output: false,
    pre_materials_ready: false,
    pre_phone_handled: false,
    during_one_block: false,
    during_shrink_step: false,
    during_record_distraction: false,
    post_visible_output: false,
    post_recall: false,
    post_next_step: false,
    life_water_food: false,
    life_walk_exercise: false,
    life_clean_tidy: false,
    life_social_outdoor: false,
    review_accomplished: "",
    review_resistance: "",
    review_tomorrow_start: "",
    review_gratitude: "",
    blocks: [blankBlock(1)],
  };
}

export default function DailyView() {
  const [records, setRecords] = useState<DailyRecord[]>([]);
  const [form, setForm] = useState<DailyInput>(blankForm);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

  async function load() {
    setLoading(true);
    setError("");
    try {
      setRecords(await api.daily());
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "每日清单加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  function update<K extends keyof DailyInput>(key: K, value: DailyInput[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function updateBlock<K extends keyof Block>(
    index: number,
    key: K,
    value: Block[K],
  ) {
    setForm((current) => ({
      ...current,
      blocks: current.blocks.map((block, blockIndex) =>
        blockIndex === index ? { ...block, [key]: value } : block,
      ),
    }));
  }

  function edit(record: DailyRecord) {
    setEditingId(record.id);
    setForm({
      ...record,
      blocks: record.blocks.map((block, index) => ({
        ...block,
        seq: index + 1,
      })),
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

  function addBlock() {
    setForm((current) => ({
      ...current,
      blocks: [...current.blocks, blankBlock(current.blocks.length + 1)],
    }));
  }

  function removeBlock(index: number) {
    setForm((current) => ({
      ...current,
      blocks: current.blocks
        .filter((_, blockIndex) => blockIndex !== index)
        .map((block, blockIndex) => ({ ...block, seq: blockIndex + 1 })),
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
      await api.saveDaily(
        {
          ...form,
          sleep_hours:
            form.sleep_hours === null ? null : Number(form.sleep_hours),
          blocks: form.blocks.map((block, index) => ({
            ...block,
            seq: index + 1,
          })),
        },
        editingId ?? undefined,
      );
      reset();
      await load();
      setNotice(isEditing ? "每日清单已更新" : "每日清单已保存");
    } catch (reason) {
      setError(
        reason instanceof Error ? reason.message : "保存失败，请检查输入后重试",
      );
    } finally {
      setSaving(false);
    }
  }

  async function remove(record: DailyRecord) {
    if (!window.confirm(`确定删除 ${record.plan_date} 的每日清单吗？`)) return;
    setError("");
    try {
      await api.deleteDaily(record.id);
      if (editingId === record.id) reset();
      await load();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "删除失败");
    }
  }

  return (
    <>
      <PageHeading
        eyebrow="DAILY EXECUTION"
        title="每日执行"
        description="先确定成果，再给注意力安排一个清晰的去处。"
      >
        <button className="button button-primary" onClick={reset}>
          ＋ 新建今日清单
        </button>
      </PageHeading>

      {error && <Alert tone="error">{error}</Alert>}
      {notice && <Alert tone="success">{notice}</Alert>}

      <form className="editor panel" onSubmit={save}>
        <div className="panel-heading editor-heading">
          <div>
            <span className="eyebrow">
              {editingId ? "UPDATE YOUR PLAN" : "MAKE TODAY COUNT"}
            </span>
            <h2>{editingId ? "编辑每日清单" : "今日方向"}</h2>
          </div>
          <span className="editor-step">
            01 <i>/</i> 04
          </span>
        </div>
        <div className="form-grid form-grid-three">
          <InputField
            label="日期"
            type="date"
            required
            value={form.plan_date}
            onValueChange={(value) => update("plan_date", value)}
          />
          <InputField
            label="睡眠小时"
            type="number"
            min="0"
            max="24"
            step="0.5"
            placeholder="例如 7.5"
            value={form.sleep_hours}
            onValueChange={(value) =>
              update("sleep_hours", value ? Number(value) : null)
            }
          />
          <label className="field">
            <span>当前精力</span>
            <select
              value={form.energy_level}
              onChange={(event) =>
                update(
                  "energy_level",
                  event.target.value as DailyInput["energy_level"],
                )
              }
            >
              <option value="low">偏低</option>
              <option value="medium">一般</option>
              <option value="high">充沛</option>
            </select>
          </label>
        </div>
        <TextAreaField
          label="今天最重要的成果 MIT"
          rows={2}
          placeholder="今天结束前，哪一个可见成果最值得完成？"
          value={form.mit}
          onValueChange={(value) => update("mit", value)}
        />

        <div className="section-divider">
          <SectionTitle
            number="02"
            title="时间盒组块"
            description="每个组块都写清关键动作与可验收输出。"
          />
          <button
            className="button button-light button-small"
            type="button"
            onClick={addBlock}
          >
            ＋ 添加组块
          </button>
        </div>
        {!form.blocks.length && (
          <div className="inline-empty">
            还没有组块，点击「添加组块」开始安排。
          </div>
        )}
        {form.blocks.map((block, index) => (
          <div key={index} className="block-editor">
            <div className="block-editor-title">
              <span>组块 {String(index + 1).padStart(2, "0")}</span>
              <button
                className="text-button danger-text"
                type="button"
                onClick={() => removeBlock(index)}
              >
                移除
              </button>
            </div>
            <div className="form-grid form-grid-two">
              <InputField
                label="组块 / 任务"
                required
                placeholder="例如：完成方案初稿"
                value={block.block_name}
                onValueChange={(value) =>
                  updateBlock(index, "block_name", value)
                }
              />
              <div className="time-fields">
                <InputField
                  label="开始"
                  type="time"
                  value={block.time_start}
                  onValueChange={(value) =>
                    updateBlock(index, "time_start", value || null)
                  }
                />
                <InputField
                  label="结束"
                  type="time"
                  value={block.time_end}
                  onValueChange={(value) =>
                    updateBlock(index, "time_end", value || null)
                  }
                />
              </div>
            </div>
            <div className="form-grid form-grid-three">
              <InputField
                label="输入"
                placeholder="需要的信息或材料"
                value={block.input_desc}
                onValueChange={(value) =>
                  updateBlock(index, "input_desc", value)
                }
              />
              <InputField
                label="关键动作"
                placeholder="这段时间具体做什么"
                value={block.key_action}
                onValueChange={(value) =>
                  updateBlock(index, "key_action", value)
                }
              />
              <InputField
                label="可验收输出"
                placeholder="结束时留下什么"
                value={block.deliverable}
                onValueChange={(value) =>
                  updateBlock(index, "deliverable", value)
                }
              />
            </div>
            <CheckLine
              checked={block.is_done}
              label="已完成"
              onChange={(value) => updateBlock(index, "is_done", value)}
            />
          </div>
        ))}

        <div className="section-divider">
          <SectionTitle
            number="03"
            title="执行与生活检查"
            description="用小检查保护专注，也照顾生活基本盘。"
          />
        </div>
        <div className="check-groups">
          {checkGroups.map((group) => (
            <fieldset key={group.title} className="check-group">
              <legend>{group.title}</legend>
              {group.items.map((item) => (
                <CheckLine
                  key={item.key}
                  checked={form[item.key]}
                  label={item.label}
                  onChange={(value) => update(item.key, value)}
                />
              ))}
            </fieldset>
          ))}
        </div>

        <div className="section-divider">
          <SectionTitle
            number="04"
            title="晚间 5 分钟复盘"
            description="记录事实、阻力和明天可以采取的最小动作。"
          />
        </div>
        <div className="form-grid form-grid-two">
          <TextAreaField
            label="今天真正完成了什么"
            rows={2}
            value={form.review_accomplished}
            onValueChange={(value) => update("review_accomplished", value)}
          />
          <TextAreaField
            label="今天最大的阻力"
            rows={2}
            value={form.review_resistance}
            onValueChange={(value) => update("review_resistance", value)}
          />
          <TextAreaField
            label="明天最小的启动动作"
            rows={2}
            value={form.review_tomorrow_start}
            onValueChange={(value) => update("review_tomorrow_start", value)}
          />
          <TextAreaField
            label="今天值得感谢的一件事"
            rows={2}
            value={form.review_gratitude}
            onValueChange={(value) => update("review_gratitude", value)}
          />
        </div>
        <div className="form-actions">
          <button className="button button-primary" disabled={saving}>
            {saving
              ? "保存中…"
              : editingId
                ? "保存修改"
                : "保存每日清单"}
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
            <span className="eyebrow">YOUR DAILY LOG</span>
            <h2>历史清单</h2>
          </div>
          <span className="count-pill">{records.length} 条记录</span>
        </div>
        {loading && <div className="loading-card">正在加载每日清单…</div>}
        {!loading && !records.length && (
          <EmptyState
            icon="◷"
            title="还没有每日记录"
            description="从上方开始，建立你的第一个清晰日程。"
            compact
          />
        )}
        {!loading && records.length > 0 && (
          <div className="record-list">
            {records.map((record) => (
              <article key={record.id} className="record-row">
                <div className="record-date">
                  <strong>{record.plan_date.slice(5)}</strong>
                  <small>{record.plan_date.slice(0, 4)}</small>
                </div>
                <div className="record-main">
                  <strong>{record.mit || "每日执行清单"}</strong>
                  <small>
                    {record.blocks_done}/{record.blocks_total} 个组块完成 · 检查{" "}
                    {record.checklist_done}/{record.checklist_total}
                  </small>
                </div>
                <span className={`energy-pill energy-${record.energy_level}`}>
                  {{ low: "偏低", medium: "一般", high: "充沛" }[
                    record.energy_level
                  ]}
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
