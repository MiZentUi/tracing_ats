<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { FileUp, Loader2, Sparkles, X } from '@lucide/vue'
import type { ResumeBatchUploadResponse, VacancySummary } from '@/api/generated'
import { apiErrorMessage, resumesApi, vacanciesApi } from '@/api'
import { useToast } from '@/composables/useToast'

const props = defineProps<{
  /** Если задан, резюме сразу привязывается к вакансии. */
  vacancyId?: string
}>()

const emit = defineEmits<{
  uploaded: [response: ResumeBatchUploadResponse]
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
const vacancies = ref<VacancySummary[]>([])
const selectedVacancyId = ref('')

// Вакансия из карточки вакансии фиксирована; в разделе «Резюме» её можно выбрать.
const selectedVacancy = computed(() => props.vacancyId ?? (selectedVacancyId.value || undefined))

const canUpload = computed(
  () => !uploading.value && (files.value.length > 0 || pastedText.value.trim() !== ''),
)

onMounted(async () => {
  if (props.vacancyId) {
    return
  }
  try {
    const response = await vacanciesApi.listVacancies({ limit: 100, offset: 0 })
    vacancies.value = response.data.items ?? []
  } catch {
    // Список вакансий необязателен: без него резюме уйдёт в обратный подбор.
    vacancies.value = []
  }
})

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

function formatSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} Б`
  }
  return `${(bytes / 1024).toFixed(1)} КБ`
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
      ...(selectedVacancy.value ? { vacancyId: selectedVacancy.value } : {}),
    })
    success(
      selectedVacancy.value ? 'Резюме добавлено' : 'Резюме принято',
      selectedVacancy.value
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
  <div class="space-y-4">
    <label
      class="flex cursor-pointer flex-col items-center justify-center gap-3 rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center transition hover:border-indigo-400 hover:bg-indigo-50/40"
      @dragover.prevent
      @drop.prevent="dropFiles"
    >
      <FileUp :size="30" class="text-indigo-500" />
      <span class="text-sm font-semibold text-slate-800">
        Перетащите файлы сюда или нажмите, чтобы выбрать
      </span>
      <span class="text-xs text-slate-500">
        PDF, DOCX, TXT или MD · до 5 МБ · можно несколько файлов
      </span>
      <input
        ref="fileInput"
        type="file"
        multiple
        accept=".pdf,.docx,.txt,.md"
        class="hidden"
        @change="pickFiles"
      />
    </label>

    <div class="rounded-2xl border border-slate-200 bg-white p-4">
      <div class="flex items-center justify-between gap-3">
        <label for="resume-pasted-text" class="text-sm font-medium text-slate-700">
          Или вставьте текст резюме
        </label>
        <span class="text-xs text-slate-400">{{ pastedText.length }} символов</span>
      </div>
      <textarea
        id="resume-pasted-text"
        v-model="pastedText"
        rows="5"
        class="textarea mt-2 w-full resize-y text-sm"
        placeholder="Опыт, навыки, образование, проекты…"
      />
      <p class="mt-1 text-xs text-slate-400">
        Текст будет добавлен вместе с выбранными файлами по кнопке ниже.
      </p>
    </div>

    <ul v-if="files.length" class="space-y-2">
      <li
        v-for="(file, index) in files"
        :key="`${file.name}-${index}`"
        class="flex items-center gap-3 rounded-xl border border-slate-200 bg-white px-3 py-2 text-sm"
      >
        <span class="truncate text-slate-700">{{ file.name }}</span>
        <span class="ml-auto shrink-0 text-xs text-slate-400">{{ formatSize(file.size) }}</span>
        <button
          type="button"
          class="shrink-0 rounded-lg p-1 text-slate-400 transition hover:bg-rose-50 hover:text-rose-600"
          title="Убрать файл"
          @click="removeFile(index)"
        >
          <X :size="14" />
        </button>
      </li>
    </ul>

    <div v-if="!vacancyId" class="rounded-2xl border border-slate-200 bg-white p-4">
      <label for="resume-vacancy" class="text-sm font-medium text-slate-700">
        Вакансия <span class="font-normal text-slate-400">(необязательно)</span>
      </label>
      <select id="resume-vacancy" v-model="selectedVacancyId" class="input mt-2">
        <option value="">Без привязки — подобрать вакансии автоматически</option>
        <option v-for="item in vacancies" :key="item.id" :value="item.id">
          {{ item.title || item.specialization }}
        </option>
      </select>
      <p class="mt-1 text-xs text-slate-400">
        С выбранной вакансией резюме сразу попадёт в её кандидаты. Без вакансии система сама
        покажет, куда оно подходит.
      </p>
    </div>

    <p v-if="error" class="rounded-xl bg-rose-50 px-4 py-3 text-sm text-rose-700">
      {{ error }}
    </p>

    <div class="flex flex-wrap items-center gap-3">
      <button class="btn-primary" :disabled="!canUpload" @click="upload">
        <Loader2 v-if="uploading" :size="16" class="animate-spin" />
        <Sparkles v-else :size="16" />
        {{
          uploading
            ? 'Добавляем…'
            : selectedVacancy
              ? 'Добавить и подобрать'
              : 'Добавить резюме'
        }}
      </button>
      <span class="text-xs text-slate-500">
        В LLM уходит только обезличенный текст. Обработка идёт в фоне.
      </span>
    </div>
  </div>
</template>
