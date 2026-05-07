<template>
  <div class="page-container profile-page">
    <!-- Page Title with teal left accent bar -->
    <div class="page-title-bar">
      <h2 class="page-title">个人中心</h2>
    </div>

    <!-- Profile Header Card — Ctrip-style avatar + gradient -->
    <el-card class="profile-header-card" :body-style="{ padding: 0 }" shadow="never">
      <div class="profile-header">
        <div class="avatar-circle">
          <span class="avatar-letter">{{ firstLetter }}</span>
        </div>
        <div class="profile-info-group">
          <div class="profile-nickname">{{ auth.nickname || auth.username }}</div>
          <div class="profile-username">@{{ auth.username }}</div>
        </div>
      </div>
      <div class="stats-row">
        <div class="stat-item">
          <span class="stat-number">--</span>
          <span class="stat-label">景点收藏</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-number">--</span>
          <span class="stat-label">旅行日记</span>
        </div>
        <div class="stat-divider"></div>
        <div class="stat-item">
          <span class="stat-number">--</span>
          <span class="stat-label">路线规划</span>
        </div>
      </div>
    </el-card>

    <!-- User Info Card -->
    <el-card class="info-card" :body-style="{ padding: '20px' }">
      <template #header>
        <span class="card-header-title">
          <el-icon><User /></el-icon> 基本信息
        </span>
      </template>
      <el-skeleton :loading="infoLoading" animated>
        <template #template>
          <div class="skeleton-content">
            <el-skeleton-item variant="text" style="width: 80%; margin-bottom: 18px" />
            <el-skeleton-item variant="text" style="width: 60%; margin-bottom: 18px" />
            <el-skeleton-item variant="text" style="width: 70%; margin-bottom: 18px" />
            <el-skeleton-item variant="text" style="width: 50%; margin-bottom: 18px" />
            <el-skeleton-item variant="button" style="width: 100px; height: 36px" />
          </div>
        </template>
        <el-form :model="infoForm" label-width="85px">
          <el-form-item>
            <template #label>
              <span><el-icon style="vertical-align: middle"><User /></el-icon> 用户名</span>
            </template>
            <el-input :model-value="auth.username" disabled />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span><el-icon style="vertical-align: middle"><Postcard /></el-icon> 昵称</span>
            </template>
            <el-input v-model="infoForm.nickname" />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span><el-icon style="vertical-align: middle"><Iphone /></el-icon> 手机号</span>
            </template>
            <el-input v-model="infoForm.phone" />
          </el-form-item>
          <el-form-item>
            <template #label>
              <span><el-icon style="vertical-align: middle"><Message /></el-icon> 邮箱</span>
            </template>
            <el-input v-model="infoForm.email" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" round :loading="saveLoading" @click="saveInfo">保存</el-button>
          </el-form-item>
        </el-form>
      </el-skeleton>
    </el-card>

    <!-- Password Card -->
    <el-card class="info-card" :body-style="{ padding: '20px' }">
      <template #header>
        <span class="card-header-title">
          <el-icon><Lock /></el-icon> 修改密码
        </span>
      </template>
      <el-form :model="pwdForm" label-width="100px">
        <el-form-item label="旧密码">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="pwdForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item>
          <el-button type="warning" round :loading="pwdLoading" @click="changePwd">修改密码</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- Preferences Card -->
    <el-card class="info-card" :body-style="{ padding: '20px' }">
      <template #header>
        <span class="card-header-title">
          <el-icon><Setting /></el-icon> 偏好设置
        </span>
      </template>
      <el-skeleton :loading="prefLoading" animated>
        <template #template>
          <div class="skeleton-content">
            <el-skeleton-item variant="text" style="width: 50%; margin-bottom: 18px" />
            <el-skeleton-item variant="text" style="width: 70%; margin-bottom: 18px" />
            <el-skeleton-item variant="text" style="width: 40%; margin-bottom: 18px" />
            <el-skeleton-item variant="button" style="width: 120px; height: 36px" />
          </div>
        </template>
        <el-form :model="prefForm" label-width="100px">
          <el-form-item label="出行方式">
            <el-radio-group v-model="prefForm.transportType">
              <el-radio-button value="walk">步行</el-radio-button>
              <el-radio-button value="bike">骑行</el-radio-button>
              <el-radio-button value="shuttle">接驳车</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="步行上限(m)">
            <el-input-number v-model="prefForm.maxWalkDistance" :min="500" :max="50000" :step="500" />
          </el-form-item>
          <el-form-item label="日预算(元)">
            <el-input-number v-model="prefForm.budgetPerDay" :min="0" :max="100000" :step="50" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" round :loading="prefSaveLoading" @click="savePrefs">保存偏好</el-button>
          </el-form-item>
        </el-form>
      </el-skeleton>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  User,
  Postcard,
  Iphone,
  Message,
  Lock,
  Setting,
} from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { userApi } from '@/api/modules/user'

const auth = useAuthStore()

