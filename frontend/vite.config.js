import path from 'node:path';
import { defineConfig, loadEnv } from 'vite';
import vue from '@vitejs/plugin-vue';
import viteCompression from 'vite-plugin-compression';
import AutoImport from 'unplugin-auto-import/vite';
import Components from 'unplugin-vue-components/vite';
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers';
export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, __dirname);
    return {
        plugins: [
            vue(),
            AutoImport({
                imports: ['vue', 'vue-router', 'pinia'],
                resolvers: [ElementPlusResolver()],
                dts: 'src/auto-imports.d.ts',
            }),
            Components({
                resolvers: [
                    ElementPlusResolver({
                        importStyle: 'css',
                    }),
                ],
                dts: 'src/components.d.ts',
            }),
            viteCompression({
                algorithm: 'gzip',
                ext: '.gz',
                threshold: 10240,
                deleteOriginFile: false,
            }),
        ],
        resolve: {
            alias: {
                '@': path.resolve(__dirname, 'src'),
            },
        },
        build: {
            chunkSizeWarningLimit: 800,
            rollupOptions: {
                output: {
                    manualChunks: {
                        vendor: ['vue', 'vue-router', 'pinia', 'axios'],
                        'element-plus': ['element-plus', '@element-plus/icons-vue'],
                        echarts: ['echarts/core', 'echarts/charts', 'echarts/components', 'echarts/renderers'],
                    },
                    chunkFileNames: 'assets/js/[name]-[hash].js',
                    entryFileNames: 'assets/js/[name]-[hash].js',
                    assetFileNames: 'assets/[ext]/[name]-[hash].[ext]',
                },
            },
        },
        server: {
            host: '0.0.0.0',
            port: 5173,
            proxy: {
                '/api': {
                    target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
                    changeOrigin: true,
                    secure: false,
                },
                '/ws': {
                    target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:8080',
                    changeOrigin: true,
                    secure: false,
                    ws: true,
                },
            },
        },
    };
});
