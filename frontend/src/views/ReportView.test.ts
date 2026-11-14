import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { ApplicationReport } from '@/api/generated'
import ReportView from './ReportView.vue'

const getApplicationReport = vi.fn()

vi.mock('@/api', () => ({
  reportsApi: {
    getApplicationReport: (...args: unknown[]) => getApplicationReport(...args),
    getResumeVacancyReport: vi.fn(),
    saveReportDecision: vi.fn(),
    revealCandidate: vi.fn(),
  },
  resumesApi: {
    getResumeText: vi.fn(() => Promise.resolve({ data: { resumeId: 'resume-1', maskedText: 'Текст', warnings: [], blind: true } })),
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
    strengths: [],
    risks: [],
    questions: [],
    gaps: [],
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/applications/:id/report', name: 'report', component: ReportView, props: true },
    ],
  })
  await router.push('/applications/application-1/report')
  await router.isReady()
  const wrapper = mount(ReportView, {
    global: {
      plugins: [router],
      stubs: {
        TextComparison: true,
        RevealPiiDialog: true,
        AppModal: true,
      },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ReportView', () => {
  beforeEach(() => {
    getApplicationReport.mockResolvedValue({ data: report() })
  })

  it('показывает стаж и легенду статусов', async () => {
    const wrapper = await mountView()

    expect(getApplicationReport).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('Как читать статусы')
    expect(wrapper.text()).toContain('«Не указано» ≠ «отсутствует»')
    expect(wrapper.text()).toContain('В резюме нет сведений. Это не означает')
    expect(wrapper.text()).toContain('3 года')
  })
})
