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
              <div class="stat-value">{{ statistics.messageCount || 0 }}</div>
              <div class="stat-label">消息总数</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background: #e6a23c">
              <el-icon :size="30"><Picture /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ statistics.materialCount || 0 }}</div>
              <div class="stat-label">素材数量</div>
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
              <span>消息趋势</span>
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
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import * as echarts from 'echarts'
import { getStatistics } from '@/api'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const chart1Ref = ref(null)
const chart2Ref = ref(null)
let chart1 = null
let chart2 = null

const statistics = ref({})
const messageTrend = ref([0, 0, 0, 0, 0, 0, 0])
const weekDayLabels = ref(['周一', '周二', '周三', '周四', '周五', '周六', '周日'])
const materialDistribution = ref([])

const loadStatistics = async () => {
  try {
    const res = await getStatistics({ accountId: userStore.selectedAccountId })
    statistics.value = res.data
    
    // 处理消息趋势数据 — 最近7天，x轴标签与实际星期对齐
    const dailyCount = res.data.dailyMessageCount || {}
    const dayLabels = []
    const trendData = []
    for (let i = 6; i >= 0; i--) {
      const date = new Date()
      date.setDate(date.getDate() - i)
      const dateStr = date.toISOString().split('T')[0]
      const weekDayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
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
        smooth: true
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
