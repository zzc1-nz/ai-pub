<template>
  <div class="message-list">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>消息记录</span>
          <el-button size="small" @click="loadData" icon="Refresh">刷新</el-button>
        </div>
      </template>
      <el-table :data="tableData" v-loading="loading">
        <el-table-column prop="openid" label="发送者" />
        <el-table-column prop="toUserName" label="接收者" />
        <el-table-column prop="msgType" label="消息类型">
          <template #default="{ row }">
            <el-tag>{{ row.msgType }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="content" label="消息内容" show-overflow-tooltip />
        <el-table-column prop="replyContent" label="回复内容" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="接收时间">
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
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { ElNotification } from 'element-plus'
import { getMessageList } from '@/api'
import { formatDate } from '@/utils/date'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client'

const tableData = ref([])
const loading = ref(false)
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
let stompClient = null

const loadData = async () => {
  loading.value = true
  try {
    const res = await getMessageList({ pageNum: page.value, pageSize: pageSize.value, accountId: userStore.selectedAccountId })
    tableData.value = res.data.records || []
    total.value = res.data.total || 0
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const connectWebSocket = () => {
  stompClient = new Client({
    webSocketFactory: () => new SockJS('/ws'),
    onConnect: () => {
      console.log('WebSocket 已连接')
      stompClient.subscribe('/topic/messages', (msg) => {
        const data = JSON.parse(msg.body)
        // 同一消息：有 replyContent 说明是回复更新，否则是新消息
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
