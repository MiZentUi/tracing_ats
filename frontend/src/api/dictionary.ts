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

export async function listSkills(
  query = '',
  skillType = '',
  status = 'ACTIVE',
): Promise<Skill[]> {
  const { data } = await axios.get<Skill[]>(`${base}/skills`, {
    params: { query, skillType, status },
  })
  return data
}

export async function addAlias(skillId: string, raw: string): Promise<void> {
  await axios.post(`${base}/skills/${skillId}/aliases`, { raw, lang: 'mixed', lemmaMode: 'AUTO' })
}

export async function createSkill(
  canonicalName: string,
  skillType = 'OTHER',
): Promise<void> {
  await axios.post(`${base}/skills`, { canonicalName, skillType })
}

export async function setCanonical(skillId: string, aliasId: string): Promise<void> {
  await axios.post(`${base}/skills/${skillId}/canonical`, { aliasId })
}

export interface GenerationAliasDraft {
  id: string
  text: string
  lang: string
  category: string | null
  conflictSkillId: string | null
  conflictSkillName: string | null
  status: string
}

export interface GenerationSkillDraft {
  id: string
  canonical: string
  skillType: string
  targetSkillId: string | null
  targetSkillName: string | null
  conflictSkillId: string | null
  conflictSkillName: string | null
  status: string
  aliases: GenerationAliasDraft[]
}

export interface GenerationJob {
  id: string
  specialization: string
  note: string | null
  status: string
  model: string | null
  promptVersion: string | null
  maxSkills: number
  error: string | null
  createdAt: string
  finishedAt: string | null
  skills: GenerationSkillDraft[]
}

export interface ApprovedSkill {
  draftId: string
  canonical: string
  skillType: string
  targetSkillId: string | null
  aliases: string[]
}

export async function createGenerationJob(input: {
  specialization: string
  note?: string
  maxSkills?: number
}): Promise<GenerationJob> {
  const { data } = await axios.post<GenerationJob>(`${base}/generation-jobs`, input)
  return data
}

export async function getGenerationJob(id: string): Promise<GenerationJob> {
  const { data } = await axios.get<GenerationJob>(`${base}/generation-jobs/${id}`)
  return data
}

export async function listGenerationJobs(): Promise<GenerationJob[]> {
  const { data } = await axios.get<GenerationJob[]>(`${base}/generation-jobs`)
  return data
}

export async function approveGeneration(
  id: string,
  skills: ApprovedSkill[],
): Promise<GenerationJob> {
  const { data } = await axios.post<GenerationJob>(`${base}/generation-jobs/${id}/approve`, {
    skills,
  })
  return data
}

export async function rejectGeneration(id: string): Promise<GenerationJob> {
  const { data } = await axios.post<GenerationJob>(`${base}/generation-jobs/${id}/reject`)
  return data
}
