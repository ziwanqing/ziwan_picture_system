<template>
  <a-modal
    class="image-cropper"
    v-model:visible="visible"
    title="编辑图片"
    :footer="false"
    @cancel="closeModal"
  >
    <!-- 图片裁切组件 -->
    <vue-cropper
      ref="cropperRef"
      :img="imageUrl"
      output-type="png"
      :info="true"
      :can-move-box="true"
      :fixed-box="false"
      :auto-crop="true"
      :center-box="true"
    />
    <div style="margin-bottom: 16px" />
    <!-- 协同编辑操作 -->
    <div class="image-edit-actions" v-if="isTeamSpace">
      <a-space>
        <a-button v-if="editingUser" disabled>{{ editingUser.userName }} 正在编辑</a-button>
        <a-button v-if="canEnterEdit" type="primary" ghost @click="enterEdit">进入编辑</a-button>
        <a-button v-if="canExitEdit" danger ghost @click="exitEdit">退出编辑</a-button>
      </a-space>
    </div>
    <div style="margin-bottom: 16px" />
    <!-- 图片操作 -->
    <div class="image-cropper-actions">
      <a-space>
        <a-button @click="rotateLeft" :disabled="!canEdit">向左旋转</a-button>
        <a-button @click="rotateRight" :disabled="!canEdit">向右旋转</a-button>
        <a-button @click="changeScale(1)" :disabled="!canEdit">放大</a-button>
        <a-button @click="changeScale(-1)" :disabled="!canEdit">缩小</a-button>
        <a-button type="primary" :loading="loading" :disabled="!canEdit" @click="handleConfirm">
          确认
        </a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
import { ref, computed, onUnmounted, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebsocket.ts'
import { PICTURE_EDIT_ACTION_ENUM, PICTURE_EDIT_MESSAGE_TYPE_ENUM } from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

interface Props {
  imageUrl?: string
  picture?: API.PictureVO
  spaceId?: number
  space?: API.SpaceVO
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

// ----------------- 状态 -----------------
const visible = ref(false)
const cropperRef = ref<any>()
const loading = ref(false)
const spaceData = ref<API.SpaceVO | null>(props.space || null)

// 实时编辑
const editingUser = ref<API.UserVO>()
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser
let websocket: PictureEditWebSocket | null = null

// ----------------- 计算属性 -----------------
const isTeamSpace = computed(() => spaceData.value?.spaceType === SPACE_TYPE_ENUM.TEAM)
const canEnterEdit = computed(() => !editingUser.value)
const canExitEdit = computed(() => editingUser.value?.id === loginUser.id)
const canEdit = computed(() => {
  if (!isTeamSpace.value) return true
  return editingUser.value?.id === loginUser.id
})

// ----------------- 打开/关闭 -----------------
const openModal = async () => {
  visible.value = true
  // 如果没有传 spaceData，则根据 spaceId 调接口获取
  if (!spaceData.value && props.spaceId) {
    try {
      const res = await getSpaceVoByIdUsingGet({ id: props.spaceId })
      if (res.data.code === 0 && res.data.data) {
        spaceData.value = res.data.data
      } else {
        message.error('获取空间信息失败: ' + res.data.message)
      }
    } catch (e: any) {
      message.error('获取空间信息失败: ' + e.message)
    }
  }
}

const closeModal = () => {
  visible.value = false
  websocket?.disconnect()
  websocket = null
  editingUser.value = undefined
}

defineExpose({ openModal })

// ----------------- 图片操作 -----------------
const changeScale = (num: number) => {
  cropperRef.value?.changeScale(num)
  editAction(num > 0 ? PICTURE_EDIT_ACTION_ENUM.ZOOM_IN : PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
}

const rotateLeft = () => {
  cropperRef.value?.rotateLeft()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}

const rotateRight = () => {
  cropperRef.value?.rotateRight()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
}

const handleConfirm = () => {
  cropperRef.value.getCropBlob((blob: Blob) => {
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    handleUpload({ file })
  })
}

const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    if (props.spaceId) params.spaceId = props.spaceId
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败: ' + res.data.message)
    }
  } catch (error: any) {
    console.error(error)
    message.error('图片上传失败: ' + error.message)
  }
  loading.value = false
}

// ----------------- 实时协作 -----------------
const initWebsocket = () => {
  const pictureId = props.picture?.id
  if (!pictureId || !visible.value) return

  websocket?.disconnect()
  websocket = new PictureEditWebSocket(pictureId)
  websocket.connect()

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => message.info(msg.message))
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => message.error(msg.message))
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
    message.info(msg.message)
    editingUser.value = msg.user
  })
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
    switch (msg.editAction) {
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT: rotateLeft(); break
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT: rotateRight(); break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN: changeScale(1); break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT: changeScale(-1); break
    }
  })
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
    message.info(msg.message)
    editingUser.value = undefined
  })
}

watchEffect(() => {
  if (visible.value && isTeamSpace.value) initWebsocket()
})

onUnmounted(() => websocket?.disconnect())

// 进入/退出编辑
const enterEdit = () => {
  websocket?.sendMessage({ type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT })
}
const exitEdit = () => {
  websocket?.sendMessage({ type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT })
}
const editAction = (action: string) => {
  websocket?.sendMessage({ type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, editAction: action })
}
</script>

<style>
.image-cropper { text-align: center; }
.image-cropper .vue-cropper { height: 400px !important; }
</style>
