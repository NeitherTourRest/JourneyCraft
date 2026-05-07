<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '@/api/modules/auth'
import { User, Lock, Postcard, Iphone, Message } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  nickname: '',
  password: '',
  confirmPassword: '',
  phone: '',
  email: '',
})

const validateConfirmPassword = (_rule: any, value: string, callback: any) => {
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, message: '用户名至少2个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, message: '密码至少6个字符', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    { validator: validateConfirmPassword, trigger: 'blur' },
  ],
  email: [
    { type: 'email', message: '请输入正确的邮箱格式', trigger: 'blur' },
  ],
}

async function handleRegister() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await authApi.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || undefined,
      phone: form.phone || undefined,
      email: form.email || undefined,
    })
    ElMessage.success('注册成功')
    router.push('/login')
  } catch (err: any) {
    const msg = err?.response?.data?.message || err?.message || '注册失败'
    ElMessage.error('注册失败: ' + msg)
  } finally {
    loading.value = false
  }
}

function goToLogin() {
  router.push('/login')
}
</script>

<template>
  <div class="auth-page">
    <el-card class="auth-card" shadow="always">
      <div class="auth-header">
        <h1 class="auth-title">JourneyCraft</h1>
        <p class="auth-subtitle">注册</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        class="auth-form"
        @keyup.enter="handleRegister"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="nickname">
          <el-input
            v-model="form.nickname"
            placeholder="昵称（选填）"
            :prefix-icon="Postcard"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            placeholder="确认密码"
            :prefix-icon="Lock"
            show-password
            size="large"
          />
        </el-form-item>

        <el-form-item prop="phone">
          <el-input
            v-model="form.phone"
            placeholder="手机号（选填）"
            :prefix-icon="Iphone"
            size="large"
          />
        </el-form-item>

        <el-form-item prop="email">
          <el-input
            v-model="form.email"
            placeholder="邮箱（选填）"
            :prefix-icon="Message"
            size="large"
          />
        </el-form-item>

        <el-form-item>
          <el-button
            type="primary"
            size="large"
            :loading="loading"
            class="submit-btn"
            @click="handleRegister"
          >
            注册
          </el-button>
        </el-form-item>
      </el-form>

      <div class="auth-footer">
        已有账号？
        <el-link type="primary" @click="goToLogin">去登录</el-link>
      </div>
    </el-card>
  </div>
</template>

<style scoped>
/* ── Page Container ── */
.auth-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 16px;
  background: #f5f0e8;
}

/* ── Auth Card ── */
.auth-card {
  width: 100%;
  max-width: 480px;
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  border-top: 4px solid #2c2c2c;
  background: #faf5ed;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  overflow: hidden;
  animation: authCardFadeIn 0.15s ease;
}

@keyframes authCardFadeIn {
  from {
    opacity: 0;
    transform: translateY(24px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ── Header ── */
.auth-header {
  text-align: center;
  margin-bottom: 32px;
}

.auth-title {
  font-size: 28px;
  font-weight: 700;
  color: #2c2c2c;
  margin: 0 0 8px;
  letter-spacing: 1px;
  font-family: Georgia, 'Times New Roman', serif;
}

.auth-subtitle {
  font-size: 14px;
  color: #2c2c2c;
  opacity: 0.7;
  margin: 0;
}

/* ── Form ── */
.auth-form {
  margin-bottom: 16px;
}

.submit-btn {
  width: 100%;
}

/* ── Footer ── */
.auth-footer {
  text-align: center;
  font-size: 14px;
  color: #2c2c2c;
  opacity: 0.7;
}

.auth-footer .el-link {
  font-size: 14px;
  font-weight: 500;
}

.auth-footer .el-link:hover {
  text-decoration: underline;
}

/* ── Responsive ── */
@media (max-width: 768px) {
  .auth-card {
    max-width: 100%;
  }

  .auth-title {
    font-size: 24px;
  }
}
</style>
