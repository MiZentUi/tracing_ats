import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CandidateMessageKind } from '@/api/generated'
import type { CandidateMessage } from '@/api/generated'
import CandidateMessagesPanel from './CandidateMessagesPanel.vue'

const getCandidateMessages = vi.fn()
const generateCandidateMessage = vi.fn()

vi.mock('@/api', () => ({
  reportsApi: {
    getCandidateMessages: (...args: unknown[]) => getCandidateMessages(...args),
    generateCandidateMessage: (...args: unknown[]) => generateCandidateMessage(...args),
  },
  apiErrorMessage: () => 'Ошибка',
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}))

function message(
  kind: CandidateMessageKind,
  subject: string,
  body: string,
): CandidateMessage {
  return {
    id: `${kind}-id`,
    applicationId: 'application-1',
    phase: 'FINAL',
    kind,
    subject,
    body,
    promptVersion: 'test',
    model: 'mock',
    createdAt: '2026-11-14T00:00:00Z',
  }
}

function mountPanel(overrides: Record<string, unknown> = {}) {
  return mount(CandidateMessagesPanel, {
    props: {
      applicationId: 'application-1',
      phase: 'FINAL',
      hasGaps: true,
      ...overrides,
    },
  })
}

describe('CandidateMessagesPanel', () => {
  beforeEach(() => {
    getCandidateMessages.mockReset()
    generateCandidateMessage.mockReset()
    getCandidateMessages.mockResolvedValue({ data: [] })
    generateCandidateMessage.mockResolvedValue({
      data: message(
        CandidateMessageKind.Rejection,
        'Отклик на вакансию',
        'Здравствуйте! Спасибо за отклик. '.repeat(5),
      ),
    })
  })

  it('показывает сохранённый черновик и фазу', async () => {
    getCandidateMessages.mockResolvedValue({
      data: [
        message(
          CandidateMessageKind.Rejection,
          'Тема отказа',
          'Здравствуйте! Спасибо за отклик. '.repeat(5),
        ),
      ],
    })
    const wrapper = mountPanel({ preferredKind: CandidateMessageKind.Rejection })
    await flushPromises()

    expect(wrapper.text()).toContain('Письмо')
    expect(wrapper.text()).toContain('Итоговый расчёт')
    expect(wrapper.text()).toContain('Тема отказа')
    expect(wrapper.text()).toContain('Сгенерировать заново')
  })

  it('генерирует приглашение одной кнопкой', async () => {
    generateCandidateMessage.mockResolvedValue({
      data: message(
        CandidateMessageKind.InterviewInvitation,
        'Приглашение на собеседование',
        'Здравствуйте! Приглашаем на собеседование. '.repeat(5),
      ),
    })
    const wrapper = mountPanel()
    await flushPromises()

    const generate = wrapper.findAll('button').find((button) => button.text() === 'Сгенерировать')
    expect(generate).toBeDefined()
    await generate?.trigger('click')
    await flushPromises()

    expect(generateCandidateMessage).toHaveBeenCalledWith({
      applicationId: 'application-1',
      candidateMessageRequest: { kind: CandidateMessageKind.InterviewInvitation },
      phase: 'FINAL',
    })
    expect(wrapper.text()).toContain('Сгенерировать заново')
  })

  it('не предлагает письмо отказа без пробелов', async () => {
    const wrapper = mountPanel({
      hasGaps: false,
      preferredKind: CandidateMessageKind.Rejection,
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Нет непокрытых требований')
    expect(
      wrapper.findAll('button').find((button) => button.text() === 'Сгенерировать'),
    ).toBeUndefined()
  })

  it('показывает предварительную фазу', async () => {
    const wrapper = mountPanel({ phase: 'PRELIMINARY' })
    await flushPromises()

    expect(wrapper.text()).toContain('Предварительный расчёт')
  })
})
