<template>
  <div class="reply-rule">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>自动回复规则</span>
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>
            新增规则
          </el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="keyword" label="关键词" />
        <el-table-column prop="matchType" label="匹配模式" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ { CONTAINS: '包含', EXACT: '精确', ALL_CONTAINS: '全含', STARTS_WITH: '前缀', ENDS_WITH: '后缀', FUZZY: '模糊', REGEX: '正则' }[row.matchType] || row.matchType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replyType" label="回复类型">
          <template #default="{ row }">
            <el-tag>{{ row.replyType === 'TEXT' ? '文本' : row.replyType === 'IMAGE' ? '图片' : '图文' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="replyContent" label="回复内容" show-overflow-tooltip />
        <el-table-column prop="status" label="状态">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'">
              {{ row.status === 1 ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
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
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑规则' : '新增规则'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入规则名称" />
        </el-form-item>
        <el-form-item label="关键词" prop="keyword">
          <el-input v-model="form.keyword" placeholder="请输入关键词，多个用逗号分隔" />
        </el-form-item>
        <el-form-item label="匹配模式" prop="matchType">
          <el-select v-model="form.matchType" placeholder="请选择匹配模式">
            <el-option label="包含匹配" value="CONTAINS" />
            <el-option label="精确匹配" value="EXACT" />
            <el-option label="全部包含" value="ALL_CONTAINS" />
            <el-option label="前缀匹配" value="STARTS_WITH" />
            <el-option label="后缀匹配" value="ENDS_WITH" />
            <el-option label="模糊匹配" value="FUZZY" />
            <el-option label="正则匹配" value="REGEX" />
          </el-select>
        </el-form-item>
        <el-form-item label="回复类型" prop="replyType">
          <el-radio-group v-model="form.replyType">
            <el-radio label="TEXT">文本</el-radio>
            <el-radio label="IMAGE">图片</el-radio>
            <el-radio label="NEWS">图文</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="回复内容" prop="replyContent">
          <el-input
            v-if="form.replyType === 'TEXT'"
            v-model="form.replyContent"
            type="textarea"
            :rows="4"
            placeholder="请输入回复内容"
          />
          <el-input v-else v-model="form.replyContent" placeholder="请输入素材ID或URL" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
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
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getReplyRuleList, createReplyRule, updateReplyRule, deleteReplyRule } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const dialogVisible = ref(false)
const isEdit = ref(false)

const formRef = ref(null)
const form = ref({
  accountId: userStore.selectedAccountId,
  name: '',
  keyword: '',
  matchType: 'CONTAINS',
  replyType: 'TEXT',
  replyContent: '',
  status: 1
})

const rules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  keyword: [{ required: true, message: '请输入关键词', trigger: 'blur' }],
  replyContent: [{ required: true, message: '请输入回复内容', trigger: 'blur' }]
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getReplyRuleList({ pageNum: page.value, pageSize: pageSize.value, accountId: userStore.selectedAccountId })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleAdd = () => {
  isEdit.value = false
  form.value = { accountId: userStore.selectedAccountId, name: '', keyword: '', matchType: 'CONTAINS', replyType: 'TEXT', replyContent: '', status: 1 }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form.value, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该规则吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteReplyRule(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error(error)
    }
  })
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        if (isEdit.value) {
          await updateReplyRule(form.value.id, form.value)
        } else {
          await createReplyRule(form.value)
        }
        ElMessage.success('保存成功')
        dialogVisible.value = false
        loadData()
      } catch (error) {
        console.error(error)
      } finally {
        loading.value = false
      }
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
</style>
