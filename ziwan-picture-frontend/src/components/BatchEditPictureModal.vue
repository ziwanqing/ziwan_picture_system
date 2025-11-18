<template>
  <a-modal
    v-model:visible="visible"
    title="批量编辑图片"
    :footer="false"
    @cancel="closeModal"
  >
    <a-typography-paragraph type="secondary">
      * 只对选中的图片生效
    </a-typography-paragraph>

    <a-form layout="vertical" :model="formData" @finish="handleSubmit">
      <!-- 分类 -->
      <a-form-item label="分类" name="category">
        <a-auto-complete
          v-model:value="formData.category"
          :options="categoryOptions"
          placeholder="请输入分类"
          allowClear
        />
      </a-form-item>

      <!-- 标签 -->
      <a-form-item label="标签" name="tags">
        <a-select
          v-model:value="formData.tags"
          :options="tagOptions"
          mode="tags"
          placeholder="请输入标签"
          allowClear
        />
      </a-form-item>

      <!-- 命名规则 -->
      <a-form-item label="命名规则" name="nameRule">
        <a-input
          v-model:value="formData.nameRule"
          placeholder="请输入命名规则，输入 {序号} 可动态生成"
        />
      </a-form-item>

      <a-form-item>
        <a-button type="primary" html-type="submit">提交</a-button>
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  batchEditPictureMetadataUsingPost,
  listPictureTagCategoryUsingGet
} from '@/api/pictureController'
import { message } from 'ant-design-vue'

interface Props {
  spaceId: number
  onSuccess: () => void
}

const props = defineProps<Props>()

// 控制弹窗
const visible = ref(false)

// 保存父组件传来的选中图片ID
const selectedIds = ref<number[]>([])

// 打开弹窗并接收选中图片
const openModal = (ids: number[]) => {
  if (!ids || ids.length === 0) {
    message.warning('请先选择图片')
    return
  }
  selectedIds.value = ids
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

// 暴露给父组件
defineExpose({
  openModal
})

// 表单数据
const formData = reactive({
  category: '',
  tags: [],
  nameRule: ''
})

const categoryOptions = ref<{ value: string; label: string }[]>([])
const tagOptions = ref<{ value: string; label: string }[]>([])

// 获取分类和标签选项
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  if (res.data.code === 0 && res.data.data) {
    tagOptions.value = (res.data.data.tagList ?? []).map((t: string) => ({ value: t, label: t }))
    categoryOptions.value = (res.data.data.categoryList ?? []).map((c: string) => ({ value: c, label: c }))
  } else {
    message.error('加载选项失败：' + res.data.message)
  }
}

// 提交表单
const handleSubmit = async (values: any) => {
  if (!selectedIds.value || selectedIds.value.length === 0) {
    message.warning('未选择图片')
    return
  }

  const res = await batchEditPictureMetadataUsingPost({
    pictureIdList: selectedIds.value, // 只提交选中的图片
    spaceId: props.spaceId,
    ...values
  })

  if (res.data.code === 0 && res.data.data) {
    message.success('批量编辑成功')
    closeModal()
    props.onSuccess?.()
  } else {
    message.error('操作失败：' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})
</script>
