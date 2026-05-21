<template>
  <div class="menu-manage">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>菜单管理</span>
          <div class="header-actions">
            <el-button type="primary" @click="handleAdd">
              <el-icon><Plus /></el-icon>
              新增菜单
            </el-button>
            <el-button type="success" @click="handleSync">
              <el-icon><Upload /></el-icon>
              同步到微信
            </el-button>
          </div>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading" row-key="id" :tree-props="{ children: 'children' }">
        <el-table-column prop="name" label="菜单名称" />
        <el-table-column prop="type" label="菜单类型">
          <template #default="{ row }">
            <el-tag>{{ row.type === 'click' ? '点击事件' : '跳转链接' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="key" label="菜单Key/URL" />
        <el-table-column prop="sortOrder" label="排序" />
        <el-table-column label="操作" width="250">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑菜单' : '新增菜单'" width="500px">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="100px">
        <el-form-item label="菜单名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入菜单名称" />
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio label="click">点击事件</el-radio>
            <el-radio label="view">跳转链接</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="form.type === 'click' ? '菜单Key' : '跳转链接'" prop="key">
          <el-input v-model="form.key" :placeholder="form.type === 'click' ? '请输入菜单Key' : '请输入跳转链接'" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
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
import { getMenuList, createMenu, updateMenu, deleteMenu, syncMenu } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

const formRef = ref(null)
const form = ref({
  name: '',
  type: 'click',
  key: '',
  sort: 0,
  parentId: null,
  accountId: null
})

const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  key: [{ required: true, message: '请输入菜单Key/URL', trigger: 'blur' }]
}

const loadData = async () => {
  if (!userStore.selectedAccountId) return
  loading.value = true
  try {
    const res = await getMenuList(userStore.selectedAccountId)
    tableData.value = res.data || []
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

watch(() => userStore.selectedAccountId, () => {
  loadData()
})

const handleAdd = () => {
  isEdit.value = false
  form.value = { name: '', type: 'click', key: '', sort: 0, parentId: null, accountId: userStore.selectedAccountId }
  dialogVisible.value = true
}

const handleEdit = (row) => {
  isEdit.value = true
  Object.assign(form.value, row)
  dialogVisible.value = true
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要删除该菜单吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteMenu(row.id)
      ElMessage.success('删除成功')
      loadData()
    } catch (error) {
      console.error(error)
    }
  })
}

const handleSync = () => {
  if (!userStore.selectedAccountId) {
    ElMessage.warning('请先在顶部选择公众号')
    return
  }
  ElMessageBox.confirm('确定要同步菜单到微信吗？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await syncMenu(userStore.selectedAccountId)
      ElMessage.success('同步成功')
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
          await updateMenu(form.value.id, form.value)
        } else {
          await createMenu(form.value)
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
.header-actions {
  display: flex;
  align-items: center;
}
</style>
