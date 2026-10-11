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
      <el-alert title="通过下方“排序”列的数值调整菜单顺序，数值小的排在前面" type="info" show-icon :closable="false" style="margin-bottom: 16px" />
      <el-table :data="tableData" v-loading="loading" row-key="id">
        <el-table-column prop="name" label="菜单名称" />
        <el-table-column prop="type" label="菜单类型">
          <template #default="{ row }">
            <el-tag>{{ row.type === 'click' ? '点击事件' : '跳转链接' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="key" label="菜单Key" />
        <el-table-column prop="url" label="跳转链接" show-overflow-tooltip />
        <el-table-column prop="sortOrder" label="排序" width="80">
          <template #default="{ row }">
            <el-input-number v-model="row.sortOrder" :min="0" size="small" @change="handleSortChange(row)" style="width: 70px" />
          </template>
        </el-table-column>
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
        <el-form-item label="上级菜单">
          <el-select v-model="form.parentId" placeholder="无（一级菜单）" clearable>
            <el-option label="无（一级菜单）" :value="0" />
            <el-option v-for="item in parentMenus" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="菜单类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio label="click">点击事件</el-radio>
            <el-radio label="view">跳转链接</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 'click'" label="菜单Key" prop="key">
          <el-input v-model="form.key" placeholder="请输入菜单Key" />
        </el-form-item>
        <el-form-item v-else label="跳转链接" prop="url">
          <el-input v-model="form.url" placeholder="请输入跳转链接（http/https）" />
        </el-form-item>
        <el-form-item label="排序" prop="sortOrder">
          <el-input-number v-model="form.sortOrder" :min="0" />
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
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMenuList, createMenu, updateMenu, deleteMenu, syncMenu } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const tableData = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(false)

// 字段名与后端 WechatMenu 实体对齐：
//   sort  -> sortOrder（原字段名错误，弹窗里的排序完全无效）
//   url   -> view 类型菜单的跳转链接（原先无论 click/view 都写进 key，
//            后端 WechatApiService 对 view 类型读的是 url，同步到微信会生成非法按钮）
const formRef = ref(null)
const form = ref({
  name: '',
  type: 'click',
  key: '',
  url: '',
  sortOrder: 0,
  parentId: null,
  accountId: null
})

const rules = {
  name: [{ required: true, message: '请输入菜单名称', trigger: 'blur' }],
  key: [{
    validator: (rule, value, callback) => {
      if (form.value.type === 'click' && !value) {
        callback(new Error('请输入菜单Key'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }],
  url: [{
    validator: (rule, value, callback) => {
      if (form.value.type === 'view' && !value) {
        callback(new Error('请输入跳转链接'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }]
}

// 一级菜单列表（用于选择上级菜单）
const parentMenus = computed(() => {
  return tableData.value.filter(item => !item.parentId || item.parentId === 0)
})

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
  form.value = { name: '', type: 'click', key: '', url: '', sortOrder: 0, parentId: 0, accountId: userStore.selectedAccountId }
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

const handleSortChange = async (row) => {
  try {
    await updateMenu(row.id, { sortOrder: row.sortOrder })
    ElMessage.success('排序已更新')
  } catch (error) {
    console.error(error)
    loadData()
  }
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
