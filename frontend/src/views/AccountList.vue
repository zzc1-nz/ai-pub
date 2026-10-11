<template>
  <div class="account-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>公众号列表</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增公众号
          </el-button>
        </div>
      </template>
      <el-row :gutter="20" v-loading="loading">
        <el-col :span="8" v-for="item in tableData" :key="item.id" style="margin-bottom: 20px">
          <el-card shadow="hover" class="account-card">
            <div class="account-card-header">
              <el-avatar :size="50" style="background: #409eff">
                {{ item.name ? item.name.charAt(0) : '?' }}
              </el-avatar>
              <div class="account-info">
                <div class="account-name">{{ item.name }}</div>
                <div class="account-appid">{{ item.appId }}</div>
              </div>
              <el-tag :type="item.status === 1 ? 'success' : 'danger'" size="small">
                {{ item.status === 1 ? '启用' : '禁用' }}
              </el-tag>
            </div>
            <div class="account-card-body">
              <div class="account-meta">
                <span>AI对话：</span>
                <el-tag :type="item.aiEnabled ? 'success' : 'info'" size="small">
                  {{ item.aiEnabled ? '已开启' : '已关闭' }}
                </el-tag>
              </div>
              <div class="account-meta">
                <span>创建时间：</span>
                <span>{{ formatDate(item.createdAt) }}</span>
              </div>
            </div>
            <div class="account-card-footer">
              <el-button type="primary" link @click="handleEdit(item)">编辑</el-button>
              <el-button type="danger" link @click="handleDelete(item)">删除</el-button>
            </div>
          </el-card>
        </el-col>
      </el-row>
      <el-empty v-if="!loading && !tableData.length" description="暂无公众号" />
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAccountList, deleteAccount } from '@/api'
import { formatDate } from '@/utils/date'

const router = useRouter()

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const loadData = async () => {
  loading.value = true
  try {
    const res = await getAccountList({ pageNum: page.value, pageSize: pageSize.value })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  router.push('/accounts/edit')
}

const handleEdit = (row) => {
  router.push(`/accounts/edit/${row.id}`)
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该公众号吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteAccount(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error(error)
    }
  })
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.account-card {
  height: 100%;
}

.account-card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.account-info {
  flex: 1;
}

.account-name {
  font-size: 16px;
  font-weight: bold;
  color: #333;
}

.account-appid {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}

.account-card-body {
  margin-bottom: 16px;
}

.account-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #666;
}

.account-card-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  border-top: 1px solid #eee;
  padding-top: 12px;
}
</style>
