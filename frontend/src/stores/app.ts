import { computed, ref } from 'vue';
import { defineStore } from 'pinia';

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(false);

  const toggleSidebar = () => {
    sidebarCollapsed.value = !sidebarCollapsed.value;
  };

  const sidebarWidth = computed(() => (sidebarCollapsed.value ? '84px' : '260px'));

  return {
    sidebarCollapsed,
    sidebarWidth,
    toggleSidebar,
  };
});
