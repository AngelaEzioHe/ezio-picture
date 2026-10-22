<template>
  <a-modal
    class="image-out-painting"
    v-model:visible="visible"
    title="AI 扩图"
    :footer="false"
    @cancel="closeModal"
  >
    <a-row gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" :alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          :alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-flex justify="center" gap="16">
      <a-button type="primary" :loading="creating || !!taskId" ghost @click="createTask"
        >生成图片</a-button
      >
      <a-button v-if="resultImageUrl" type="primary" :loading="uploadLoading" @click="handleUpload">
        应用结果
      </a-button>
    </a-flex>
  </a-modal>
</template>

<script lang="ts" setup>
import { onUnmounted, ref } from 'vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const resultImageUrl = ref<string>('')

// 任务 id
const taskId = ref<string>()
const creating = ref(false)

/**
 * 创建任务
 */
const createTask = async () => {
  if (!props.picture?.id) {
    return
  }
  if (creating.value || taskId.value) return
  creating.value = true
  resultImageUrl.value = ''
  try {
    const res = await createPictureOutPaintingTaskUsingPost(
      {
        pictureId: props.picture.id,
        // 根据需要设置扩图参数
        parameters: {
          topScale: 1.5,
          bottomScale: 1.5,
          leftScale: 1.5,
          rightScale: 1.5,
        },
      },
      { timeout: 35000 },
    )
    if (!visible.value) return
    if (res.data.code === 0 && res.data.data?.output?.taskId) {
      message.success('创建任务成功，请耐心等待，不要退出界面')
      taskId.value = res.data.data.output.taskId
      // 开启轮询
      startPolling()
    } else {
      message.error('图片任务失败，' + res.data.message)
    }
  } catch (error) {
    message.error('创建扩图任务失败，请稍后重试')
    console.error(error)
  } finally {
    creating.value = false
  }
}

// 轮询定时器
let pollingTimer: ReturnType<typeof setInterval> | undefined

// 开始轮询
const startPolling = () => {
  if (!taskId.value) {
    return
  }

  let querying = false
  const currentTaskId = taskId.value
  pollingTimer = setInterval(async () => {
    if (querying) return
    querying = true
    try {
      const res = await getPictureOutPaintingTaskUsingGet(
        {
          taskId: currentTaskId,
        },
        { timeout: 35000 },
      )
      if (taskId.value !== currentTaskId) return
      if (res.data.code === 0 && res.data.data?.output) {
        const taskResult = res.data.data.output
        if (taskResult.taskStatus === 'SUCCEEDED' && taskResult.outputImageUrl) {
          message.success('扩图任务执行成功')
          resultImageUrl.value = taskResult.outputImageUrl
          // 清理轮询
          clearPolling()
        } else if (['FAILED', 'CANCELED', 'UNKNOWN'].includes(taskResult.taskStatus || '')) {
          message.error('扩图任务执行失败，' + (taskResult.message || taskResult.taskStatus))
          // 清理轮询
          clearPolling()
        }
      } else {
        message.error('获取扩图结果失败，' + res.data.message)
        clearPolling()
      }
    } catch (error) {
      if (taskId.value !== currentTaskId) return
      console.error('扩图任务轮询失败', error)
      message.error('扩图任务轮询失败，' + (error instanceof Error ? error.message : '请稍后重试'))
      // 清理轮询
      clearPolling()
    } finally {
      querying = false
    }
  }, 3000) // 每 3 秒轮询一次
}

// 清理轮询
const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = undefined
  }
  taskId.value = undefined
}

// 是否正在上传
const uploadLoading = ref(false)

/**
 * 上传图片
 * @param file
 */
const handleUpload = async () => {
  uploadLoading.value = true
  try {
    const params: API.PictureUploadRequest = {
      fileUrl: resultImageUrl.value,
      spaceId: props.spaceId,
    }
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      // 关闭弹窗
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + (error instanceof Error ? error.message : '请稍后重试'))
  }
  uploadLoading.value = false
}

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
  clearPolling()
}

onUnmounted(clearPolling)

// 暴露函数给父组件
defineExpose({
  openModal,
})
</script>

<style>
.image-out-painting {
  text-align: center;
}
</style>
