import axios from 'axios'
import { Configuration, ReferenceApi, VacanciesApi } from './generated'
import type { ApiStatusResponse } from './generated'

const basePath = import.meta.env.VITE_API_URL ?? '/api/v1'

// Сервер может отвечать до total-timeout (170 с), клиент ждёт чуть дольше.
axios.defaults.timeout = 180_000

const configuration = new Configuration({ basePath })

export const vacanciesApi = new VacanciesApi(configuration)
export const referenceApi = new ReferenceApi(configuration)

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
    default:
      if (axios.isAxiosError<ApiStatusResponse>(error)) {
        return error.response?.data?.message ?? 'Не удалось выполнить запрос'
      }
      return 'Не удалось выполнить запрос'
  }
}
