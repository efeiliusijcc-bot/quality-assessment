<template>
  <div class="space-y-6">
    <PageIntroCard
      title="数据清洗规则"
      description="维护导入前的数据修正规则，并查看每次清洗执行留下的审计记录。"
      badge="DATA CLEANING"
      :metrics="metrics"
    />

    <section class="grid gap-6 xl:grid-cols-[420px_1fr]">
      <div class="content-card p-6">
        <div class="mb-5 flex items-center justify-between">
          <div>
            <h2 class="panel-title">新增规则</h2>
            <p class="panel-subtitle">按目标表配置条件表达式和动作表达式。</p>
          </div>
          <el-tag effect="plain">DSL</el-tag>
        </div>

        <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
          <el-form-item label="规则编码" prop="ruleCode">
            <el-input v-model.trim="form.ruleCode" placeholder="PV_NEGATIVE" maxlength="128" />
          </el-form-item>

          <el-form-item label="规则名称" prop="ruleName">
            <el-input v-model.trim="form.ruleName" placeholder="负数参数标记异常" maxlength="128" />
          </el-form-item>

          <el-form-item label="目标数据" prop="targetCategory">
            <el-select v-model="form.targetCategory" class="w-full">
              <el-option label="工艺参数值" value="parameter_value" />
              <el-option label="质量测量值" value="quality_measurement" />
              <el-option label="全部支持表" value="all" />
            </el-select>
          </el-form-item>

          <el-form-item label="条件表达式" prop="conditionExpr">
            <el-input v-model.trim="form.conditionExpr" placeholder="valueNum < 0" />
          </el-form-item>

          <el-form-item label="动作表达式" prop="actionExpr">
            <el-input
              v-model.trim="form.actionExpr"
              type="textarea"
              :rows="3"
              placeholder="qualityFlag=ANOMALY"
            />
          </el-form-item>

          <el-form-item label="优先级" prop="priorityNo">
            <el-input-number v-model="form.priorityNo" :min="1" :max="9999" class="w-full" controls-position="right" />
          </el-form-item>

          <div class="rounded-md bg-slate-50 p-3 text-sm text-slate-600">
            <div class="font-medium text-slate-800">示例</div>
            <div class="mt-1 font-mono">valueNum &lt; 0 -> qualityFlag=ANOMALY</div>
            <div class="mt-1 font-mono">v &gt; 100 -> isPass=false;deviationValue=999</div>
          </div>

          <div class="mt-5 flex justify-end gap-3">
            <el-button @click="resetForm">重置</el-button>
            <el-button type="primary" :loading="saving" @click="submitRule">
              <el-icon><Plus /></el-icon>
              创建规则
            </el-button>
          </div>
        </el-form>
      </div>

      <div class="content-card p-6">
        <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 class="panel-title">规则列表</h2>
            <p class="panel-subtitle">启用规则会在 Excel 导入时按优先级依次执行。</p>
          </div>
          <el-button :loading="loadingRules" @click="loadRules">
            <el-icon><Refresh /></el-icon>
            刷新
          </el-button>
        </div>

        <el-table v-loading="loadingRules" :data="ruleList" stripe style="width: 100%">
          <el-table-column prop="ruleCode" label="编码" min-width="150" show-overflow-tooltip />
          <el-table-column prop="ruleName" label="名称" min-width="170" show-overflow-tooltip />
          <el-table-column label="目标" width="150">
            <template #default="{ row }">
              <el-tag type="info" effect="plain">{{ targetLabel(row.targetCategory) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="conditionExpr" label="条件" min-width="180" show-overflow-tooltip />
          <el-table-column prop="actionExpr" label="动作" min-width="200" show-overflow-tooltip />
          <el-table-column prop="priorityNo" label="优先级" width="90" align="center" />
          <el-table-column label="状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.enabledFlag ? 'success' : 'info'" effect="dark" round>
                {{ row.enabledFlag ? '启用' : '停用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="日志" width="90" align="center" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" @click="filterByRule(row.ruleId)">查看</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </section>

    <section class="content-card p-6">
      <div class="mb-5 flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 class="panel-title">清洗日志</h2>
          <p class="panel-subtitle">记录命中的规则、来源记录和清洗前后值。</p>
        </div>
        <div class="flex flex-wrap items-center gap-2">
          <el-select v-model="logQuery.sourceTable" clearable placeholder="来源表" class="w-44">
            <el-option label="工艺参数值" value="parameter_value" />
            <el-option label="质量测量值" value="quality_measurement" />
          </el-select>
          <el-input v-model.trim="logQuery.sourceId" clearable placeholder="来源ID" class="w-64" />
          <el-button type="primary" :loading="loadingLogs" @click="loadLogs">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
        </div>
      </div>

      <el-table v-loading="loadingLogs" :data="logList" stripe style="width: 100%" @row-click="showLogDetail">
        <el-table-column label="执行时间" min-width="180">
          <template #default="{ row }">{{ row.createdAt || '--' }}</template>
        </el-table-column>
        <el-table-column label="来源表" width="150">
          <template #default="{ row }">{{ targetLabel(row.sourceTable) }}</template>
        </el-table-column>
        <el-table-column prop="sourceId" label="来源ID" min-width="220" show-overflow-tooltip />
        <el-table-column prop="ruleId" label="规则ID" min-width="220" show-overflow-tooltip />
        <el-table-column label="结果" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.actionResult === 'APPLIED' ? 'success' : 'warning'" round>
              {{ row.actionResult || '--' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="清洗前" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ compactJson(row.beforeValue) }}</template>
        </el-table-column>
        <el-table-column label="清洗后" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ compactJson(row.afterValue) }}</template>
        </el-table-column>
      </el-table>
    </section>

    <el-dialog v-model="detailVisible" title="清洗日志详情" width="720px" destroy-on-close>
      <div v-if="currentLog" class="space-y-4">
        <el-descriptions :column="2" border label-class-name="!bg-slate-50 !text-slate-600">
          <el-descriptions-item label="日志ID" :span="2">
            <span class="font-mono text-sm">{{ currentLog.cleaningLogId }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="来源表">{{ targetLabel(currentLog.sourceTable) }}</el-descriptions-item>
          <el-descriptions-item label="结果">{{ currentLog.actionResult || '--' }}</el-descriptions-item>
          <el-descriptions-item label="来源ID" :span="2">
            <span class="font-mono text-sm">{{ currentLog.sourceId || '--' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="规则ID" :span="2">
            <span class="font-mono text-sm">{{ currentLog.ruleId || '--' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="执行时间" :span="2">{{ currentLog.createdAt || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="grid gap-4 md:grid-cols-2">
          <div>
            <div class="mb-2 text-sm font-semibold text-slate-700">清洗前</div>
            <pre class="json-panel">{{ prettyJson(currentLog.beforeValue) }}</pre>
          </div>
          <div>
            <div class="mb-2 text-sm font-semibold text-slate-700">清洗后</div>
            <pre class="json-panel">{{ prettyJson(currentLog.afterValue) }}</pre>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import type { FormInstance, FormRules } from 'element-plus';
import { ElMessage } from 'element-plus';
import { Plus, Refresh, Search } from '@element-plus/icons-vue';

import {
  createCleaningRule,
  fetchCleaningLogs,
  fetchCleaningRules,
  type CleaningLog,
  type CleaningRule,
  type CreateCleaningRulePayload,
} from '@/api/cleaning';
import PageIntroCard from '@/components/dashboard/PageIntroCard.vue';

const formRef = ref<FormInstance>();
const loadingRules = ref(false);
const loadingLogs = ref(false);
const saving = ref(false);
const detailVisible = ref(false);
const ruleList = ref<CleaningRule[]>([]);
const logList = ref<CleaningLog[]>([]);
const currentLog = ref<CleaningLog | null>(null);

const defaultForm = (): CreateCleaningRulePayload => ({
  ruleCode: '',
  ruleName: '',
  targetCategory: 'parameter_value',
  conditionExpr: 'valueNum < 0',
  actionExpr: 'qualityFlag=ANOMALY',
  priorityNo: 100,
});

const form = reactive<CreateCleaningRulePayload>(defaultForm());
const logQuery = reactive({
  ruleId: '',
  sourceTable: '',
  sourceId: '',
});

const rules: FormRules<CreateCleaningRulePayload> = {
  ruleCode: [{ required: true, message: '请输入规则编码', trigger: 'blur' }],
  ruleName: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  targetCategory: [{ required: true, message: '请选择目标数据', trigger: 'change' }],
  conditionExpr: [{ required: true, message: '请输入条件表达式', trigger: 'blur' }],
  actionExpr: [{ required: true, message: '请输入动作表达式', trigger: 'blur' }],
  priorityNo: [{ required: true, message: '请输入优先级', trigger: 'change' }],
};

const metrics = computed(() => {
  const appliedCount = logList.value.filter((item) => item.actionResult === 'APPLIED').length;
  const enabledCount = ruleList.value.filter((item) => item.enabledFlag).length;
  return [
    { label: '规则总数', value: String(ruleList.value.length), extra: `${enabledCount} 条启用` },
    { label: '执行日志', value: String(logList.value.length), extra: `${appliedCount} 条已应用` },
    { label: '覆盖数据表', value: String(new Set(ruleList.value.map((item) => item.targetCategory || 'all')).size), extra: '规则目标' },
  ];
});

const targetLabel = (target?: string | null) => {
  switch (target) {
    case 'parameter_value':
      return '工艺参数值';
    case 'quality_measurement':
      return '质量测量值';
    case 'all':
      return '全部支持表';
    default:
      return target || '--';
  }
};

const compactJson = (value?: string | null) => {
  if (!value) return '--';
  try {
    return JSON.stringify(JSON.parse(value));
  } catch {
    return value;
  }
};

const prettyJson = (value?: string | null) => {
  if (!value) return '--';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
};

const loadRules = async () => {
  loadingRules.value = true;
  try {
    ruleList.value = await fetchCleaningRules();
  } catch {
    ElMessage.error('加载清洗规则失败');
  } finally {
    loadingRules.value = false;
  }
};

const loadLogs = async () => {
  loadingLogs.value = true;
  try {
    logList.value = await fetchCleaningLogs({
      ruleId: logQuery.ruleId || undefined,
      sourceTable: logQuery.sourceTable || undefined,
      sourceId: logQuery.sourceId || undefined,
    });
  } catch {
    ElMessage.error('加载清洗日志失败');
  } finally {
    loadingLogs.value = false;
  }
};

const submitRule = async () => {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;

  saving.value = true;
  try {
    await createCleaningRule({ ...form });
    ElMessage.success('清洗规则已创建');
    resetForm();
    await Promise.all([loadRules(), loadLogs()]);
  } catch {
    ElMessage.error('创建清洗规则失败');
  } finally {
    saving.value = false;
  }
};

const resetForm = () => {
  Object.assign(form, defaultForm());
  formRef.value?.clearValidate();
};

const filterByRule = async (ruleId: string) => {
  logQuery.ruleId = ruleId;
  logQuery.sourceTable = '';
  logQuery.sourceId = '';
  await loadLogs();
};

const showLogDetail = (row: CleaningLog) => {
  currentLog.value = row;
  detailVisible.value = true;
};

onMounted(() => {
  void Promise.all([loadRules(), loadLogs()]);
});
</script>

<style scoped>
.json-panel {
  max-height: 280px;
  overflow: auto;
  border-radius: 8px;
  background: #0f172a;
  color: #e2e8f0;
  padding: 14px;
  font-size: 12px;
  line-height: 1.6;
}

:deep(.el-table__row) {
  cursor: pointer;
}
</style>
