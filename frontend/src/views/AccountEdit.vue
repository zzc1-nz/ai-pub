<template>
  <div class="account-edit">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>{{ isEdit ? '编辑公众号' : '新增公众号' }}</span>
          <el-button @click="$router.back()">返回</el-button>
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px" style="max-width: 600px">
        <el-form-item label="公众号名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入公众号名称" />
        </el-form-item>
        <el-form-item label="AppID" prop="appId">
          <el-input v-model="form.appId" placeholder="请输入AppID" />
        </el-form-item>
        <el-form-item label="AppSecret" prop="appSecret">
          <el-input
            v-model="form.appSecret"
            :placeholder="isEdit ? '留空则保持原值不变' : '请输入AppSecret'"
            show-password
          />
          <div v-if="isEdit" class="field-hint">
            出于安全考虑，接口不再返回已保存的 AppSecret。不修改请留空。
          </div>
        </el-form-item>
        <el-form-item label="Token" prop="token">
          <el-input v-model="form.token" placeholder="请输入Token" />
        </el-form-item>
        <el-form-item label="EncodingAESKey" prop="encodingAesKey">
          <el-input
            v-model="form.encodingAesKey"
            :placeholder="isEdit ? '留空则保持原值不变' : '请输入EncodingAESKey'"
          />
          <div v-if="isEdit" class="field-hint">
            出于安全考虑，接口不再返回已保存的 EncodingAESKey。不修改请留空。
          </div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :label="1">启用</el-radio>
            <el-radio :label="0">禁用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="AI启用" prop="aiEnabled">
          <el-switch v-model="form.aiEnabled" />
        </el-form-item>
        <el-form-item label="默认回复" prop="defaultReply">
          <el-input v-model="form.defaultReply" type="textarea" :rows="3" placeholder="请输入默认回复内容" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSubmit" :loading="loading">保存</el-button>
          <el-button @click="$router.back()">取消</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getAccount, createAccount, updateAccount } from '@/api'

const route = useRoute()
const router = useRouter()

const formRef = ref(null)
const loading = ref(false)
const isEdit = computed(() => !!route.params.id)

// 字段名必须与后端 WechatAccount 实体一致：
// 原为 aesKey，后端实际是 encodingAesKey，导致该值被 Jackson 静默丢弃
// （JacksonConfig 设置了 FAIL_ON_UNKNOWN_PROPERTIES=false），EncodingAESKey 永远存不上。
const form = ref({
  name: '',
  appId: '',
  appSecret: '',
  token: '',
  encodingAesKey: '',
  status: 1,
  aiEnabled: true,
  defaultReply: '正在转接人工客服'
})

// appSecret 仅在新增时必填：编辑态下后端不再下发原值，
// 留空表示"保持原值不变"，因此不能强制要求填写。
const rules = {
  name: [{ required: true, message: '请输入公众号名称', trigger: 'blur' }],
  appId: [{ required: true, message: '请输入AppID', trigger: 'blur' }],
  appSecret: [{
    validator: (rule, value, callback) => {
      if (!isEdit.value && !value) {
        callback(new Error('请输入AppSecret'))
      } else {
        callback()
      }
    },
    trigger: 'blur'
  }]
}

const loadData = async (id) => {
  try {
    const res = await getAccount(id)
    Object.assign(form.value, res.data)
  } catch (error) {
    console.error(error)
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (valid) {
      loading.value = true
      try {
        if (isEdit.value) {
          await updateAccount(route.params.id, form.value)
        } else {
          await createAccount(form.value)
        }
        ElMessage.success('保存成功')
        router.back()
      } catch (error) {
        console.error(error)
      } finally {
        loading.value = false
      }
    }
  })
}

onMounted(() => {
  if (isEdit.value) {
    loadData(route.params.id)
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.field-hint {
  font-size: 12px;
  color: #909399;
  line-height: 1.5;
  margin-top: 4px;
}
</style>
