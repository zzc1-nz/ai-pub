<template>
  <div class="dashboard">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #409eff">
              <el-icon :size="30"><OfficeBuilding /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.accountCount || 0 }}</div>
              <div class="stat-label">公众号数量</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #67c23a">
              <el-icon :size="30"><Message /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.todayMessages || 0 }}</div>
              <div class="stat-label">今日消息</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #e6a23c">
              <el-icon :size="30"><DataLine /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.monthMessages || 0 }}</div>
              <div class="stat-label">本月消息</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #f56c6c">
              <el-icon :size="30"><User /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.userCount || 0 }}</div>
              <div class="stat-label">系统用户</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>消息趋势（近7天）</span>
            </div>
          </template>
          <div ref="chart1Ref" class="chart"></div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>素材类型分布</span>
            </div>
          </template>
          <div ref="chart2Ref" class="chart"></div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>常见问题 Top 10</span>
            </div>
          </template>
          <el-table :data="topQuestions" stripe>
            <el-table-column type="index" label="排名" width="80" />
            <el-table-column prop="content" label="问题内容" />
            <el-table-column prop="count" label="出现次数" width="120" />
          </el-table>
          <el-empty v-if="!topQuestions.length" description="暂无数据" />
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import * as echarts from 'echarts'
import { getStatistics } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const chart1Ref = ref(null)
const chart2Ref = ref(null)
let chart1 = null
let chart2 = null

const statistics = ref({})
const topQuestions = ref([])
const messageTrend = ref([0, 0, 0, 0, 0, 0, 0])
const weekDayLabels = ref(['周一', '周二', '周三', '周四', '周五', '周六', '周日'])
const materialDistribution = ref([])

const loadStatistics = async () => {
  try {
    const res = await getStatistics({ accountId: userStore.selectedAccountId })
    statistics.value = res.data

    // Top N 常见问题
    topQuestions.value = res.data.topQuestions || []

    // 处理消息趋势数据 — 最近7天
    // 注意：不能用 toISOString()（它是 UTC）。后端 dailyMessageCount 的 key 来自
    // createdAt.toLocalDate()，即 Asia/Shanghai 本地日期；用 UTC 会让本地
    // 00:00–08:00 这 8 小时的当日数据点取错 key 而显示为 0。
    const dailyCount = res.data.dailyMessageCount || {}
    const dayLabels = []
    const trendData = []
    const weekDayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
    for (let i = 6; i >= 0; i--) {
      const date = new Date()
      date.setDate(date.getDate() - i)
      const dateStr = `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
      dayLabels.push(weekDayNames[date.getDay()])
      trendData.push(dailyCount[dateStr] || 0)
    }
    messageTrend.value = trendData
    weekDayLabels.value = dayLabels

    // 处理素材类型分布
    const materialType = res.data.materialTypeDistribution || {}
    materialDistribution.value = [
      { value: materialType['image'] || 0, name: '图片' },
      { value: materialType['video'] || 0, name: '视频' },
      { value: materialType['news'] || 0, name: '图文' },
      { value: materialType['voice'] || 0, name: '语音' }
    ]
  } catch (error) {
    console.error(error)
  }
}

const initChart1 = () => {
  if (!chart1Ref.value) return
  chart1 = echarts.init(chart1Ref.value)
  const option = {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: weekDayLabels.value
    },
    yAxis: {
      type: 'value'
    },
    series: [
      {
        name: '消息数',
        type: 'line',
        data: messageTrend.value,
        smooth: true,
        areaStyle: { opacity: 0.3 }
      }
    ]
  }
  chart1.setOption(option)
}

const initChart2 = () => {
  if (!chart2Ref.value) return
  chart2 = echarts.init(chart2Ref.value)
  const option = {
    tooltip: { trigger: 'item' },
    legend: { orient: 'vertical', left: 'left' },
    series: [
      {
        name: '素材类型',
        type: 'pie',
        radius: '50%',
        data: materialDistribution.value
      }
    ]
  }
  chart2.setOption(option)
}

const handleResize = () => {
  chart1 && chart1.resize()
  chart2 && chart2.resize()
}

// 顶部切换公众号后重新加载统计并重绘图表（原先只在 onMounted 加载一次）
watch(() => userStore.selectedAccountId, async () => {
  await loadStatistics()
  chart1 && chart1.setOption({ xAxis: { data: weekDayLabels.value }, series: [{ data: messageTrend.value }] })
  chart2 && chart2.setOption({ series: [{ data: materialDistribution.value }] })
})

onMounted(async () => {
  await loadStatistics()
  initChart1()
  initChart2()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chart1 && chart1.dispose()
  chart2 && chart2.dispose()
})
</script>

<style scoped>
.stat-card {
  margin-bottom: 0;
}

.stat-content {
  display: flex;
  align-items: center;
  gap: 20px;
}

.stat-icon {
  width: 60px;
  height: 60px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
}

.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #333;
}

.stat-label {
  font-size: 14px;
  color: #999;
  margin-top: 5px;
}

.chart {
  height: 300px;
}
</style>
