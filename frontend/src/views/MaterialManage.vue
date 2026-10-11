<template>
  <div class="material-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>素材管理</span>
          <el-button type="primary" @click="handleUpload">
            <el-icon><Upload /></el-icon>
            上传素材
          </el-button>
        </div>
      </template>
      <el-tabs v-model="activeTab" @tab-change="handleTabChange">
        <el-tab-pane label="全部" name="all" />
        <el-tab-pane label="图片" name="image" />
        <el-tab-pane label="视频" name="video" />
        <el-tab-pane label="语音" name="voice" />
        <el-tab-pane label="图文" name="news" />
      </el-tabs>
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="title" label="素材名称" />
        <el-table-column prop="type" label="素材类型">
          <template #default="{ row }">
            <el-tag>{{ typeLabels[row.type] || row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览" width="100">
          <template #default="{ row }">
            <el-image
              v-if="row.type === 'image'"
              :src="row.url"
              :preview-src-list="[row.url]"
              style="width: 60px; height: 60px"
              fit="cover"
            />
            <el-tag v-else-if="row.type === 'voice'" type="info" size="small">语音</el-tag>
            <el-tag v-else-if="row.type === 'video'" type="success" size="small">视频</el-tag>
            <el-tag v-else-if="row.type === 'news'" type="warning" size="small">图文</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="上传时间">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next"
        @current-change="loadData"
        style="margin-top: 20px; justify-content: flex-end"
      />
    </el-card>
    <el-dialog v-model="dialogVisible" title="上传素材" width="500px">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :on-change="handleFileChange"
        :limit="1"
        :file-list="fileList"
      >
        <el-button type="primary">选择文件</el-button>
      </el-upload>
      <el-form :model="form" ref="formRef" label-width="100px" style="margin-top: 20px">
        <el-form-item label="素材类型" prop="type">
          <el-select v-model="form.type" placeholder="请选择素材类型">
            <el-option label="图片" value="image" />
            <el-option label="视频" value="video" />
            <el-option label="语音" value="voice" />
            <el-option label="图文" value="news" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { getMaterialList, uploadMaterial, deleteMaterial } from '@/api'
import { formatDate } from '@/utils/date'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const typeLabels = { image: '图片', video: '视频', voice: '语音', news: '图文' }
const activeTab = ref('all')
const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const uploadRef = ref(null)
const formRef = ref(null)
const fileList = ref([])
const form = ref({
  type: 'image',
  file: null
})

const loadData = async () => {
  loading.value = true
  try {
    const params = { pageNum: page.value, pageSize: pageSize.value, accountId: userStore.selectedAccountId }
    if (activeTab.value !== 'all') {
      params.type = activeTab.value
    }
    const res = await getMaterialList(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleTabChange = () => {
  page.value = 1
  loadData()
}

// 顶部切换公众号后重新拉取素材（原先只在 onMounted 读一次）
watch(() => userStore.selectedAccountId, () => {
  page.value = 1
  loadData()
})

const handleUpload = () => {
  form.value = { type: 'image', file: null }
  fileList.value = []
  dialogVisible.value = true
}

const handleFileChange = (file) => {
  form.value.file = file.raw
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该素材吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteMaterial(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error(error)
    }
  })
}

const handleSubmit = async () => {
  if (!form.value.file) {
    ElMessage.warning('请选择文件')
    return
  }
  const formData = new FormData()
  formData.append('file', form.value.file)
  formData.append('type', form.value.type)
  formData.append('accountId', userStore.selectedAccountId)
  loading.value = true
  try {
    await uploadMaterial(formData)
    ElMessage.success('上传成功')
    dialogVisible.value = false
    loadData()
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
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
</style>
