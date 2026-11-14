import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { CandidateMessageKind } from '@/api/generated'
import type { CandidateMessage } from '@/api/generated'
import CandidateMessagesPanel from './CandidateMessagesPanel.vue'

const getCandidateMessages = vi.fn()
const generateCandidateMessage = vi.fn()
const getResumeVacancyCandidateMessages = vi.fn()
const generateResumeVacancyCandidateMessage = vi.fn()

vi.mock('@/api', () => ({
  reportsApi: {
    getCandidateMessages: (...args: unknown[]) => getCandidateMessages(...args),
    generateCandidateMessage: (...args: unknown[]) => generateCandidateMessage(...args),
    getResumeVacancyCandidateMessages: (...args: unknown[]) =>
      getResumeVacancyCandidateMessages(...args),
    generateResumeVacancyCandidateMessage: (...args: unknown[]) =>
      generateResumeVacancyCandidateMessage(...args),
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
      kind: CandidateMessageKind.InterviewInvitation,
      hasGaps: true,
      ...overrides,
    },
  })
}

describe('CandidateMessagesPanel', () => {
  beforeEach(() => {
    getCandidateMessages.mockReset()
    generateCandidateMessage.mockReset()
    getResumeVacancyCandidateMessages.mockReset()
    generateResumeVacancyCandidateMessage.mockReset()
    getCandidateMessages.mockResolvedValue({ data: [] })
    getResumeVacancyCandidateMessages.mockResolvedValue({ data: [] })
    generateCandidateMessage.mockResolvedValue({
      data: message(
        CandidateMessageKind.InterviewInvitation,
        'Приглашение на собеседование',
        'Здравствуйте! Приглашаем на собеседование. '.repeat(5),
      ),
    })
    generateResumeVacancyCandidateMessage.mockResolvedValue({
      data: message(
        CandidateMessageKind.InterviewInvitation,
        'Приглашение на собеседование',
        'Здравствуйте! Приглашаем на собеседование. '.repeat(5),
      ),
    })
  })

  it('показывает сохранённое письмо и одну кнопку', async () => {
    getCandidateMessages.mockResolvedValue({
      data: [
        message(
          CandidateMessageKind.InterviewInvitation,
          'Тема приглашения',
          'Здравствуйте! Приглашаем на собеседование. '.repeat(5),
        ),
      ],
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.text()).toContain('Письмо')
    expect(wrapper.findAll('button')).toHaveLength(1)
    expect(wrapper.find('button').text()).toBe('Сгенерировать заново')
    expect(wrapper.find('textarea').element.value).toContain('Тема приглашения')
  })

  it('генерирует письмо одной кнопкой', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    const generate = wrapper.find('button')
    expect(generate.text()).toBe('Сгенерировать')
    await generate.trigger('click')
    await flushPromises()

    expect(generateCandidateMessage).toHaveBeenCalledWith({
      applicationId: 'application-1',
      candidateMessageRequest: { kind: CandidateMessageKind.InterviewInvitation },
      phase: 'FINAL',
    })
    expect(wrapper.find('textarea').element.value).toContain('Приглашение на собеседование')
    expect(wrapper.find('button').text()).toBe('Сгенерировать заново')
  })

  it('блокирует письмо отказа без пробелов', async () => {
    const wrapper = mountPanel({
      kind: CandidateMessageKind.Rejection,
      hasGaps: false,
    })
    await flushPromises()

    expect(wrapper.find('button').element.disabled).toBe(true)
    expect(wrapper.find('textarea').attributes('placeholder')).toContain(
      'Нет непокрытых требований',
    )
  })

  it('работает для отчёта по паре резюме и вакансия', async () => {
    const wrapper = mountPanel({
      applicationId: '',
      resumeId: 'resume-1',
      vacancyId: 'vacancy-1',
    })
    await flushPromises()

    await wrapper.find('button').trigger('click')
    await flushPromises()

    expect(getResumeVacancyCandidateMessages).toHaveBeenCalledWith({
      resumeId: 'resume-1',
      vacancyId: 'vacancy-1',
    })
    expect(generateResumeVacancyCandidateMessage).toHaveBeenCalledWith({
      resumeId: 'resume-1',
      vacancyId: 'vacancy-1',
      candidateMessageRequest: { kind: CandidateMessageKind.InterviewInvitation },
    })
  })

  it('показывает письмо, соответствующее выбранному решению', async () => {
    getCandidateMessages.mockResolvedValue({
      data: [
        message(
          CandidateMessageKind.InterviewInvitation,
          'Приглашение',
          'Здравствуйте! Приглашаем на собеседование. '.repeat(5),
        ),
        message(
          CandidateMessageKind.Rejection,
          'Отказ',
          'Здравствуйте! Спасибо за отклик. '.repeat(5),
        ),
      ],
    })
    const wrapper = mountPanel()
    await flushPromises()

    await wrapper.setProps({ kind: CandidateMessageKind.Rejection })
    await flushPromises()

    expect(wrapper.find('textarea').element.value).toContain('Отказ')
  })
})
