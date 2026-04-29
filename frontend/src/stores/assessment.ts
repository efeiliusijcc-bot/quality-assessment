import { computed, reactive, ref } from 'vue';
import { defineStore } from 'pinia';
import { fetchBatches, fetchStations } from '@/api/assessment';

export interface AssessmentFilters {
  batchId: string;
  station: string;
  resultStatus: 'all' | 'pass' | 'fail';
  dateRange: [string, string] | [];
}

export interface AssessmentConfig {
  passThreshold: number;
  warningThreshold: number;
  refreshIntervalMs: number;
}

export const useAssessmentStore = defineStore('assessment', () => {
  const currentBatchId = ref('');
  const selectedStation = ref('');
  const activeSampleId = ref('');
  const globalFilters = reactive<AssessmentFilters>({
    batchId: '',
    station: '',
    resultStatus: 'all',
    dateRange: [],
  });
  const config = reactive<AssessmentConfig>({
    passThreshold: 90,
    warningThreshold: 85,
    refreshIntervalMs: 1000,
  });
  const lastAssessmentAt = ref('');
  const availableStations = ref<string[]>([]);
  const availableBatches = ref<string[]>([]);

  const loadStations = async () => {
    try {
      availableStations.value = await fetchStations();
      if (!selectedStation.value && availableStations.value.length > 0) {
        const firstStation = availableStations.value[0];
        if (firstStation) {
          selectedStation.value = firstStation;
        }
      }
    } catch {
      availableStations.value = [];
    }
  };

  const loadBatches = async () => {
    try {
      availableBatches.value = await fetchBatches();
      if (!currentBatchId.value && availableBatches.value.length > 0) {
        const firstBatch = availableBatches.value[0];
        if (firstBatch) {
          currentBatchId.value = firstBatch;
          globalFilters.batchId = firstBatch;
        }
      }
    } catch {
      availableBatches.value = [];
    }
  };

  const currentContextLabel = computed(() =>
    currentBatchId.value && selectedStation.value
      ? `${currentBatchId.value} / ${selectedStation.value}`
      : '加载中...'
  );

  const setBatchContext = (payload: { batchId?: string; station?: string; sampleId?: string }) => {
    if (payload.batchId) {
      currentBatchId.value = payload.batchId;
      globalFilters.batchId = payload.batchId;
    }

    if (payload.station) {
      selectedStation.value = payload.station;
      globalFilters.station = payload.station;
    }

    if (payload.sampleId) {
      activeSampleId.value = payload.sampleId;
    }

    lastAssessmentAt.value = new Date().toLocaleString('zh-CN', { hour12: false });
  };

  const setResultStatus = (status: AssessmentFilters['resultStatus']) => {
    globalFilters.resultStatus = status;
  };

  const setDateRange = (range: AssessmentFilters['dateRange']) => {
    globalFilters.dateRange = range;
  };

  const updateConfig = (payload: Partial<AssessmentConfig>) => {
    Object.assign(config, payload);
  };

  const resetFilters = () => {
    globalFilters.batchId = currentBatchId.value;
    globalFilters.station = selectedStation.value;
    globalFilters.resultStatus = 'all';
    globalFilters.dateRange = [];
  };

  return {
    activeSampleId,
    availableBatches,
    availableStations,
    config,
    currentBatchId,
    currentContextLabel,
    globalFilters,
    lastAssessmentAt,
    loadBatches,
    loadStations,
    resetFilters,
    selectedStation,
    setBatchContext,
    setDateRange,
    setResultStatus,
    updateConfig,
  };
});
