import axios from 'axios'

export interface Candidate {
  skillId: string
  canonicalName: string
  confidence: number
}

export interface ReviewItem {
  id: string
  key: string
  samples: string[]
  occurrences: number
  status: string
  suggestedSkillId: string | null
  suggestedConfidence: number | null
  candidates: Candidate[]
  resolvedSkillId: string | null
  resolvedBy: string | null
  resolvedAt: string | null
}

export interface Alias {
  id: string
  skillId: string
  raw: string
  canonical: boolean
  lang: string
  source: string
  lemmaMode: string
  createdBy: string
  createdAt: string
}

export interface Skill {
  id: string
  canonicalName: string
  skillType: string
  status: string
  aliases: Alias[]
}

const base = '/api/v1/admin/dictionary'

export async function listReview(status = 'PENDING'): Promise<ReviewItem[]> {
  const { data } = await axios.get<ReviewItem[]>(`${base}/review-items`, { params: { status } })
  return data
}

export async function resolveReview(
  id: string,
  action: 'ALIAS' | 'NEW_SKILL' | 'REJECT',
  skillId?: string,
  canonicalName?: string,
): Promise<void> {
  await axios.post(`${base}/review-items/${id}/resolve`, { action, skillId, canonicalName })
}

export async function listSkills(query = ''): Promise<Skill[]> {
  const { data } = await axios.get<Skill[]>(`${base}/skills`, { params: { query } })
  return data
}

export async function addAlias(skillId: string, raw: string): Promise<void> {
  await axios.post(`${base}/skills/${skillId}/aliases`, { raw, lang: 'mixed', lemmaMode: 'AUTO' })
}

export async function createSkill(canonicalName: string): Promise<void> {
  await axios.post(`${base}/skills`, { canonicalName, skillType: 'OTHER' })
}
