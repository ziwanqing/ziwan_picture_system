<template>
  <div class="space-user-analyze">
    <a-card title="空间图片分析">
      <template #extra>
        <a-space>
          <!-- 只有管理员看到 spaceId 搜索框 -->
          <a-input-search
            v-if="isAdmin"
            placeholder="请输入 Space ID"
            enter-button="查询"
            @search="onSpaceIdSearch"
          />
          <a-segmented v-model:value="timeDimension" :options="timeDimensionOptions" />
        </a-space>
      </template>

      <v-chart
        :option="options"
        style="height: 320px; max-width: 100%"
        :loading="loading"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { ref, computed, watchEffect, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getSpaceUserAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { getUserSpaceIdUsingPost } from "@/api/spaceController.ts";

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: string
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 当前登录用户信息
const loginUserStore = useLoginUserStore()
const isAdmin = computed(() => loginUserStore.loginUser.userRole === 'admin')

// 路由和跳转
const route = useRoute()
const router = useRouter()

// 时间维度
const timeDimension = ref<'day' | 'week' | 'month'>('day')
const timeDimensionOptions = [
  { label: '日', value: 'day' },
  { label: '周', value: 'week' },
  { label: '月', value: 'month' },
]

// 图表数据
const dataList = ref<API.SpaceCategoryAnalyzeResponse[]>([])
const loading = ref(true)

// 当前查询的 spaceId（字符串保证精度）
const spaceId = ref<string>('')

// 获取默认空间 ID（当前用户）
const fetchDefaultSpaceId = async () => {
  try {
    const res = await getUserSpaceIdUsingPost()
    if (res.data.code === 0 && res.data.data) {
      spaceId.value = res.data.data.toString()
      router.replace({ query: { ...route.query, spaceId: spaceId.value } })
      fetchData()
    } else {
      message.error('获取默认空间失败')
    }
  } catch {
    message.error('获取默认空间失败')
  }
}

// 搜索新的 spaceId
const onSpaceIdSearch = async (value: string) => {
  const newSpaceId = value.trim()
  if (!newSpaceId) {
    fetchDefaultSpaceId()
    return
  }

  try {
    const res = await getSpaceUserAnalyzeUsingPost({
      spaceId: newSpaceId,
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      timeDimension: timeDimension.value,
    })
    if (res.data.code === 0 && res.data.data) {
      spaceId.value = newSpaceId
      router.replace({ query: { ...route.query, spaceId: newSpaceId } })
      dataList.value = res.data.data ?? []
    } else {
      message.error('空间不存在或无数据，已回到默认空间')
      fetchDefaultSpaceId()
    }
  } catch {
    message.error('查询失败，已回到默认空间')
    fetchDefaultSpaceId()
  }
}

// 获取分析数据
const fetchData = async () => {
  if (!spaceId.value) return
  loading.value = true
  try {
    const res = await getSpaceUserAnalyzeUsingPost({
      spaceId: spaceId.value,
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      timeDimension: timeDimension.value,
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
    } else {
      dataList.value = []
    }
  } catch {
    dataList.value = []
  } finally {
    loading.value = false
  }
}

// 挂载时读取 URL query 或默认空间
onMounted(() => {
  const querySpaceId = (route.query.spaceId as string) || props.spaceId
  if (querySpaceId) {
    spaceId.value = querySpaceId
    fetchData()
  } else {
    fetchDefaultSpaceId()
  }
})

// 监听 spaceId 或时间维度变化刷新数据
watchEffect(() => {
  if (spaceId.value) fetchData()
})

// 将周编号转成 MM/DD~MM/DD
const formatWeekRange = (period: string) => {
  if (!period || period.length !== 6) return period
  const year = parseInt(period.slice(0, 4))
  const week = parseInt(period.slice(4, 6))

  // ISO 周算法：第 1 周含 1 月 4 日
  const simple = new Date(year, 0, 1 + (week - 1) * 7)
  const dayOfWeek = simple.getDay() || 7
  const weekStart = new Date(simple)
  weekStart.setDate(simple.getDate() - (dayOfWeek - 1))
  const weekEnd = new Date(weekStart)
  weekEnd.setDate(weekStart.getDate() + 6)

  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(weekStart.getMonth() + 1)}/${pad(weekStart.getDate())}~${pad(weekEnd.getMonth() + 1)}/${pad(weekEnd.getDate())}`
}

// 图表配置
const options = computed(() => {
  const periods = dataList.value.map(item => {
    if (timeDimension.value === 'week') {
      return formatWeekRange(item.period)
    } else {
      return item.period
    }
  })
  const counts = dataList.value.map(item => item.count)

  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: periods, name: '时间区间' },
    yAxis: { type: 'value', name: '上传数量' },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: true,
        emphasis: { focus: 'series' },
      },
    ],
  }
})
</script>

<style scoped></style>
