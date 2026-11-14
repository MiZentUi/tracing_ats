import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  ResumeExtraction,
  ResumeSummary,
  ResumeText,
  ResumeVacancyRankingResponse,
} from '@/api/generated'
import ResumeDetailView from './ResumeDetailView.vue'

const getResume = vi.fn()
const getResumeText = vi.fn()
const getResumeExtraction = vi.fn()
const getResumeVacancyRanking = vi.fn()

vi.mock('@/api', () => ({
  resumesApi: {
    getResume: (...args: unknown[]) => getResume(...args),
    getResumeText: (...args: unknown[]) => getResumeText(...args),
    getResumeExtraction: (...args: unknown[]) => getResumeExtraction(...args),
    updateResume: vi.fn(),
    revealResumeText: vi.fn(),
  },
  resumeRankingApi: {
    getResumeVacancyRanking: (...args: unknown[]) => getResumeVacancyRanking(...args),
    matchResume: vi.fn(),
  },
  apiErrorMessage: () => 'Ошибка',
}))

vi.mock('@/composables/useToast', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn() }),
}))

function resume(): ResumeSummary {
  return {
    resumeId: 'resume-1',
    title: 'Backend-разработчик',
    status: 'MATCHED',
    fileType: 'TEXT',
    sizeBytes: 1024,
    createdAt: '2026-11-14T10:00:00Z',
    warnings: [],
  }
}

function resumeText(): ResumeText {
  return {
    resumeId: 'resume-1',
    maskedText: 'Backend-разработчик',
    warnings: [],
    blind: true,
  }
}

function extraction(): ResumeExtraction {
  return {
    resumeId: 'resume-1',
    skills: [],
    qualifications: [],
    education: [],
    experience: [
      {
        company: 'ООО Ромашка',
        position: 'Backend-разработчик',
        category: 'backend',
        beginYear: 2020,
        endYear: 2023,
        evidenceQuote: 'ООО Ромашка, 2020–2023',
        metrics: ['Ускорил API в 2 раза'],
      },
    ],
  }
}

function ranking(): ResumeVacancyRankingResponse {
  return {
    resumeId: 'resume-1',
    phase: 'FINAL',
    generatedAt: '2026-11-14T10:00:00Z',
    items: [],
  }
}

async function mountView() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/resumes', component: { template: '<div />' } },
      {
        path: '/resumes/:id',
        name: 'resume',
        component: ResumeDetailView,
        props: true,
      },
    ],
  })
  await router.push('/resumes/resume-1')
  await router.isReady()
  const wrapper = mount(ResumeDetailView, {
    props: { id: 'resume-1' },
    global: {
      plugins: [router],
      stubs: { RevealPiiDialog: true },
    },
  })
  await flushPromises()
  return wrapper
}

describe('ResumeDetailView', () => {
  beforeEach(() => {
    getResume.mockResolvedValue({ data: resume() })
    getResumeText.mockResolvedValue({ data: resumeText() })
    getResumeExtraction.mockResolvedValue({ data: extraction() })
    getResumeVacancyRanking.mockResolvedValue({ data: ranking() })
  })

  it('показывает извлечённый опыт работы', async () => {
    const wrapper = await mountView()

    expect(wrapper.text()).toContain('Опыт работы')
    expect(wrapper.text()).toContain('Backend-разработчик')
    expect(wrapper.text()).toContain('ООО Ромашка')
    expect(wrapper.text()).toContain('2020 — 2023')
    expect(wrapper.text()).toContain('ООО Ромашка, 2020–2023')
    expect(wrapper.text()).toContain('Ускорил API в 2 раза')
  })
})
