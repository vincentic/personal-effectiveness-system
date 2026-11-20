<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import type { DailyInput, DailyRecord } from "../types";

type CheckKey =
  | "pre_know_output" | "pre_materials_ready" | "pre_phone_handled"
  | "during_one_block" | "during_shrink_step" | "during_record_distraction"
  | "post_visible_output" | "post_recall" | "post_next_step"
  | "life_water_food" | "life_walk_exercise" | "life_clean_tidy" | "life_social_outdoor";

const checkGroups: { title: string; items: { key: CheckKey; label: string }[] }[] = [
  { title: "开始前", items: [
    { key: "pre_know_output", label: "明确本轮交付成果" },
    { key: "pre_materials_ready", label: "材料已准备" },
    { key: "pre_phone_handled", label: "手机与通知已处理" },
  ] },
  { title: "执行中", items: [
    { key: "during_one_block", label: "一次只做一个组块" },
    { key: "during_shrink_step", label: "卡住时缩小下一步" },
    { key: "during_record_distraction", label: "记录干扰" },
  ] },
  { title: "结束后", items: [
    { key: "post_visible_output", label: "留下可见产出" },
    { key: "post_recall", label: "主动回忆或复述" },
    { key: "post_next_step", label: "标记下一步" },
  ] },
  { title: "生活维护", items: [
    { key: "life_water_food", label: "饮水与正常饮食" },
    { key: "life_walk_exercise", label: "走路或运动" },
    { key: "life_clean_tidy", label: "清洁或整理" },
    { key: "life_social_outdoor", label: "真实交流或户外" },
  ] },
];

function today(): string {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function blankBlock(seq: number) {
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

const records = ref<DailyRecord[]>([]);
const form = ref<DailyInput>(blankForm());
const editingId = ref<number | null>(null);
const loading = ref(true);
const saving = ref(false);
const error = ref("");
const notice = ref("");

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    records.value = await api.daily();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "每日清单加载失败";
  } finally {
    loading.value = false;
  }
}

