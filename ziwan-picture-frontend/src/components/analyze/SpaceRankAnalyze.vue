<template>
  <div class="space-rank-analyze">
    <a-card title="空间使用排行分析">
      <v-chart
        :option="options"
        style="height: 320px; max-width: 100%;"
        :loading="loading"
      />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { getSpaceRankAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'
import { message } from 'ant-design-vue'

interface Props {
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: number
}

const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 图表数据
const dataList = ref<API.Space[]>([])
// 加载状态
const loading = ref(true)

// 获取数据
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getSpaceRankAnalyzeUsingPost({
      queryAll: props.queryAll,
      queryPublic: props.queryPublic,
      spaceId: props.spaceId,
      topN: 10, // 后端默认是 10
    })
    if (res.data.code === 0 && res.data.data) {
      dataList.value = res.data.data ?? []
    } else {
      message.error('获取数据失败：' + res.data.message)
    }
  } catch (e) {
    message.error('请求出错，请稍后重试')
  } finally {
    loading.value = false
  }
}

/**
 * 监听变量，参数改变时触发数据的重新加载
 */
watchEffect(() => {
  fetchData()
})

// 图表选项
const options = computed(() => {
  const spaceNames = dataList.value.map((item) => item.spaceName)
  // 每个数据项包含 value 和 userId
  const usageData = dataList.value.map((item) => ({
    value: (item.totalSize / (1024 * 1024)).toFixed(2), // 转为 MB
    userId: item.userId ?? '未知',
  }))

  return {
    tooltip: {
      trigger: 'axis',
      // 自定义 tooltip 格式化
      formatter: (params: any) => {
        const data = params[0].data
        return `
          <div style="padding: 4px 8px;">
            <strong>${params[0].axisValue}</strong><br/>
            使用量：${data.value} MB<br/>
            用户ID：${data.userId}
          </div>
        `
      },
      backgroundColor: 'rgba(50,50,50,0.8)',
      borderColor: '#aaa',
      textStyle: { color: '#fff' },
    },
    grid: {
      left: '5%',
      right: '5%',
      bottom: '10%',
      containLabel: true,
    },
    xAxis: {
      type: 'category',
      data: spaceNames,
      axisLabel: { rotate: 30, color: '#666' },
    },
    yAxis: {
      type: 'value',
      name: '空间使用量 (MB)',
      nameTextStyle: { color: '#666' },
    },
    series: [
      {
        name: '空间使用量 (MB)',
        type: 'bar',
        data: usageData,
        itemStyle: {
          color: '#5470C6',
          borderRadius: [4, 4, 0, 0],
        },
      },
    ],
  }
})
</script>

<style scoped>

</style>
