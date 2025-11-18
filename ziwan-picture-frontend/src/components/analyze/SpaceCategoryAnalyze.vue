<template>
  <div class="space-category-analyze">
    <a-card title="空间图片分类分析">
      <v-chart
        :option="options"
        style="height: 320px; max-width: 100%;"
        :loading="loading"
      />
      <!-- 空数据提示 -->
      <template v-if="!loading && dataList.length === 0">
        <div class="empty-tip">暂无数据</div>
      </template>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { ref, computed, watch } from 'vue'
import { getSpaceCategoryAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'

// Props
interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number
  userId?: string // 新增 userId
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 响应式变量
const dataList = ref<API.SpaceCategoryAnalyzeResponse>([])
const loading = ref(true)

// 数据请求函数
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceCategoryAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
      userId: props.userId, // 加上 userId
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
    } else {
      dataList.value = []
      message.error('获取数据失败：' + res.data.message)
    }
  } catch (error: any) {
    dataList.value = []
    message.error('请求出错：' + error.message)
  } finally {
    loading.value = false
  }
}

// 监听 props 变化
watch(
  () => [props.queryAll, props.queryPublic, props.spaceId, props.userId],
  fetchData,
  { immediate: true }
)

// 图表配置
const options = computed(() => {
  const categories = dataList.value.map(item => item.category)
  const countData = dataList.value.map(item => item.count)
  const sizeData = dataList.value.map(item => Number((item.totalSize / (1024 * 1024)).toFixed(2))) // MB

  return {
    color: ['#5470C6', '#91CC75'],
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
    legend: { data: ['图片数量', '图片总大小'], top: 'bottom' },
    xAxis: { type: 'category', data: categories },
    yAxis: [
      { type: 'value', name: '图片数量', axisLine: { lineStyle: { color: '#5470C6' } } },
      {
        type: 'value',
        name: '图片总大小 (MB)',
        position: 'right',
        axisLine: { lineStyle: { color: '#91CC75' } },
        splitLine: { lineStyle: { color: '#91CC75', type: 'dashed' } },
      },
    ],
    series: [
      { name: '图片数量', type: 'bar', data: countData, yAxisIndex: 0 },
      { name: '图片总大小', type: 'line', data: sizeData, yAxisIndex: 1 },
    ],
  }
})
</script>

<style scoped>
.empty-tip {
  text-align: center;
  color: rgba(0,0,0,0.45);
  margin-top: 50px;
  font-size: 14px;
}
</style>
