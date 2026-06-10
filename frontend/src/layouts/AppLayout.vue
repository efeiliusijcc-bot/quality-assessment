<template>
  <div class="flex min-h-screen bg-transparent p-3 sm:p-4">
    <AppSidebar />

    <div class="min-w-0 flex-1 px-2 sm:px-4">
      <AppHeader />

      <main class="page-shell">
        <RouterView v-slot="{ Component, route }">
          <Transition name="page" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </Transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<script setup lang="ts">
import AppHeader from '@/components/layout/AppHeader.vue';
import AppSidebar from '@/components/layout/AppSidebar.vue';
</script>
