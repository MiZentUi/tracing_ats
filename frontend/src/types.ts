import type { KindSource, NormalizationStatus, Requirement, SkillKind, SkillKindHistory } from '@/api/generated'

export interface EditableRequirement {
  key: string
  id: string | null
  skill: string | null
  skillId: string | null
  normalizationStatus: NormalizationStatus
  kind: SkillKind
  kindSource: KindSource
  llmKind: SkillKind | null
  yearsRequired: number | null
  quote: string | null
  clarityWarning: string | null
  history: SkillKindHistory | null
  qualification: boolean
}

export function toEditable(requirement: Requirement, index: number): EditableRequirement {
  return {
    key: requirement.id ?? `new-${index}`,
    id: requirement.id,
    skill: requirement.skill ?? null,
    skillId: requirement.skill_id ?? null,
    normalizationStatus: requirement.normalization_status,
    kind: requirement.kind,
    kindSource: requirement.kind_source,
    llmKind: requirement.llm_kind ?? null,
    yearsRequired: requirement.years_required ?? null,
    quote: requirement.quote ?? null,
    clarityWarning: requirement.clarity_warning ?? null,
    history: requirement.history ?? null,
    qualification: requirement.qualification ?? false,
  }
}

export const NO_LEVEL = 'none'

export function levelLabel(code: string | null | undefined): string {
  return !code || code === NO_LEVEL ? 'Без уровня' : code
}

export const kindSections: Array<{ kind: SkillKind; title: string; hint: string }> = [
  { kind: 'MUST', title: 'Обязательные', hint: 'Пока нет обязательных требований' },
  { kind: 'NICE', title: 'Желательные', hint: 'Пока нет желательных требований' },
  { kind: 'OPTIONAL', title: 'Научим / не требуется', hint: 'Пока нет таких требований' },
]
