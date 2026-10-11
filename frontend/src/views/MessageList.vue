<template>
  <div class="message-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>消息记录</span>
          <el-button size="small" @click="loadData" icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-form :inline="true" :model="searchForm" style="margin-bottom: 16px">
        <el-form-item label="OpenID">
          <el-input v-model="searchForm.openid" placeholder="按发送者搜索" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="消息类型">
          <el-select v-model="searchForm.msgType" placeholder="全部" clearable style="width: 120px">
            <el-option label="文本" value="text" />
            <el-option label="图片" value="image" />
            <el-option label="语音" value="voice" />
            <el-option label="视频" value="video" />
            <el-option label="事件" value="event" />
          </el-select>
        </el-form-item>
        <el-form-item label="时间范围">
          <el-date-picker
            v-model="searchForm.timeRange"
            type="datetimerange"
            range-separator="至"
            start-placeholder="开始时间"
            end-placeholder="结束时间"
            value-format="YYYY-MM-DD HH:mm:ss"
            style="width: 360px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="openid" label="发送者" width="200" />
        <el-table-column prop="msgType" label="消息类型" width="100">
          <template #default="{ row }">
            <el-tag>{{ typeLabels[row.msgType] || row.msgType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="消息内容" show-overflow-tooltip />
        <el-table-column prop="replyContent" label="回复内容" show-overflow-tooltip />
        <el-table-column prop="replyType" label="回复方式" width="100">
          <template #default="{ row }">
            <el-tag :type="replyTypeTag(row.replyType)" v-if="row.replyType">
              {{ replyTypeLabels[row.replyType] || row.replyType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="接收时间" width="180">
          <template #default="{ row }">
            {{ formatDate(row.createdAt) }}
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, watch } from 'vue'
import { ElNotification } from 'element-plus'
import { getMessageList } from '@/api'
import { formatDate } from '@/utils/date'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const typeLabels = { text: '文本', image: '图片', voice: '语音', video: '视频', event: '事件' }
const replyTypeLabels = { AI: 'AI回复', KEYWORD: '关键词', DEFAULT: '默认' }
const replyTypeTag = (type) => ({ AI: 'success', KEYWORD: 'primary', DEFAULT: 'info' }[type] || '')

const searchForm = reactive({
  openid: '',
  msgType: '',
  timeRange: null
})

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
let stompClient = null

const loadData = async () => {
  loading.value = true
  try {
    const params = {
      pageNum: page.value,
      pageSize: pageSize.value,
      accountId: userStore.selectedAccountId
    }
    if (searchForm.openid) params.openid = searchForm.openid
    if (searchForm.msgType) params.msgType = searchForm.msgType
    if (searchForm.timeRange && searchForm.timeRange.length === 2) {
      params.startTime = searchForm.timeRange[0]
      params.endTime = searchForm.timeRange[1]
    }
    const res = await getMessageList(params)
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  loadData()
}

const handleReset = () => {
  searchForm.openid = ''
  searchForm.msgType = ''
  searchForm.timeRange = null
  page.value = 1
  loadData()
}

const connectWebSocket = () => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    onConnect: () => {
      console.log('WebSocket 已连接')
      stompClient.subscribe('/topic/messages', (msg) => {
        const data = JSON.parse(msg.body)
        const idx = tableData.value.findIndex(m => m.id === data.id)
        if (idx >= 0) {
          tableData.value[idx] = data
        } else {
          tableData.value.unshift(data)
          total.value++
        }
        if (data.replyContent) {
          ElNotification({
            title: '新回复',
            message: data.replyContent.substring(0, 50),
            type: 'success',
            duration: 3000
          })
        }
      })
    },
    onStompError: (frame) => {
      console.error('WebSocket 错误: ' + frame.headers['message'])
    }
  })
  stompClient.activate()
}

onMounted(() => {
  loadData()
  connectWebSocket()
})

onBeforeUnmount(() => {
  if (stompClient) {
    stompClient.deactivate()
  }
})
</script>

<style scoped>
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
