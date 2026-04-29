<template>
  <div class="flex min-h-screen items-center justify-center px-6 py-10">
    <div class="w-full max-w-3xl rounded-[32px] border border-amber-400/15 bg-slate-950/80 p-12 text-center shadow-[0_30px_80px_rgba(15,23,42,0.4)] backdrop-blur">
      <div class="text-sm tracking-[0.36em] text-amber-300/70">ACCESS DENIED</div>
      <div class="mt-6 text-7xl font-semibold text-white">403</div>
      <h1 class="mt-6 text-3xl font-semibold text-slate-100">无权限访问当前页面</h1>
      <p class="mx-auto mt-4 max-w-xl text-sm leading-7 text-slate-400">
        当前账号角色不具备此功能访问权限。请返回可访问模块，或联系系统管理员分配对应角色权限。
      </p>

      <div class="mt-10 flex flex-wrap justify-center gap-4">
        <el-button type="primary" @click="router.push('/upload')">返回上传页</el-button>
        <el-button @click="router.back()">返回上一页</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router';

const router = useRouter();
</script>
