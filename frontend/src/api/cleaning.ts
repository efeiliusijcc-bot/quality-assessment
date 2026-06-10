import { request } from '@/utils/request';

export interface CleaningRule {
  ruleId: string;
  ruleCode: string;
  ruleName: string;
  targetCategory: string | null;
  conditionExpr: string;
  actionExpr: string;
  priorityNo: number | null;
  enabledFlag: boolean;
}

export interface CreateCleaningRulePayload {
  ruleCode: string;
  ruleName: string;
  targetCategory: string;
  conditionExpr: string;
  actionExpr: string;
  priorityNo: number;
}

export interface CleaningLog {
  cleaningLogId: string;
  ruleId: string | null;
  sourceTable: string | null;
  sourceId: string | null;
  beforeValue: string | null;
  afterValue: string | null;
  actionResult: string | null;
  createdAt: string | null;
}

export interface CleaningLogQuery {
  ruleId?: string;
  sourceTable?: string;
  sourceId?: string;
}

export const fetchCleaningRules = async (targetCategory?: string): Promise<CleaningRule[]> => {
  return request<CleaningRule[]>({
    url: '/etl/cleaning-rules',
    method: 'GET',
    params: targetCategory ? { targetCategory } : undefined,
  });
};

export const createCleaningRule = async (payload: CreateCleaningRulePayload): Promise<CleaningRule> => {
  return request<CleaningRule>({
    url: '/etl/cleaning-rules',
    method: 'POST',
    data: payload,
    showLoading: false,
  });
};

export const fetchCleaningLogs = async (params?: CleaningLogQuery): Promise<CleaningLog[]> => {
  return request<CleaningLog[]>({
    url: '/etl/cleaning-logs',
    method: 'GET',
    params,
  });
};
