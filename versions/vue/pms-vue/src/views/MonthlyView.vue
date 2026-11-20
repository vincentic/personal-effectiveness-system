<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import type { HealthCheck, MonthlyInput, MonthlyRecord } from "../types";

type QuestionKey =
  | "q_skill_portfolio" | "q_career_alignment" | "q_life_autonomy"
  | "q_recovery_relations" | "q_longterm_decision";

const questions: { key: QuestionKey; label: string }[] = [
  { key: "q_skill_portfolio", label: "技能是否形成了可展示的作品？" },
  { key: "q_career_alignment", label: "工作是否更接近长期方向？" },
  { key: "q_life_autonomy", label: "生活是否更有自主性？" },
  { key: "q_recovery_relations", label: "恢复与关系投入是否足够？" },
  { key: "q_longterm_decision", label: "长期投资是否需要继续、转向或停止？" },
];

const modules = [
  { key: "direction" as const, label: "方向清晰度" },
  { key: "taskload" as const, label: "任务负荷 / 在制任务" },
  { key: "blockquality" as const, label: "组块质量" },
  { key: "timebox" as const, label: "时间盒现实程度" },
  { key: "sleepenergy" as const, label: "睡眠 / 体能 / 情绪" },
  { key: "reviewquality" as const, label: "复盘与反馈质量" },
];

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

const records = ref<MonthlyRecord[]>([]);
const form = ref<MonthlyInput>(blankForm());
const editingId = ref<number | null>(null);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const notice = ref("");

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    records.value = await api.monthly();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "每月重构加载失败";
  } finally {
    loading.value = false;
  }
}

