<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { FileUp, Loader2, Sparkles, X } from '@lucide/vue'
import type { ResumeBatchUploadResponse } from '@/api/generated'
import { apiErrorMessage, resumesApi } from '@/api'
import { useToast } from '@/composables/useToast'
import { formatBytes } from '@/lib/format'

const props = withDefaults(
  defineProps<{
    /** Если задан, резюме сразу привязывается к вакансии. */
    vacancyId?: string
    /** Компактный вариант для боковой панели. */
    compact?: boolean
  }>(),
  { compact: false },
)

const emit = defineEmits<{
  uploaded: [response: ResumeBatchUploadResponse]
  selection: [payload: { files: File[]; textLength: number }]
}>()

const { success, error: toastError } = useToast()

const ALLOWED_EXTENSIONS = ['pdf', 'docx', 'txt', 'md']
const MAX_FILE_SIZE = 5 * 1024 * 1024

const files = ref<File[]>([])
const pastedText = ref('')
const pastedCounter = ref(0)
const uploading = ref(false)
const error = ref('')
const fileInput = ref<HTMLInputElement | null>(null)

const canUpload = computed(
  () => !uploading.value && (files.value.length > 0 || pastedText.value.trim() !== ''),
)

const actionLabel = computed(() => {
  if (uploading.value) {
    return 'Добавляем…'
  }
  return props.vacancyId ? 'Добавить и подобрать' : 'Добавить резюме'
})

watch(
  [files, pastedText],
  () => {
    emit('selection', {
      files: [...files.value],
      textLength: pastedText.value.trim().length,
    })
  },
  { deep: true },
)

function appendFiles(incoming: File[]) {
  const accepted: File[] = []
  for (const file of incoming) {
    const extension = file.name.split('.').pop()?.toLowerCase() ?? ''
    if (!ALLOWED_EXTENSIONS.includes(extension)) {
      toastError('Неподдерживаемый формат', `«${file.name}»: нужен PDF, DOCX, TXT или MD`)
      continue
    }
    if (file.size > MAX_FILE_SIZE) {
      toastError('Файл слишком большой', `«${file.name}»: максимум 5 МБ`)
      continue
    }
    accepted.push(file)
  }
  files.value = [...files.value, ...accepted]
}

function pickFiles(event: Event) {
  const input = event.target as HTMLInputElement
  appendFiles(Array.from(input.files ?? []))
  input.value = ''
}

function dropFiles(event: DragEvent) {
  appendFiles(Array.from(event.dataTransfer?.files ?? []))
}

function removeFile(index: number) {
  files.value = files.value.filter((_, current) => current !== index)
}

function buildPayloadFiles(): File[] {
  const result = [...files.value]
  const text = pastedText.value.trim()
  if (text) {
    pastedCounter.value += 1
    result.push(
      new File([text], `pasted-resume-${pastedCounter.value}.txt`, { type: 'text/plain' }),
    )
  }
  return result
}

async function upload() {
  if (!canUpload.value) {
    return
  }
  uploading.value = true
  error.value = ''
  try {
    const response = await resumesApi.uploadResumes({
      files: buildPayloadFiles(),
      consentGithub: false,
      ...(props.vacancyId ? { vacancyId: props.vacancyId } : {}),
    })
    success(
      props.vacancyId ? 'Резюме добавлено' : 'Резюме принято',
      props.vacancyId
        ? 'Обработка идёт в фоне, резюме появится в кандидатах вакансии'
        : 'Обработка идёт в фоне, подходящие вакансии появятся в списке',
    )
    files.value = []
    pastedText.value = ''
    if (fileInput.value) {
      fileInput.value.value = ''
    }
    emit('uploaded', response.data)
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    toastError('Не удалось добавить резюме', message)
  } finally {
    uploading.value = false
  }
}
</script>

<template>
  <div class="space-y-3">
    <div v-if="compact" class="grid gap-3">
      <div class="rounded-lg border border-slate-200 bg-white p-3">
        <div class="flex items-center justify-between gap-3">
          <label for="resume-pasted-text" class="text-sm font-medium text-slate-700">
            Вставить текст
          </label>
          <span class="text-[11px] text-slate-400">{{ pastedText.length }} символов</span>
        </div>
        <textarea
          id="resume-pasted-text"
          v-model="pastedText"
          rows="4"
          class="textarea mt-2 min-h-24 w-full resize-y text-sm"
          placeholder="Опыт, навыки, образование…"
        />
      </div>

      <label
        class="flex cursor-pointer flex-col items-center justify-center gap-1 rounded-lg border border-dashed border-slate-300 bg-slate-50 px-3 py-4 text-center transition hover:border-indigo-300 hover:bg-indigo-50/40"
        @dragover.prevent
        @drop.prevent="dropFiles"
      >
        <FileUp :size="20" class="text-indigo-500" />
        <span class="text-xs font-medium text-slate-700">Загрузить файлы</span>
        <span class="text-[11px] text-slate-400">PDF, DOCX, TXT, MD · до 5 МБ</span>
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".pdf,.docx,.txt,.md"
          class="hidden"
          @change="pickFiles"
        />
      </label>
    </div>

    <div v-else class="grid gap-4 lg:grid-cols-[minmax(0,1fr)_18rem]">
      <div class="rounded-xl border border-slate-200 bg-white p-4">
        <div class="flex items-center justify-between gap-3">
          <label for="resume-pasted-text" class="text-sm font-medium text-slate-700">
            Текст резюме
          </label>
          <span class="text-xs text-slate-400">{{ pastedText.length }} символов</span>
        </div>
        <textarea
          id="resume-pasted-text"
          v-model="pastedText"
          rows="8"
          class="textarea mt-2 min-h-48 w-full resize-y text-sm"
          placeholder="Опыт, навыки, образование, проекты…"
        />
      </div>

      <label
        class="flex cursor-pointer flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-6 text-center transition hover:border-indigo-300 hover:bg-indigo-50/40"
        @dragover.prevent
        @drop.prevent="dropFiles"
      >
        <FileUp :size="24" class="text-indigo-500" />
        <span class="text-sm font-medium text-slate-800">Загрузить файл</span>
        <span class="text-xs text-slate-500">PDF, DOCX, TXT или MD<br />до 5 МБ, можно несколько</span>
        <input
          ref="fileInput"
          type="file"
          multiple
          accept=".pdf,.docx,.txt,.md"
          class="hidden"
          @change="pickFiles"
        />
      </label>
    </div>

    <ul v-if="files.length" class="space-y-1.5">
      <li
        v-for="(file, index) in files"
        :key="`${file.name}-${index}`"
        class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-1.5 text-xs"
      >
        <span class="truncate text-slate-700">{{ file.name }}</span>
        <span class="ml-auto shrink-0 text-[11px] text-slate-400">{{ formatBytes(file.size) }}</span>
        <button
          type="button"
          class="shrink-0 rounded-md p-1 text-slate-400 transition hover:bg-rose-50 hover:text-rose-600"
          title="Убрать файл"
          @click="removeFile(index)"
        >
          <X :size="13" />
        </button>
      </li>
    </ul>

    <p v-if="error" class="rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">
      {{ error }}
    </p>

    <div class="flex flex-wrap items-center gap-3">
      <button class="btn-primary" :disabled="!canUpload" @click="upload">
        <Loader2 v-if="uploading" :size="16" class="animate-spin" />
        <Sparkles v-else :size="16" />
        {{ actionLabel }}
      </button>
      <span class="text-xs text-slate-500">
        В LLM уходит только обезличенный текст. Обработка идёт в фоне.
      </span>
    </div>
  </div>
</template>
