import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DictionaryView from './DictionaryView.vue'

const listReview = vi.fn()
const listSkills = vi.fn()
const listGenerationJobs = vi.fn()

vi.mock('@/api/dictionary', () => ({
  addAlias: vi.fn(),
  approveGeneration: vi.fn(),
  createGenerationJob: vi.fn(),
  createSkill: vi.fn(),
  listGenerationJobs: (...args: unknown[]) => listGenerationJobs(...args),
  listReview: (...args: unknown[]) => listReview(...args),
  listSkills: (...args: unknown[]) => listSkills(...args),
  rejectGeneration: vi.fn(),
  resolveReview: vi.fn(),
  setCanonical: vi.fn(),
}))

vi.mock('@/api', () => ({
  apiErrorMessage: () => 'Ошибка',
}))

describe('DictionaryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listReview.mockResolvedValue([])
    listGenerationJobs.mockResolvedValue([])
    listSkills.mockResolvedValue([
      {
        id: 'skill-1',
        canonicalName: 'PostgreSQL',
        skillType: 'TECH',
        status: 'ACTIVE',
        aliases: [
          {
            id: 'alias-1',
            skillId: 'skill-1',
            raw: 'Postgres',
            canonical: true,
            lang: 'mixed',
            source: 'ADMIN',
            lemmaMode: 'AUTO',
            createdBy: 'hr',
            createdAt: '2026-11-12T19:11:18Z',
          },
        ],
      },
    ])
  })

  it('показывает вкладки и карточку навыка в текущем стиле', async () => {
    const wrapper = mount(DictionaryView, {
      global: { stubs: { teleport: true } },
    })
    await flushPromises()

    expect(wrapper.text()).toContain('Словарь навыков')
    expect(wrapper.text()).toContain('PostgreSQL')
    expect(wrapper.text()).toContain('Технический')
    expect(wrapper.text()).toContain('Активный')
    expect(wrapper.text()).toContain('1 алиас')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Очередь'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('Очередь пуста')
  })
})
