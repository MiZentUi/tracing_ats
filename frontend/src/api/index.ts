import axios from 'axios'
import { logger } from '@/lib/logger'
import {
  Configuration,
  HHApi,
  RankingApi,
  ReferenceApi,
  ReportsApi,
  ResumesApi,
  ResumeRankingApi,
  ScreeningApi,
  VacanciesApi,
} from './generated'
import type { ApiStatusResponse } from './generated'

const basePath = import.meta.env.VITE_API_URL ?? '/api/v1'

// Сервер может отвечать до total-timeout (300 с), а nginx ждёт 300 с: клиент ждёт дольше обоих.
axios.defaults.timeout = 310_000

// Ошибки API логируем без тел запросов и PII: только метод, статус и request_id.
axios.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    if (axios.isAxiosError(error)) {
      logger.warn(
        'api_request_failed',
        `method=${error.config?.method?.toUpperCase() ?? '-'}`,
        `status=${error.response?.status ?? 'network'}`,
        `request_id=${(error.response?.headers?.['x-request-id'] as string | undefined) ?? '-'}`,
      )
    }
    return Promise.reject(error)
  },
)

const configuration = new Configuration({ basePath })

export const vacanciesApi = new VacanciesApi(configuration)
export const referenceApi = new ReferenceApi(configuration)
export const resumesApi = new ResumesApi(configuration)
export const resumeRankingApi = new ResumeRankingApi(configuration)
export const screeningApi = new ScreeningApi(configuration)
export const rankingApi = new RankingApi(configuration)
export const reportsApi = new ReportsApi(configuration)
export const hhApi = new HHApi(configuration)

export function apiErrorCode(error: unknown): string | null {
  if (axios.isAxiosError<ApiStatusResponse>(error) && error.response?.data?.status) {
    return error.response.data.status
  }
  return null
}

export function apiErrorMessage(error: unknown): string {
  const code = apiErrorCode(error)
  switch (code) {
    case 'LLM_NOT_CONFIGURED':
      return 'Ключ LLM не настроен'
    case 'LLM_UNAVAILABLE':
      return 'Сервис разбора временно недоступен, попробуйте позже'
    case 'LLM_INVALID_RESPONSE':
      return 'Модель вернула некорректный ответ, попробуйте ещё раз'
    case 'VERSION_CONFLICT':
      return 'Шаблон изменён в другом месте. Перезагрузите данные'
    case 'DUPLICATE_SKILL':
      return 'Один навык указан несколько раз. Исправьте дубликаты'
    case 'NO_ACTIVE_SCORING_WEIGHTS':
      return 'Нет активного профиля весов'
    case 'SPECIALIZATION_EXISTS':
      return 'Такая специализация уже есть'
    case 'INVALID_SPECIALIZATION_CODE':
      return 'Код специализации: только латиница, цифры и дефисы'
    default:
      if (axios.isAxiosError<ApiStatusResponse>(error)) {
        return error.response?.data?.message ?? 'Не удалось выполнить запрос'
      }
      return 'Не удалось выполнить запрос'
  }
}
