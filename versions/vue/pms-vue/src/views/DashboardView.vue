<script setup lang="ts">
import { onMounted, ref } from "vue";
import { api } from "../api";
import type { Dashboard } from "../types";

const emit = defineEmits<{ navigate: [page: "daily" | "weekly" | "monthly"] }>();
const data = ref<Dashboard | null>(null);
const error = ref("");
const loading = ref(true);

async function load(): Promise<void> {
  loading.value = true;
  error.value = "";
  try {
    data.value = await api.dashboard();
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "工作台暂时无法加载";
  } finally {
    loading.value = false;
  }
}

function localDate(): string {
  const date = new Date();
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
}

function blockRate(): number {
  return data.value?.block_total ? Math.round((data.value.block_done / data.value.block_total) * 100) : 0;
}

onMounted(load);
</script>

<template>
  <div class="page-heading">
    <div>
      <span class="eyebrow">YOUR PERSONAL RHYTHM</span>
      <h1>今天，先做好一件重要的事。</h1>
      <p>把注意力放回当下，让进展清晰可见。</p>
    </div>
    <button class="button button-primary" @click="emit('navigate', 'daily')">开始今日执行 <span>→</span></button>
  </div>

  <div v-if="error" class="alert alert-error">{{ error }} <button @click="load">重试</button></div>
  <div v-else-if="loading" class="loading-card">正在汇总你的效能数据…</div>
  <template v-else-if="data">
    <section class="stat-grid">
      <article class="stat-card stat-highlight">
        <div class="stat-top"><span>本周时间盒</span><span class="stat-symbol">◷</span></div>
        <strong>{{ data.block_done }}<small> / {{ data.block_total }}</small></strong>
        <div class="stat-progress"><span :style="{ width: `${blockRate()}%` }"></span></div>
        <small class="stat-note">最近 7 条每日清单的完成情况</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>每日执行</span><span class="stat-symbol">▤</span></div>
        <strong>{{ data.daily_count }}</strong>
        <small class="stat-note">持续积累的每日记录</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>每周复盘</span><span class="stat-symbol">↗</span></div>
        <strong>{{ data.weekly_count }}</strong>
        <small class="stat-note">让任务流动起来</small>
      </article>
      <article class="stat-card">
        <div class="stat-top"><span>每月重构</span><span class="stat-symbol">◫</span></div>
        <strong>{{ data.monthly_count }}</strong>
        <small class="stat-note">把精力投向值得的方向</small>
      </article>
    </section>

    <section class="dashboard-grid">
      <article class="panel today-panel">
        <div class="panel-heading">
          <div><span class="eyebrow">TODAY</span><h2>今日重点</h2></div>
          <span class="date-pill">{{ localDate() }}</span>
        </div>
        <template v-if="data.today_plan">
          <div class="mit-box"><span>最重要成果 · MIT</span><strong>{{ data.today_plan.mit || "还没有填写今日成果" }}</strong></div>
          <div class="inline-progress">
            <div><span>组块进度</span><strong>{{ data.today_plan.blocks_done }} / {{ data.today_plan.blocks_total }}</strong></div>
            <div class="progress-track"><span :style="{ width: `${data.today_plan.block_progress}%` }"></span></div>
          </div>
          <button class="button button-secondary" @click="emit('navigate', 'daily')">查看今日清单 <span>→</span></button>
        </template>
        <template v-else>
          <div class="empty-state"><span class="empty-illustration">✳</span><strong>今天还没有计划</strong><p>用几分钟写下 MIT 和时间盒，开始一个清晰的日子。</p></div>
          <button class="button button-primary" @click="emit('navigate', 'daily')">填写今日清单 <span>→</span></button>
        </template>
      </article>

      <article class="panel rhythm-panel">
        <div class="panel-heading"><div><span class="eyebrow">KEEP THE LOOP</span><h2>保持复盘节奏</h2></div></div>
        <button class="rhythm-row" @click="emit('navigate', 'weekly')">
          <span class="rhythm-icon weekly-icon">↗</span>
          <span><strong>每周复盘</strong><small>{{ data.week_review ? `本周已记录 · ${data.week_review.week_start}` : "回顾事实，设计下周" }}</small></span>
          <span class="rhythm-arrow">→</span>
        </button>
        <button class="rhythm-row" @click="emit('navigate', 'monthly')">
          <span class="rhythm-icon monthly-icon">◫</span>
          <span><strong>每月重构</strong><small>{{ data.month_review ? `本月健康分 · ${data.month_review.avg_score ?? "—"}` : "校准目标，调整系统" }}</small></span>
          <span class="rhythm-arrow">→</span>
        </button>
        <div class="quote-box">“你不需要做更多，<br />只需要更清楚地做重要的事。”</div>
      </article>
    </section>

    <section class="panel recent-panel">
      <div class="panel-heading">
        <div><span class="eyebrow">RECENT ACTIVITY</span><h2>最近记录</h2></div>
      </div>
      <div class="recent-grid">
        <div class="recent-column">
          <div class="recent-title"><span class="recent-dot daily-dot"></span>每日执行</div>
          <button v-for="record in data.recent_daily.slice(0, 3)" :key="record.id" class="recent-item" @click="emit('navigate', 'daily')">
            <span>{{ record.plan_date }}</span><strong>{{ record.mit || "每日执行清单" }}</strong><small>{{ record.blocks_done }}/{{ record.blocks_total }} 组块</small>
          </button>
          <p v-if="!data.recent_daily.length" class="muted-copy">还没有每日记录</p>
        </div>
        <div class="recent-column">
          <div class="recent-title"><span class="recent-dot weekly-dot"></span>每周复盘</div>
          <button v-for="record in data.recent_weekly.slice(0, 3)" :key="record.id" class="recent-item" @click="emit('navigate', 'weekly')">
            <span>{{ record.week_start }} — {{ record.week_end }}</span><strong>{{ record.next_mit_1 || "每周复盘" }}</strong><small>下周成果</small>
          </button>
          <p v-if="!data.recent_weekly.length" class="muted-copy">还没有每周复盘</p>
        </div>
        <div class="recent-column">
          <div class="recent-title"><span class="recent-dot monthly-dot"></span>每月重构</div>
          <button v-for="record in data.recent_monthly.slice(0, 3)" :key="record.id" class="recent-item" @click="emit('navigate', 'monthly')">
            <span>{{ record.month }}</span><strong>{{ record.top_achievement || "每月重构" }}</strong><small>健康均分 {{ record.avg_score ?? "—" }}</small>
          </button>
          <p v-if="!data.recent_monthly.length" class="muted-copy">还没有每月重构</p>
        </div>
      </div>
    </section>
  </template>
</template>
