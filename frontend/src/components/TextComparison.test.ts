import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { RequirementReportItem } from '@/api/generated'
import TextComparison from './TextComparison.vue'

function requirement(status: RequirementReportItem['status']): RequirementReportItem {
  return {
    requirementId: 'req-1',
    text: 'Java',
    kind: 'MUST',
    critical: false,
    cR: 0.5,
    status,
    evidenceQuote: 'Java',
  }
}

function mountComparison(status: RequirementReportItem['status']) {
  return mount(TextComparison, {
    props: {
      vacancyText: 'Java',
      resumeText: 'Java',
      requirements: [requirement(status)],
    },
  })
}

describe('TextComparison', () => {
  it('красит подтверждённый навык как статус COVERED', () => {
    const wrapper = mountComparison('COVERED')
    const mark = wrapper.find('mark')

    expect(mark.exists()).toBe(true)
    expect(mark.classes()).toContain('bg-emerald-100')
  })

  it('красит частичный навык как статус PARTIAL', () => {
    const wrapper = mountComparison('PARTIAL')
    const mark = wrapper.find('mark')

    expect(mark.exists()).toBe(true)
    expect(mark.classes()).toContain('bg-amber-100')
  })

  it('красит пробел как статус GAP', () => {
    const wrapper = mountComparison('GAP')
    const mark = wrapper.find('mark')

    expect(mark.exists()).toBe(true)
    expect(mark.classes()).toContain('bg-rose-100')
  })
})
