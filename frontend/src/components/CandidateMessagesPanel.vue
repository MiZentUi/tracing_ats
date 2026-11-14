<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { CandidateMessageKind } from '@/api/generated'
import type { CandidateMessage } from '@/api/generated'
import { apiErrorMessage, reportsApi } from '@/api'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  applicationId?: string
  resumeId?: string
  vacancyId?: string
  phase: 'PRELIMINARY' | 'FINAL'
  kind: CandidateMessageKind
  hasGaps: boolean
}>()

const { error: toastError } = useToast()

const messages = ref<Partial<Record<CandidateMessageKind, CandidateMessage>>>({})
const loading = ref(false)
const generating = ref(false)
const error = ref('')

const activeMessage = computed(() => messages.value[props.kind] ?? null)
const isRejection = computed(() => props.kind === CandidateMessageKind.Rejection)
const blocked = computed(() => isRejection.value && !props.hasGaps)
const text = computed(() => {
  const message = activeMessage.value
  return message ? `${message.subject}\n\n${message.body}` : ''
})
const buttonLabel = computed(() => {
  if (generating.value) {
    return 'Генерируем…'
  }
  return activeMessage.value ? 'Сгенерировать заново' : 'Сгенерировать'
})

async function loadMessages() {
  if (!props.applicationId && (!props.resumeId || !props.vacancyId)) {
    return
  }
  loading.value = true
  error.value = ''
  try {
    const response = props.applicationId
      ? await reportsApi.getCandidateMessages({
          applicationId: props.applicationId,
          phase: props.phase,
        })
      : await reportsApi.getResumeVacancyCandidateMessages({
          resumeId: props.resumeId as string,
          vacancyId: props.vacancyId as string,
        })
    messages.value = Object.fromEntries(
      response.data.map((message) => [message.kind, message]),
    ) as Partial<Record<CandidateMessageKind, CandidateMessage>>
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    loading.value = false
  }
}

async function generate() {
  if (generating.value || blocked.value) {
    return
  }
  if (!props.applicationId && (!props.resumeId || !props.vacancyId)) {
    return
  }
  generating.value = true
  error.value = ''
  try {
    const response = props.applicationId
      ? await reportsApi.generateCandidateMessage({
          applicationId: props.applicationId,
          candidateMessageRequest: { kind: props.kind },
          phase: props.phase,
        })
      : await reportsApi.generateResumeVacancyCandidateMessage({
          resumeId: props.resumeId as string,
          vacancyId: props.vacancyId as string,
          candidateMessageRequest: { kind: props.kind },
        })
    messages.value = {
      ...messages.value,
      [response.data.kind]: response.data,
    }
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось сгенерировать письмо', message)
  } finally {
    generating.value = false
  }
}

watch(
  () => [props.applicationId, props.resumeId, props.vacancyId, props.phase],
  () => {
    messages.value = {}
    void loadMessages()
  },
)

onMounted(loadMessages)
</script>

<template>
  <div>
    <div class="flex items-center justify-between gap-2">
      <label class="label" for="candidate-message">Письмо</label>
      <button
        class="btn-primary px-3 py-1.5 text-xs"
        type="button"
        :disabled="generating || blocked"
        @click="generate"
      >
        {{ buttonLabel }}
      </button>
    </div>
    <textarea
      id="candidate-message"
      class="textarea mt-1 min-h-64 resize-y"
      readonly
      :value="text"
      :placeholder="
        blocked
          ? 'Нет непокрытых требований для письма отказа'
          : loading
            ? 'Загрузка…'
            : 'Нажмите «Сгенерировать», чтобы подготовить черновик'
      "
    />
    <p v-if="error" class="mt-2 text-sm text-rose-700">{{ error }}</p>
  </div>
</template>
