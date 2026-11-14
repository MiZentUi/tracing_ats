import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApplicationReport } from '@/api/generated'
import ReportView from './ReportView.vue'

const getApplicationReport = vi.fn()
const getResumeVacancyReport = vi.fn()
const getResumeVacancyCandidateMessages = vi.fn()

vi.mock('@/api', () => ({
  reportsApi: {
    getApplicationReport: (...args: unknown[]) => getApplicationReport(...args),
    getResumeVacancyReport: (...args: unknown[]) => getResumeVacancyReport(...args),
    saveReportDecision: vi.fn(),
    revealCandidate: vi.fn(),
    getCandidateMessages: vi.fn(() => Promise.resolve({ data: [] })),
    generateCandidateMessage: vi.fn(),
    getResumeVacancyCandidateMessages: (...args: unknown[]) =>
      getResumeVacancyCandidateMessages(...args),
    generateResumeVacancyCandidateMessage: vi.fn(),
  },
  resumesApi: {
    getResumeText: vi.fn(() =>
      Promise.resolve({
        data: { resumeId: 'resume-1', maskedText: 'Текст', warnings: [], blind: true },
      }),
    ),
    revealResumeText: vi.fn(),
  },
  vacanciesApi: {
    getVacancy: vi.fn(() =>
      Promise.resolve({ data: { vacancy_id: 'vacancy-1', source_text: 'Вакансия' } }),
    ),
  },
  apiErrorMessage: () => 'Ошибка',
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}))

function report(): ApplicationReport {
  return {
    applicationId: 'application-1',
    resumeId: 'resume-1',
    vacancyId: 'vacancy-1',
    phase: 'FINAL',
    score: 69,
    scoreLow: 62,
    scoreHigh: 82,
    capApplied: false,
    requirements: [
      {
        requirementId: 'req-1',
        text: 'Python',
        kind: 'MUST',
        critical: true,
        relevance: 0.8,
        cR: 0.7,
        tenure: 3,
        status: 'NOT_STATED',
        gap: 'В резюме нет сведений',
      },
    ],
    externalEvidence: [],
    flags: [],
    blind: true,
    recruiterNote: 'Не показывать на странице',
    strengths: ['Не показывать на странице'],
    risks: [],
    questions: [],
    gaps: [{ requirementId: 'req-1', text: 'Python', reason: 'NOT_STATED' }],
  }
}

async function mountView(path = '/applications/application-1/report') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      {
        path: '/applications/:id/report',
        name: 'application-report',
        component: ReportView,
        props: true,
      },
      {
        path: '/resumes/:resumeId/vacancies/:vacancyId/report',
        name: 'resume-vacancy-report',
        component: ReportView,
        props: true,
      },
    ],
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(ReportView, {
    global: {
      plugins: [router],
      stubs: {
        TextComparison: true,
        RevealPiiDialog: true,
        AppModal: {
          props: ['open'],
          template: '<div v-if="open"><slot /></div>',
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ReportView', () => {
  beforeEach(() => {
    getApplicationReport.mockResolvedValue({ data: report() })
    getResumeVacancyReport.mockResolvedValue({
      data: { ...report(), applicationId: undefined, phase: 'PRELIMINARY' },
    })
    getResumeVacancyCandidateMessages.mockResolvedValue({ data: [] })
  })

  it('показывает стаж и легенду статусов без удалённых блоков', async () => {
    const wrapper = await mountView()

    expect(getApplicationReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Как читать статусы')
    expect(wrapper.text()).toContain('«Не указано» ≠ «отсутствует»')
    expect(wrapper.text()).toContain('В резюме нет сведений. Это не означает')
    expect(wrapper.text()).toContain('3 года')
    expect(wrapper.text()).not.toContain('Обратная связь по вакансии')
    expect(wrapper.text()).not.toContain('Подтверждения из резюме')
    expect(wrapper.text()).not.toContain('Пробелы')
  })

  it('показывает блок «Письмо» только в окне решения', async () => {
    const wrapper = await mountView()
    expect(wrapper.text()).not.toContain('Письмо')

    const openDecision = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Выбрать решение'))
    expect(openDecision).toBeDefined()
    await openDecision?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Письмо')
    expect(wrapper.text()).toContain('Сгенерировать')
    expect(wrapper.find('#report-comment').attributes('rows')).toBe('2')
  })

  it('показывает письмо в окне решения для пары резюме и вакансия', async () => {
    const wrapper = await mountView('/resumes/resume-1/vacancies/vacancy-1/report')
    const openDecision = wrapper
      .findAll('button')
      .find((button) => button.text().includes('Выбрать решение'))
    expect(openDecision).toBeDefined()
    await openDecision?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Письмо')
    expect(getResumeVacancyCandidateMessages).toHaveBeenCalledWith({
      resumeId: 'resume-1',
      vacancyId: 'vacancy-1',
    })
  })
})
