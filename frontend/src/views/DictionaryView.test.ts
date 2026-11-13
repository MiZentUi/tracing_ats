import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DictionaryView from './DictionaryView.vue'

const listReview = vi.fn()
const listSkills = vi.fn()
const listGenerationJobs = vi.fn()
const listQualifications = vi.fn()
const listRelations = vi.fn()

vi.mock('@/api/dictionary', () => ({
  addAlias: vi.fn(),
  addQualificationAlias: vi.fn(),
  approveGeneration: vi.fn(),
  createGenerationJob: vi.fn(),
  createQualification: vi.fn(),
  createRelation: vi.fn(),
  deleteQualification: vi.fn(),
  deleteQualificationAlias: vi.fn(),
  deleteRelation: vi.fn(),
  createSkill: vi.fn(),
  listGenerationJobs: (...args: unknown[]) => listGenerationJobs(...args),
  listQualificationReview: vi.fn().mockResolvedValue([]),
  listQualifications: (...args: unknown[]) => listQualifications(...args),
  listRelations: (...args: unknown[]) => listRelations(...args),
  listReview: (...args: unknown[]) => listReview(...args),
  listSkills: (...args: unknown[]) => listSkills(...args),
  rejectGeneration: vi.fn(),
  resolveQualificationReview: vi.fn(),
  resolveReview: vi.fn(),
  setCanonical: vi.fn(),
  setCanonicalQualificationAlias: vi.fn(),
}))

vi.mock('@/api', () => ({
  apiErrorMessage: () => 'Ошибка',
}))

describe('DictionaryView', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    listReview.mockResolvedValue([])
    listGenerationJobs.mockResolvedValue([])
    listQualifications.mockResolvedValue([])
    listRelations.mockResolvedValue([])
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

    expect(wrapper.text()).toContain('Словарь')
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

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Квалификации'))
      ?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Квалификации не найдены')

    await wrapper
      .findAll('button')
      .find((button) => button.text().includes('Связи'))
      ?.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Связей пока нет')
  })
})
