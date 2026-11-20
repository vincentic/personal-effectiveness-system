<script setup lang="ts">
import { ref } from "vue";
import { api } from "../api";
import type { User } from "../types";

const emit = defineEmits<{
  success: [payload: { token: string; user: User }];
}>();

const username = ref("");
const password = ref("");
const loading = ref(false);
const error = ref("");

async function submit(): Promise<void> {
  if (loading.value) return;
  loading.value = true;
  error.value = "";
  try {
    const result = await api.login(username.value.trim(), password.value);
    emit("success", result);
  } catch (reason) {
    error.value = reason instanceof Error ? reason.message : "登录失败，请稍后重试";
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-layout">
    <section class="login-story">
      <div class="login-brand"><span class="brand-mark">P</span><strong>个人效能系统</strong></div>
      <div class="story-content">
        <span class="eyebrow">PERSONAL EFFECTIVENESS SYSTEM</span>
        <h1>把时间还给<br /><em>真正重要的事。</em></h1>
        <p>用日执行、周复盘、月重构，建立清晰而可持续的个人节奏。</p>
        <div class="story-points">
          <span><i>01</i> 每天聚焦一个关键成果</span>
          <span><i>02</i> 每周让任务流动起来</span>
          <span><i>03</i> 每月调整值得投入的方向</span>
        </div>
      </div>
      <div class="story-foot">清晰一点，轻盈一点，持续一点。</div>
    </section>
    <section class="login-panel">
      <form class="login-card" @submit.prevent="submit">
        <span class="eyebrow">WELCOME BACK</span>
        <h2>登录你的工作台</h2>
        <p class="login-subtitle">继续构建属于自己的效能系统。</p>
        <label class="field-label" for="username">用户名</label>
        <input id="username" v-model="username" autocomplete="username" autofocus required placeholder="输入用户名" />
        <label class="field-label" for="password">密码</label>
        <input id="password" v-model="password" autocomplete="current-password" required type="password" placeholder="输入密码" />
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <button class="button button-primary login-button" :disabled="loading">
          {{ loading ? "正在登录…" : "登录工作台" }} <span>→</span>
        </button>
        <p class="login-hint">使用 FastAPI 配置的管理员账号或已创建的用户登录。</p>
      </form>
      <div class="login-foot">安全连接 <span>·</span> 数据按账号隔离</div>
    </section>
  </main>
</template>