function edit(record: MonthlyRecord): void {
  editingId.value = record.id;
  form.value = {
    month: record.month,
    top_achievement: record.top_achievement,
    worth_continue: record.worth_continue,
    worth_reason: record.worth_reason,
    most_valuable_input: record.most_valuable_input,
    biggest_opportunity_cost: record.biggest_opportunity_cost,
    keep_item: record.keep_item,
    cut_item: record.cut_item,
    experiment_item: record.experiment_item,
    q_skill_portfolio: record.q_skill_portfolio,
    q_career_alignment: record.q_career_alignment,
    q_life_autonomy: record.q_life_autonomy,
    q_recovery_relations: record.q_recovery_relations,
    q_longterm_decision: record.q_longterm_decision,
    health_checks: modules.map(({ key }) => {
      const current = record.health_checks.find((item) => item.module === key);
      return current ? { ...current } : blankHealth(key);
    }),
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

function setScore(index: number, event: Event): void {
  const value = (event.target as HTMLSelectElement).value;
  form.value.health_checks[index].score = value ? Number(value) : null;
}

async function save(): Promise<void> {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  try {
    const isEditing = editingId.value !== null;
    await api.saveMonthly({
      ...form.value,
      health_checks: form.value.health_checks.map((item) => ({
        ...item,
        score: item.score === null ? null : Number(item.score),
      })),
    }, editingId.value ?? undefined);
    await load();
    reset();
    notice.value = isEditing ? "每月重构已更新" : "每月重构已保存";
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "保存失败，请检查输入后重试";
  } finally {
    saving.value = false;
  }
}

async function remove(record: MonthlyRecord): Promise<void> {
  if (!window.confirm(`确定删除 ${record.month} 的每月重构吗？`)) return;
  error.value = "";
  try {
    await api.deleteMonthly(record.id);
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
    <div><span class="eyebrow">MONTHLY RESET</span><h1>每月重构</h1><p>回到目标与价值，只保留能让系统更好的改变。</p></div>
    <button class="button button-primary" @click="reset">＋ 新建月重构</button>
  </div>
  <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
  <div v-if="notice" class="alert alert-success">{{ notice }}</div>

  <form class="editor panel" @submit.prevent="save">
    <div class="panel-heading editor-heading">
      <div><span class="eyebrow">{{ editingId ? "UPDATE YOUR RESET" : "REVIEW & REFOCUS" }}</span><h2>{{ editingId ? "编辑每月重构" : "目标与价值检查" }}</h2></div>
      <span class="editor-step">01 <i>/</i> 04</span>
    </div>
    <div class="form-grid form-grid-two">
      <label class="field"><span>月份</span><input v-model="form.month" type="month" required /></label>
      <label class="field"><span>本月最重要的成果</span><input v-model="form.top_achievement" placeholder="留下最值得记住的进展" /></label>
    </div>
    <div class="form-grid form-grid-three">
      <label class="field"><span>它是否仍值得投入？</span><select v-model="form.worth_continue"><option value="yes">继续</option><option value="adjust">调整</option><option value="stop">停止</option></select></label>
      <label class="field"><span>判断原因</span><input v-model="form.worth_reason" placeholder="是什么让你这样判断" /></label>
      <label class="field"><span>本月最有价值的投入</span><input v-model="form.most_valuable_input" placeholder="时间 / 精力 / 资源" /></label>
    </div>
    <label class="field"><span>本月最大的机会成本</span><textarea v-model="form.biggest_opportunity_cost" rows="2"></textarea></label>

    <div class="section-divider"><div class="section-title"><span class="section-number">02</span><div><h3>系统健康检查</h3><p>给六个模块打分，并写下一个值得尝试的改变。</p></div></div></div>
    <div class="health-grid">
      <article v-for="(item, index) in form.health_checks" :key="item.module" class="health-card">
        <div class="health-card-title"><span class="domain-number">0{{ index + 1 }}</span><strong>{{ modules[index].label }}</strong></div>
        <label class="field"><span>自评（1–5）</span><select :value="item.score ?? ''" @change="setScore(index, $event)"><option value="">暂不评分</option><option v-for="score in 5" :key="score" :value="score">{{ score }} 分</option></select></label>
        <label class="field"><span>需要的改变</span><input v-model="item.needed_change" placeholder="写下最小的改善动作" /></label>
      </article>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">03</span><div><h3>下月三项改进</h3><p>有效的保留，低价值的删减，新的小实验。</p></div></div></div>
    <div class="form-grid form-grid-three">
      <label class="field"><span>保留</span><textarea v-model="form.keep_item" rows="3" placeholder="什么做法值得延续？"></textarea></label>
      <label class="field"><span>删减</span><textarea v-model="form.cut_item" rows="3" placeholder="什么可以减少或停止？"></textarea></label>
      <label class="field"><span>实验</span><textarea v-model="form.experiment_item" rows="3" placeholder="下月尝试的最小改变"></textarea></label>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">04</span><div><h3>季度方向校准</h3><p>选中与你当前情况相符的观察。</p></div></div></div>
    <div class="question-grid">
      <label v-for="item in questions" :key="item.key" class="check-line"><input v-model="form[item.key]" type="checkbox" /><span>{{ item.label }}</span></label>
    </div>
    <div class="form-actions"><button class="button button-primary" :disabled="saving">{{ saving ? "保存中…" : editingId ? "保存修改" : "保存每月重构" }}</button><button v-if="editingId" class="button button-secondary" type="button" @click="reset">取消编辑</button></div>
  </form>

  <section class="panel records-panel">
    <div class="panel-heading"><div><span class="eyebrow">MONTHLY RESET LOG</span><h2>历史重构</h2></div><span class="count-pill">{{ records.length }} 条记录</span></div>
    <div v-if="loading" class="loading-card">正在加载每月重构…</div>
    <div v-else-if="!records.length" class="empty-state compact-empty"><span class="empty-illustration">◫</span><strong>还没有每月重构</strong><p>每个月留一点时间，重新校准目标和系统。</p></div>
    <div v-else class="record-list">
      <article v-for="record in records" :key="record.id" class="record-row">
        <div class="record-date"><strong>{{ record.month.slice(5) }}</strong><small>{{ record.month.slice(0, 4) }}</small></div>
        <div class="record-main"><strong>{{ record.top_achievement || "每月重构" }}</strong><small>{{ { yes: "继续投入", adjust: "调整方向", stop: "停止投入" }[record.worth_continue] }} · 健康均分 {{ record.avg_score ?? "—" }}</small></div>
        <span class="status-pill" :class="`decision-${record.worth_continue}`">{{ { yes: "继续", adjust: "调整", stop: "停止" }[record.worth_continue] }}</span>
        <div class="record-actions"><button class="text-button" @click="edit(record)">编辑</button><button class="text-button danger-text" @click="remove(record)">删除</button></div>
      </article>
    </div>
  </section>
</template>