function edit(record: DailyRecord): void {
  editingId.value = record.id;
  form.value = {
    plan_date: record.plan_date,
    sleep_hours: record.sleep_hours,
    energy_level: record.energy_level,
    mit: record.mit,
    pre_know_output: record.pre_know_output,
    pre_materials_ready: record.pre_materials_ready,
    pre_phone_handled: record.pre_phone_handled,
    during_one_block: record.during_one_block,
    during_shrink_step: record.during_shrink_step,
    during_record_distraction: record.during_record_distraction,
    post_visible_output: record.post_visible_output,
    post_recall: record.post_recall,
    post_next_step: record.post_next_step,
    life_water_food: record.life_water_food,
    life_walk_exercise: record.life_walk_exercise,
    life_clean_tidy: record.life_clean_tidy,
    life_social_outdoor: record.life_social_outdoor,
    review_accomplished: record.review_accomplished,
    review_resistance: record.review_resistance,
    review_tomorrow_start: record.review_tomorrow_start,
    review_gratitude: record.review_gratitude,
    blocks: record.blocks.map((block, index) => ({ ...block, seq: index + 1 })),
  };
  notice.value = "";
  error.value = "";
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function reset(): void {
  editingId.value = null;
  form.value = blankForm();
  error.value = "";
  notice.value = "";
}

function setSleepHours(event: Event): void {
  const value = (event.target as HTMLInputElement).value;
  form.value.sleep_hours = value ? Number(value) : null;
}

function addBlock(): void {
  form.value.blocks.push(blankBlock(form.value.blocks.length + 1));
}

function removeBlock(index: number): void {
  form.value.blocks.splice(index, 1);
  form.value.blocks.forEach((block, i) => { block.seq = i + 1; });
}

async function save(): Promise<void> {
  if (saving.value) return;
  saving.value = true;
  error.value = "";
  notice.value = "";
  try {
    const isEditing = editingId.value !== null;
    const payload: DailyInput = {
      ...form.value,
      sleep_hours: form.value.sleep_hours === null ? null : Number(form.value.sleep_hours),
      blocks: form.value.blocks.map((block, index) => ({ ...block, seq: index + 1 })),
    };
    await api.saveDaily(payload, editingId.value ?? undefined);
    reset();
    await load();
    notice.value = isEditing ? "每日清单已更新" : "每日清单已保存";
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "保存失败，请检查输入后重试";
  } finally {
    saving.value = false;
  }
}

async function remove(record: DailyRecord): Promise<void> {
  if (!window.confirm(`确定删除 ${record.plan_date} 的每日清单吗？`)) return;
  error.value = "";
  try {
    await api.deleteDaily(record.id);
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
    <div><span class="eyebrow">DAILY EXECUTION</span><h1>每日执行</h1><p>先确定成果，再给注意力安排一个清晰的去处。</p></div>
    <button class="button button-primary" @click="reset">＋ 新建今日清单</button>
  </div>

  <div v-if="error" class="alert alert-error" role="alert">{{ error }}</div>
  <div v-if="notice" class="alert alert-success">{{ notice }}</div>

  <form class="editor panel" @submit.prevent="save">
    <div class="panel-heading editor-heading">
      <div><span class="eyebrow">{{ editingId ? "UPDATE YOUR PLAN" : "MAKE TODAY COUNT" }}</span><h2>{{ editingId ? "编辑每日清单" : "今日方向" }}</h2></div>
      <span class="editor-step">01 <i>/</i> 04</span>
    </div>
    <div class="form-grid form-grid-three">
      <label class="field"><span>日期</span><input v-model="form.plan_date" type="date" required /></label>
      <label class="field"><span>睡眠小时</span><input :value="form.sleep_hours ?? ''" type="number" min="0" max="24" step="0.5" placeholder="例如 7.5" @input="setSleepHours" /></label>
      <label class="field"><span>当前精力</span><select v-model="form.energy_level"><option value="low">偏低</option><option value="medium">一般</option><option value="high">充沛</option></select></label>
    </div>
    <label class="field"><span>今天最重要的成果 <em>MIT</em></span><textarea v-model="form.mit" rows="2" placeholder="今天结束前，哪一个可见成果最值得完成？"></textarea></label>

    <div class="section-divider">
      <div class="section-title"><span class="section-number">02</span><div><h3>时间盒组块</h3><p>每个组块都写清关键动作与可验收输出。</p></div></div>
      <button class="button button-light button-small" type="button" @click="addBlock">＋ 添加组块</button>
    </div>
    <div v-if="!form.blocks.length" class="inline-empty">还没有组块，点击「添加组块」开始安排。</div>
    <div v-for="(block, index) in form.blocks" :key="index" class="block-editor">
      <div class="block-editor-title"><span>组块 {{ String(index + 1).padStart(2, "0") }}</span><button class="text-button danger-text" type="button" @click="removeBlock(index)">移除</button></div>
      <div class="form-grid form-grid-two">
        <label class="field"><span>组块 / 任务</span><input v-model="block.block_name" required placeholder="例如：完成方案初稿" /></label>
        <div class="time-fields">
          <label class="field"><span>开始</span><input v-model="block.time_start" type="time" /></label>
          <label class="field"><span>结束</span><input v-model="block.time_end" type="time" /></label>
        </div>
      </div>
      <div class="form-grid form-grid-three">
        <label class="field"><span>输入</span><input v-model="block.input_desc" placeholder="需要的信息或材料" /></label>
        <label class="field"><span>关键动作</span><input v-model="block.key_action" placeholder="这段时间具体做什么" /></label>
        <label class="field"><span>可验收输出</span><input v-model="block.deliverable" placeholder="结束时留下什么" /></label>
      </div>
      <label class="check-line"><input v-model="block.is_done" type="checkbox" /><span>已完成</span></label>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">03</span><div><h3>执行与生活检查</h3><p>用小检查保护专注，也照顾生活基本盘。</p></div></div></div>
    <div class="check-groups">
      <fieldset v-for="group in checkGroups" :key="group.title" class="check-group">
        <legend>{{ group.title }}</legend>
        <label v-for="item in group.items" :key="item.key" class="check-line"><input v-model="form[item.key]" type="checkbox" /><span>{{ item.label }}</span></label>
      </fieldset>
    </div>

    <div class="section-divider"><div class="section-title"><span class="section-number">04</span><div><h3>晚间 5 分钟复盘</h3><p>记录事实、阻力和明天可以采取的最小动作。</p></div></div></div>
    <div class="form-grid form-grid-two">
      <label class="field"><span>今天真正完成了什么</span><textarea v-model="form.review_accomplished" rows="2"></textarea></label>
      <label class="field"><span>今天最大的阻力</span><textarea v-model="form.review_resistance" rows="2"></textarea></label>
      <label class="field"><span>明天最小的启动动作</span><textarea v-model="form.review_tomorrow_start" rows="2"></textarea></label>
      <label class="field"><span>今天值得感谢的一件事</span><textarea v-model="form.review_gratitude" rows="2"></textarea></label>
    </div>
    <div class="form-actions"><button class="button button-primary" :disabled="saving">{{ saving ? "保存中…" : editingId ? "保存修改" : "保存每日清单" }}</button><button v-if="editingId" class="button button-secondary" type="button" @click="reset">取消编辑</button></div>
  </form>

  <section class="panel records-panel">
    <div class="panel-heading"><div><span class="eyebrow">YOUR DAILY LOG</span><h2>历史清单</h2></div><span class="count-pill">{{ records.length }} 条记录</span></div>
    <div v-if="loading" class="loading-card">正在加载每日清单…</div>
    <div v-else-if="!records.length" class="empty-state compact-empty"><span class="empty-illustration">◷</span><strong>还没有每日记录</strong><p>从上方开始，建立你的第一个清晰日程。</p></div>
    <div v-else class="record-list">
      <article v-for="record in records" :key="record.id" class="record-row">
        <div class="record-date"><strong>{{ record.plan_date.slice(5) }}</strong><small>{{ record.plan_date.slice(0, 4) }}</small></div>
        <div class="record-main"><strong>{{ record.mit || "每日执行清单" }}</strong><small>{{ record.blocks_done }}/{{ record.blocks_total }} 个组块完成 · 检查 {{ record.checklist_done }}/{{ record.checklist_total }}</small></div>
        <span class="energy-pill" :class="`energy-${record.energy_level}`">{{ { low: "偏低", medium: "一般", high: "充沛" }[record.energy_level] }}</span>
        <div class="record-actions"><button class="text-button" @click="edit(record)">编辑</button><button class="text-button danger-text" @click="remove(record)">删除</button></div>
      </article>
    </div>
  </section>
</template>
