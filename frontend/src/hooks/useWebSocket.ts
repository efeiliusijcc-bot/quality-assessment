import { computed, onBeforeUnmount, ref } from 'vue';

export type WebSocketStatus = 'idle' | 'connecting' | 'open' | 'reconnecting' | 'closed' | 'error';

export interface UseWebSocketOptions {
  autoConnect?: boolean;
  heartbeatInterval?: number;
  heartbeatMessage?: string;
  pongTimeout?: number;
  reconnectDelay?: number;
  reconnectLimit?: number;
  protocols?: string | string[];
  onOpen?: (event: Event) => void;
  onClose?: (event: CloseEvent) => void;
  onError?: (event: Event) => void;
  onMessage?: (event: MessageEvent) => void;
}

const DEFAULT_OPTIONS: Required<
  Pick<
    UseWebSocketOptions,
    'autoConnect' | 'heartbeatInterval' | 'heartbeatMessage' | 'pongTimeout' | 'reconnectDelay' | 'reconnectLimit'
  >
> = {
  autoConnect: true,
  heartbeatInterval: 15000,
  heartbeatMessage: 'ping',
  pongTimeout: 8000,
  reconnectDelay: 3000,
  reconnectLimit: Infinity,
};

export const useWebSocket = (url: string, options: UseWebSocketOptions = {}) => {
  const mergedOptions = {
    ...DEFAULT_OPTIONS,
    ...options,
  };

  const status = ref<WebSocketStatus>('idle');
  const socket = ref<WebSocket | null>(null);
  const reconnectAttempts = ref(0);
  const lastMessageAt = ref<number | null>(null);
  const manuallyClosed = ref(false);
  const lastError = ref<Event | null>(null);

  let heartbeatTimer: number | null = null;
  let pongTimer: number | null = null;
  let reconnectTimer: number | null = null;

  const clearHeartbeatTimer = () => {
    if (heartbeatTimer !== null) {
      window.clearInterval(heartbeatTimer);
      heartbeatTimer = null;
    }
  };

  const clearPongTimer = () => {
    if (pongTimer !== null) {
      window.clearTimeout(pongTimer);
      pongTimer = null;
    }
  };

  const clearReconnectTimer = () => {
    if (reconnectTimer !== null) {
      window.clearTimeout(reconnectTimer);
      reconnectTimer = null;
    }
  };

  const cleanupTimers = () => {
    clearHeartbeatTimer();
    clearPongTimer();
    clearReconnectTimer();
  };

  const isOpen = computed(() => status.value === 'open');

  const scheduleReconnect = () => {
    if (manuallyClosed.value || reconnectAttempts.value >= mergedOptions.reconnectLimit) {
      status.value = 'closed';
      return;
    }

    clearReconnectTimer();
    status.value = 'reconnecting';
    reconnectAttempts.value += 1;
    reconnectTimer = window.setTimeout(() => {
      connect();
    }, mergedOptions.reconnectDelay);
  };

  const startHeartbeat = () => {
    clearHeartbeatTimer();
    clearPongTimer();

    heartbeatTimer = window.setInterval(() => {
      if (socket.value?.readyState !== WebSocket.OPEN) {
        return;
      }

      socket.value.send(mergedOptions.heartbeatMessage);
      clearPongTimer();
      pongTimer = window.setTimeout(() => {
        socket.value?.close();
      }, mergedOptions.pongTimeout);
    }, mergedOptions.heartbeatInterval);
  };

  const connect = () => {
    cleanupTimers();
    manuallyClosed.value = false;
    status.value = reconnectAttempts.value > 0 ? 'reconnecting' : 'connecting';

    const ws = new WebSocket(url, mergedOptions.protocols);
    socket.value = ws;

    ws.onopen = (event) => {
      status.value = 'open';
      reconnectAttempts.value = 0;
      startHeartbeat();
      mergedOptions.onOpen?.(event);
    };

    ws.onmessage = (event) => {
      lastMessageAt.value = Date.now();

      if (event.data === 'pong') {
        clearPongTimer();
        return;
      }

      clearPongTimer();
      mergedOptions.onMessage?.(event);
    };

    ws.onerror = (event) => {
      lastError.value = event;
      status.value = 'error';
      mergedOptions.onError?.(event);
    };

    ws.onclose = (event) => {
      cleanupTimers();
      socket.value = null;
      mergedOptions.onClose?.(event);

      if (manuallyClosed.value) {
        status.value = 'closed';
        return;
      }

      scheduleReconnect();
    };
  };

  const disconnect = () => {
    manuallyClosed.value = true;
    cleanupTimers();

    if (socket.value && socket.value.readyState <= WebSocket.OPEN) {
      socket.value.close();
    }

    socket.value = null;
    status.value = 'closed';
  };

  const send = (payload: string | ArrayBufferLike | Blob | ArrayBufferView) => {
    if (socket.value?.readyState !== WebSocket.OPEN) {
      return false;
    }

    socket.value.send(payload);
    return true;
  };

  if (mergedOptions.autoConnect) {
    connect();
  }

  onBeforeUnmount(() => {
    disconnect();
  });

  return {
    connect,
    disconnect,
    isOpen,
    lastError,
    lastMessageAt,
    reconnectAttempts,
    send,
    socket,
    status,
  };
};
