<template>
  <div class="flex min-h-screen items-center justify-center px-6 py-10">
    <div class="grid w-full max-w-6xl overflow-hidden rounded-[32px] border border-cyan-400/10 bg-slate-950/70 shadow-[0_25px_70px_rgba(8,47,73,0.42)] backdrop-blur xl:grid-cols-[1.2fr_0.8fr]">
      <section class="relative hidden overflow-hidden bg-[radial-gradient(circle_at_top_left,rgba(6,182,212,0.26),transparent_38%),linear-gradient(180deg,#06101d,#0b1d34)] p-12 xl:block">
        <div class="max-w-lg">
          <div class="text-sm tracking-[0.4em] text-cyan-300/80">ASSEMBLY LINE QA</div>
          <h1 class="mt-6 text-4xl font-semibold leading-tight text-slate-50">
            电子元器件装配生产线
            <span class="text-cyan-300">质量评估系统</span>
          </h1>
          <p class="mt-6 text-base leading-7 text-slate-300">
            面向军工制造场景的实时质量分析前端，聚焦多模态数据接入、缺陷识别、工艺研判与预测优化。
          </p>
        </div>
      </section>

      <section class="p-10 sm:p-14">
        <div class="mx-auto max-w-md">
          <div class="text-sm tracking-[0.32em] text-cyan-300/80">SECURE ACCESS</div>
          <h2 class="mt-4 text-3xl font-semibold text-white">系统登录</h2>
          <p class="mt-3 text-sm leading-6 text-slate-400">
            请输入账号密码并完成验证码校验以登录系统。
          </p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            class="mt-10"
            label-position="top"
            size="large"
          >
            <el-form-item label="账号" prop="username">
              <el-input v-model="form.username" placeholder="请输入账号" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input
                v-model="form.password"
                type="password"
                show-password
                placeholder="请输入密码"
              />
            </el-form-item>
            <el-form-item label="验证码" prop="captcha">
              <div class="grid grid-cols-[1fr_132px] gap-3">
                <el-input v-model="form.captcha" placeholder="请输入验证码" />
                <img
                  v-if="captchaImage"
                  :src="captchaImage"
                  alt="captcha"
                  class="h-12 w-[132px] cursor-pointer rounded-2xl border border-cyan-400/20 transition hover:border-cyan-400/40"
                  @click="refreshCaptcha"
                />
              </div>
            </el-form-item>

            <el-button
              type="primary"
              class="mt-4 !h-12 !w-full !rounded-2xl"
              :loading="loading"
              @click="handleLogin"
            >
              登录系统
            </el-button>
          </el-form>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, type FormInstance, type FormRules } from 'element-plus';
import { useRouter } from 'vue-router';

import { getLoginCaptcha, loginByPassword } from '@/api/user';
import { useUserStore } from '@/stores/user';

const router = useRouter();
const userStore = useUserStore();
const formRef = ref<FormInstance>();

const form = reactive({
  username: 'admin',
  password: '123456',
  captcha: '',
});

const captchaImage = ref('');
const captchaId = ref('');
const loading = ref(false);

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  captcha: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
};

const refreshCaptcha = async () => {
  const response = await getLoginCaptcha();
  captchaId.value = response.captchaId;
  captchaImage.value = response.captchaImage;
};

const handleLogin = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;

  try {
    const response = await loginByPassword({
      username: form.username,
      password: form.password,
      captchaId: captchaId.value,
      captchaCode: form.captcha,
    });

    userStore.setAuth({
      token: response.token,
      profile: response.user,
    });
    ElMessage.success('登录成功');
    router.push('/upload');
  } catch (error) {
    const message = error instanceof Error ? error.message : String((error as { message?: string })?.message ?? '登录失败');
    ElMessage.error(message);
    await refreshCaptcha();
  } finally {
    loading.value = false;
  }
};

onMounted(() => {
  refreshCaptcha();
});
</script>

<style scoped>
:deep(.el-form-item__label) {
  color: #cbd5e1;
}
</style>
