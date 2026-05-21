<template>
  <el-container class="layout-container">
    <el-aside width="220px">
      <div class="logo">
        <h2>公众号管理</h2>
      </div>
      <el-menu :default-active="activeMenu" router>
        <el-menu-item index="/dashboard">
          <el-icon><DataLine /></el-icon>
          <span>数据统计</span>
        </el-menu-item>
        <el-menu-item index="/accounts">
          <el-icon><OfficeBuilding /></el-icon>
          <span>公众号管理</span>
        </el-menu-item>
        <el-menu-item index="/menus">
          <el-icon><Menu /></el-icon>
          <span>菜单管理</span>
        </el-menu-item>
        <el-menu-item index="/materials">
          <el-icon><Picture /></el-icon>
          <span>素材管理</span>
        </el-menu-item>
        <el-menu-item index="/reply-rules">
          <el-icon><ChatLineRound /></el-icon>
          <span>自动回复</span>
        </el-menu-item>
        <el-menu-item index="/messages">
          <el-icon><Message /></el-icon>
          <span>消息记录</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header>
        <div class="header-content">
          <span class="title">微信公众号管理系统</span>
          <div class="header-right">
            <el-select
              v-model="selectedAccount"
              placeholder="请选择公众号"
              style="width: 200px; margin-right: 20px"
              @change="handleAccountChange"
              size="small"
            >
              <el-option
                v-for="item in accounts"
                :key="item.id"
                :label="item.name"
                :value="item.id"
              />
            </el-select>
            <div class="user-info">
              <el-dropdown @command="handleCommand">
                <span class="el-dropdown-link">
                  <el-icon><User /></el-icon>
                  {{ userStore.userInfo.username || '用户' }}
                  <el-icon><ArrowDown /></el-icon>
                </span>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="logout">退出登录</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
        </div>
      </el-header>
      <el-main>
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useUserStore } from '@/store/user'
import { getAccountList } from '@/api'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeMenu = computed(() => route.path)
const accounts = ref([])
const selectedAccount = ref(userStore.selectedAccountId)

const loadAccounts = async () => {
  try {
    const res = await getAccountList({ pageNum: 1, pageSize: 100 })
    accounts.value = res.data.records || []
    if (!accounts.value.find(a => a.id === selectedAccount.value)) {
      const first = accounts.value.find(a => a.status === 1) || accounts.value[0]
      if (first) {
        selectedAccount.value = first.id
        userStore.setSelectedAccountId(first.id)
      }
    }
  } catch (e) {
    console.error(e)
  }
}

const handleAccountChange = (val) => {
  userStore.setSelectedAccountId(val)
}

const handleCommand = (command) => {
  if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      userStore.logout()
      router.push('/login')
    })
  }
}

onMounted(() => {
  loadAccounts()
})
</script>

<style scoped>
.layout-container {
  width: 100%;
  height: 100%;
}

.el-aside {
  background-color: #304156;
  color: #fff;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #2b3a4a;
}

.logo h2 {
  margin: 0;
  font-size: 18px;
  color: #fff;
}

.el-menu {
  border-right: none;
  background-color: #304156;
}

.el-header {
  background-color: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.header-content {
  width: 100%;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-right {
  display: flex;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: 500;
}

.user-info {
  cursor: pointer;
}

.el-dropdown-link {
  display: flex;
  align-items: center;
  gap: 5px;
}

.el-main {
  background-color: #f0f2f5;
  padding: 20px;
}
</style>
