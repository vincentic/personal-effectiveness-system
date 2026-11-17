<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import type { Flow, WeeklyInput, WeeklyRecord } from "../types";

type QuestionKey =
  | "q_skilled_block" | "q_low_value_time" | "q_estimate_gap"
  | "q_rest_connection" | "q_next_adjust";

const questions: { key: QuestionKey; label: string }[] = [
  { key: "q_skilled_block", label: "哪个组块本周变得更熟练？" },
  { key: "q_low_value_time", label: "哪件事耗时很多但价值较低？" },
  { key: "q_estimate_gap", label: "哪个时间盒的估算偏差最大？" },
  { key: "q_rest_connection", label: "本周有休息、快乐和真实连接吗？" },
  { key: "q_next_adjust", label: "下周要继续、调整或停止什么？" },
];

const domainLabels = [
  { domain: "work" as const, label: "工作 / 项目" },
  { domain: "study" as const, label: "学习 / 技能" },
  { domain: "body" as const, label: "身体 / 精力" },
  { domain: "relations" as const, label: "关系 / 生活" },
];
const bucketLabels = [
  { value: "todo" as const, label: "待办" },
  { value: "doing" as const, label: "进行中" },
  { value: "done" as const, label: "已完成" },
  { value: "remove" as const, label: "删除 / 委托 / 延后" },
];

function currentMonday(): string {
  const date = new Date();
  date.setDate(date.getDate() - ((date.getDay() + 6) % 7));
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
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
    domains: domainLabels.map(({ domain }) => ({ domain, facts: "", status: "smooth" })),
    flows: [blankFlow(1)],
  };
}

const records = ref<WeeklyRecord[]>([]);
const form = ref<WeeklyInput>(blankForm());
const editingId = ref<number | null>(null);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const notice = ref("");

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    records.value = await api.weekly();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "每周复盘加载失败";
  } finally {
    loading.value = false;
  }
}

function edit(record: WeeklyRecord): void {
  editingId.value = record.id;
  form.value = {
    week_start: record.week_start,
    next_mit_1: record.next_mit_1,
    next_mit_2: record.next_mit_2,
    next_mit_3: record.next_mit_3,
    deep_work_time: record.deep_work_time,
    prep_env_materials: record.prep_env_materials,
    if_condition: record.if_condition,
    then_action: record.then_action,
    q_skilled_block: record.q_skilled_block,
    q_low_value_time: record.q_low_value_time,
    q_estimate_gap: record.q_estimate_gap,
    q_rest_connection: record.q_rest_connection,
    q_next_adjust: record.q_next_adjust,
    domains: domainLabels.map(({ domain }) => {
      const current = record.domains.find((item) => item.domain === domain);
      return { domain, facts: current?.facts ?? "", status: current?.status ?? "smooth" };
    }),
    flows: record.flows.length ? record.flows.map((flow, index) => ({ ...flow, seq: index + 1 })) : [blankFlow(1)],
  };
  error.value = "";
  notice.value = "";
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function reset(): void {
  editingId.value = null;
  form.value = blankForm();
  error.value = "";
  notice.value = "";
}

function addFlow(): void {
  form.value.flows.push(blankFlow(form.value.flows.length + 1));
}

function removeFlow(index: number): void {
  form.value.flows.splice(index, 1);
  form.value.flows.forEach((flow, i) => { flow.seq = i + 1; });
}

async function save(): Promise<void> {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    const isEditing = editingId.value !== null;
    await api.saveWeekly({
      ...form.value,
      flows: form.value.flows.map((flow, index) => ({ ...flow, seq: index + 1 })),
    }, editingId.value ?? undefined);
    await load();
    reset();
    notice.value = isEditing ? "每周复盘已更新" : "每周复盘已保存";
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "保存失败，请检查输入后重试";
  } finally {
    saving.value = false;
  }
}

async function remove(record: WeeklyRecord): Promise<void> {
  if (!window.confirm(`确定删除 ${record.week_start} 的每周复盘吗？`)) return;
  error.value = "";
  try {
    await api.deleteWeekly(record.id);
    if (editingId.value === record.id) reset();
    await load();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "删除失败";
  }
}

onMounted(load);
</script>

