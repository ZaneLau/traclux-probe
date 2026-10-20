export type MetricTone = 'teal' | 'blue' | 'amber'

export interface MetricFact {
  label: string
  value: string | number
}

export interface MetricFactor {
  label: string
  count: number
  total: number
  rate: number
  weight: number
  score: number
  tone: MetricTone
}

export interface FormulaTerm {
  label: string
  value: string | number
}

export interface ProbeSignalRow {
  id: string
  question: string
  topic: string
  mentioned: boolean
  recommended: boolean
  top3: boolean
}

export interface AuxiliaryStat {
  label: string
  value: string | number
  tag: string
}

export interface TimelinePoint {
  label: string
  active: boolean
}
