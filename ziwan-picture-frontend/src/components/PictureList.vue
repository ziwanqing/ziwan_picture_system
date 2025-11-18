<template>
  <div class="picture-list">
    <a-list
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="dataList"
      :loading="loading"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <a-card hoverable @click="doClickPicture(picture)" class="relative" @mouseenter="hoveredId = picture.id" @mouseleave="hoveredId = null">
            <template #cover>
              <div class="img-wrapper">
                <img
                  :alt="picture.name"
                  :src="picture.thumbnailUrl ?? picture.url"
                  class="picture-img"
                />
                <!-- 勾选按钮 -->
                <div
                  v-if="showSelect"
                  class="select-circle"
                  :data-id="picture.id"
                  :class="{ selected: selectedIds.includes(picture.id), visible: hoveredId === picture.id || selectedIds.includes(picture.id) }"
                  @click.stop="toggleSelect(picture.id)"
                >
                  <span v-if="selectedIds.includes(picture.id)">✔</span>
                </div>

              </div>
            </template>

            <a-card-meta :title="picture.name">
              <template #description>
                <a-flex>
                  <a-tag color="green">{{ picture.category ?? '默认' }}</a-tag>
                  <a-tag v-for="tag in picture.tags" :key="tag">{{ tag }}</a-tag>
                </a-flex>
              </template>
            </a-card-meta>

            <template #actions>
              <a-space v-if="showOp" @click="(e) => doSearch(picture, e)">
                <search-outlined /> 搜索
              </a-space>
              <a-space v-if="showOp" @click="(e) => doEdit(picture, e)">
                <edit-outlined /> 编辑
              </a-space>
              <a-space v-if="showOp" @click="(e) => doDelete(picture, e)">
                <delete-outlined /> 删除
              </a-space>
            </template>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup lang="ts">
import {ref, watch} from 'vue'
import { useRouter } from 'vue-router'
import { deletePictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  showSelect?: boolean
  selectedIds?: number[]
  onReload?: () => void
}

const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false,
  showSelect: false,
})
const router = useRouter()
const emit = defineEmits(['update:selectedIds'])


const selectedIds = ref<number[]>([])
const hoveredId = ref<number | null>(null)

const toggleSelect = (id: number) => {
  if (selectedIds.value.includes(id)) {
    selectedIds.value = selectedIds.value.filter(x => x !== id)
  } else {
    selectedIds.value.push(id)
  }
  emit('update:selectedIds', selectedIds.value)
}

const doClickPicture = (picture: API.PictureVO) => {
  router.push({ path: `/picture/${picture.id}` })
}

const doEdit = (picture, e) => {
  e.stopPropagation()
  router.push({ path: '/add_picture', query: { id: picture.id, spaceId: picture.spaceId } })
}

const doDelete = async (picture, e) => {
  e.stopPropagation()
  const res = await deletePictureUsingPost({ id: picture.id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props.onReload?.()
  } else {
    message.error('删除失败')
  }
}

const doSearch = (picture, e) => {
  e.stopPropagation()
  window.open(`/search_picture?pictureId=${picture.id}`)
}

watch(() => props.selectedIds, (newVal) => {
  selectedIds.value = newVal
})

const clearSelected = () => {
  selectedIds.value = []
}
defineExpose({ clearSelected })

</script>

<style scoped>
.picture-img {
  height: 180px;
  width: 100%;
  object-fit: cover;
  border-radius: 6px;
}

.img-wrapper {
  position: relative;
}

/* 勾选按钮 */
.select-circle {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border: 2px solid white;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.35);
  color: white;
  font-size: 14px;
  font-weight: bold;
  text-align: center;
  line-height: 22px;
  cursor: pointer;
  transition: all 0.2s;
  opacity: 0;
  pointer-events: none; /* 默认不可点击 */
}

.select-circle.visible {
  opacity: 1;
  pointer-events: auto; /* 可点击 */
}

.select-circle {
  position: absolute;
  top: 8px;
  right: 8px;
  width: 24px;
  height: 24px;
  border: 2px solid white;
  border-radius: 50%;
  background-color: rgba(0, 0, 0, 0.35);
  color: white;
  font-size: 14px;
  font-weight: bold;
  text-align: center;
  line-height: 22px;
  cursor: pointer;
  transition: all 0.3s;
}

.select-circle.selected {
  background-color: #1890ff;
  border-color: #1890ff;
}



</style>