const firstLetter = computed(() => {
  return (auth.nickname || auth.username || 'U').charAt(0).toUpperCase()
})

const infoLoading = ref(false)
const saveLoading = ref(false)
const pwdLoading = ref(false)
const prefLoading = ref(false)
const prefSaveLoading = ref(false)

const infoForm = reactive({ nickname: '', phone: '', email: '' })
const pwdForm = reactive({ oldPassword: '', newPassword: '' })
const prefForm = reactive({ transportType: 'walk', maxWalkDistance: 5000, budgetPerDay: 200 })

onMounted(async () => {
  if (!auth.userId) return
  infoLoading.value = true
  prefLoading.value = true
  try {
    const res = await userApi.getInfo(auth.userId)
    if (res.data.code === 200) {
      const u = res.data.data
      infoForm.nickname = u.nickname || ''
      infoForm.phone = u.phone || ''
      infoForm.email = u.email || ''
      if (u.preferences) {
        prefForm.transportType = u.preferences.transportType || 'walk'
        prefForm.maxWalkDistance = u.preferences.maxWalkDistance || 5000
        prefForm.budgetPerDay = u.preferences.budgetPerDay || 200
      }
    }
  } catch { ElMessage.error('加载个人信息失败') }
  finally { infoLoading.value = false; prefLoading.value = false }
})

async function saveInfo() {
  if (!auth.userId) return
  saveLoading.value = true
  try {
    const res = await userApi.updateInfo(auth.userId, {
      nickname: infoForm.nickname, phone: infoForm.phone, email: infoForm.email
    })
    if (res.data.code === 200) {
      ElMessage.success('保存成功')
      if (res.data.data?.nickname) { auth.nickname = res.data.data.nickname }
    }
  } catch { ElMessage.error('保存失败') }
  finally { saveLoading.value = false }
}

async function changePwd() {
  if (!auth.userId) return
  if (!pwdForm.oldPassword || !pwdForm.newPassword) { ElMessage.warning('请填写新旧密码'); return }
  pwdLoading.value = true
  try {
    await userApi.changePassword(auth.userId, {
      oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword
    })
    ElMessage.success('密码已修改')
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
  } catch { ElMessage.error('密码修改失败') }
  finally { pwdLoading.value = false }
}

async function savePrefs() {
  if (!auth.userId) return
  prefSaveLoading.value = true
  try {
    await userApi.updatePreferences(auth.userId, { ...prefForm })
    ElMessage.success('偏好已保存')
  } catch { ElMessage.error('保存失败') }
  finally { prefSaveLoading.value = false }
}
</script>

<style scoped>
.profile-page {
  max-width: 600px;
  margin: 0 auto;
}

/* ── Page Title — sketch accent bar ── */
.page-title-bar {
  margin-bottom: 20px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: #2c2c2c;
  padding-left: 12px;
  border-left: 3px solid #2c2c2c;
  margin: 0;
  line-height: 1.3;
  font-family: Georgia, 'Times New Roman', serif;
}

/* ── Profile Header Card (Sketch-style) ── */
.profile-header-card {
  margin-bottom: 24px;
  border-radius: 4px;
  overflow: hidden;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  background: #faf5ed;
  position: relative;
}

.profile-header-card::after {
  display: none;
}

.profile-header {
  display: flex;
  align-items: center;
  padding: 32px 24px 20px;
  gap: 20px;
}

/* ── Avatar Circle (80px) ── */
.avatar-circle {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: #2c2c2c;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
}

.avatar-letter {
  font-size: 36px;
  font-weight: 700;
  color: #f5f0e8;
  user-select: none;
}

/* ── Profile Info ── */
.profile-info-group {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.profile-nickname {
  font-size: 18px;
  font-weight: 600;
  color: #2c2c2c;
}

.profile-username {
  font-size: 13px;
  color: #2c2c2c;
  opacity: 0.7;
}

/* ── Stats Row ── */
.stats-row {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 16px 20px 20px;
  border-top: 2px dashed #2c2c2c;
}

.stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  flex: 1;
}

.stat-number {
  font-size: 22px;
  font-weight: 700;
  color: #2c2c2c;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #2c2c2c;
  opacity: 0.7;
  white-space: nowrap;
}

.stat-divider {
  width: 1px;
  height: 32px;
  background: #2c2c2c;
  opacity: 0.3;
  flex-shrink: 0;
}

/* ── Info Cards ── */
.info-card {
  margin-bottom: 24px;
  border-radius: 4px;
  border: 2px dashed #2c2c2c;
  box-shadow: 2px 2px 0px rgba(44,44,44,0.3);
  background: #faf5ed;
}

/* ── Skeleton ── */
.skeleton-content {
  padding: 4px 0;
}

/* ── Card Header Title ── */
.card-header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
  font-size: 16px;
  border-left: 3px solid #2c2c2c;
  padding-left: 12px;
  color: #2c2c2c;
}

/* ── Buttons ── */
.info-card .el-button.round {
  border-radius: 4px;
}
</style>