<template>
  <div class="page-heading">
    <div><span class="eyebrow">WEEKLY REVIEW</span><h1>每周复盘</h1><p>用事实回看这一周，为下一周留出更清晰的空间。</p></div>
    <button class="button button-primary" @click="reset">＋ 新建周复盘</button>
  </div>
  <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
  <div v-if="notice" class="alert alert-success">{{ notice }}</div>

  <form class="editor panel" @submit.prevent="save">
    <div class="panel-heading editor-heading">
      <div><span class="eyebrow">{{ editingId ? "UPDATE YOUR REVIEW" : "LOOK BACK, MOVE FORWARD" }}</span><h2>{{ editingId ? "编辑每周复盘" : "本周回顾" }}</h2></div>
      <span class="editor-step">01 <i>/</i> 04</span>
    </div>
    <div class="form-grid form-grid-two">
      <label class="field"><span>本周起始日（周一）</span><input v-model="form.week_start" type="date" required /></label>
      <label class="field"><span>要保护的深度时间</span><input v-model="form.deep_work_time" placeholder="例如：周二、周四上午" /></label>
    </div>
    <div class="section-title section-title-alone"><span class="section-number">01</span><div><h3>四个领域的事实</h3><p>记录具体产出，不用评价自己。</p></div></div>
    <div class="domain-grid">
      <article v-for="(domain, index) in form.domains" :key="domain.domain" class="domain-card">
        <div class="domain-card-head"><span class="domain-number">0{{ index + 1 }}</span><strong>{{ domainLabels[index].label }}</strong></div>
        <label class="field"><span>本周事实 / 产出</span><textarea v-model="domain.facts" rows="3" placeholder="记录发生了什么"></textarea></label>
        <label class="field"><span>状态</span><select v-model="domain.status"><option value="smooth">顺利</option><option value="blocked">受阻</option><option value="paused">暂停</option></select></label>
      </article>
    </div>

    <div class="section-divider">
      <div class="section-title"><span class="section-number">02</span><div><h3>任务流整理</h3><p>为任务设置出口，不让清单只增不减。</p></div></div>
      <button class="button button-light button-small" type="button" @click="addFlow">＋ 添加任务</button>
    </div>
    <div v-if="!form.flows.length" class="inline-empty">暂无任务。你可以添加一条下周需要推进的事项。</div>
    <div v-for="(flow, index) in form.flows" :key="index" class="flow-row">
      <select v-model="flow.bucket" aria-label="任务状态">
        <option v-for="item in bucketLabels" :key="item.value" :value="item.value">{{ item.label }}</option>
      </select>
      <input v-model="flow.content" placeholder="任务或事项" />
      <button class="text-button danger-text" type="button" aria-label="移除任务" @click="removeFlow(index)">×</button>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">03</span><div><h3>设计下周</h3><p>只承诺少数真正重要的成果。</p></div></div></div>
    <div class="form-grid form-grid-three">
      <label class="field"><span>下周成果 1</span><input v-model="form.next_mit_1" placeholder="最重要的一项" /></label>
      <label class="field"><span>下周成果 2</span><input v-model="form.next_mit_2" placeholder="可选" /></label>
      <label class="field"><span>下周成果 3</span><input v-model="form.next_mit_3" placeholder="可选" /></label>
    </div>
    <div class="form-grid form-grid-two">
      <label class="field"><span>提前准备的环境 / 材料</span><textarea v-model="form.prep_env_materials" rows="2"></textarea></label>
      <div class="if-then">
        <label class="field"><span>如果……</span><input v-model="form.if_condition" placeholder="遇到什么情况" /></label>
        <label class="field"><span>那么我就……</span><input v-model="form.then_action" placeholder="采取什么行动" /></label>
      </div>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">04</span><div><h3>复盘问题</h3><p>为下周带走一个有用的发现。</p></div></div></div>
    <div class="question-grid">
      <label v-for="item in questions" :key="item.key" class="check-line"><input v-model="form[item.key]" type="checkbox" /><span>{{ item.label }}</span></label>
    </div>
    <div class="form-actions"><button class="button button-primary" :disabled="saving">{{ saving ? "保存中…" : editingId ? "保存修改" : "保存每周复盘" }}</button><button v-if="editingId" class="button button-secondary" type="button" @click="reset">取消编辑</button></div>
  </form>

  <section class="panel records-panel">
    <div class="panel-heading"><div><span class="eyebrow">WEEKLY NOTES</span><h2>历史复盘</h2></div><span class="count-pill">{{ records.length }} 条记录</span></div>
    <div v-if="loading" class="loading-card">正在加载每周复盘…</div>
    <div v-else-if="!records.length" class="empty-state compact-empty"><span class="empty-illustration">↗</span><strong>还没有每周复盘</strong><p>花一点时间回顾事实，为下一周做轻量设计。</p></div>
    <div v-else class="record-list">
      <article v-for="record in records" :key="record.id" class="record-row">
        <div class="record-date"><strong>{{ record.week_start.slice(5) }}</strong><small>至 {{ record.week_end.slice(5) }}</small></div>
        <div class="record-main"><strong>{{ record.next_mit_1 || "每周复盘" }}</strong><small>{{ record.week_start }} — {{ record.week_end }} · {{ record.flows.length }} 条任务流</small></div>
        <span class="status-pill">{{ record.domains.length }} 个领域</span>
        <div class="record-actions"><button class="text-button" @click="edit(record)">编辑</button><button class="text-button danger-text" @click="remove(record)">删除</button></div>
      </article>
    </div>
  </section>
</template>
