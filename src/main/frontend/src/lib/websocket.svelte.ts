import { Client } from '@stomp/stompjs';

/*
 * REQ-00014 – Support for websocket/STOMP async exchanges
 * TSK-00021.2 – Build STOMP Service
 */

const getWsUrl = (): string => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  if (window.location.port === '5173') {
    return `ws://localhost:8080/ws-stomp`;
  }
  return `${protocol}//${window.location.host}/ws-stomp`;
};

export const getApiUrl = (path: string): string => {
  if (window.location.port === '5173') {
    return `http://localhost:8080${path}`;
  }
  return path;
};

export class WebSocketManager {
  private client: Client | null = null;
  
  // Svelte 5 state properties
  connected = $state(false);
  pongs = $state<{ id: string; message: string; timestamp: number }[]>([]);
  opcUaUpdates = $state<Record<string, { value: string; timestamp: number }>>({});

  connect() {
    /*
     * TSK-00021.2: Connect STOMP client to /ws-stomp and subscribe to /topic/ping-response
     */
    if (this.client) return;

    const brokerURL = getWsUrl();
    console.log('Connecting to WebSocket broker at:', brokerURL);

    this.client = new Client({
      brokerURL,
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      onConnect: (frame) => {
        this.connected = true;
        console.log('STOMP connected');

        this.client?.subscribe('/topic/ping-response', (message) => {
          try {
            const body = JSON.parse(message.body);
            const pong = {
              id: Math.random().toString(36).substring(2, 9),
              message: body.message,
              timestamp: body.timestamp
            };
            this.pongs = [pong, ...this.pongs].slice(0, 50); // limit to last 50 pongs
          } catch (e) {
            console.error('Failed to parse pong message', e);
          }
        });

        this.client?.subscribe('/topic/opcua-tree', (message) => {
          try {
            const body = JSON.parse(message.body);
            console.log('Received OPC-UA subscription update:', body);
            this.opcUaUpdates = {
              ...this.opcUaUpdates,
              [body.nodeId]: {
                value: body.value,
                timestamp: body.timestamp
              }
            };
          } catch (e) {
            console.error('Failed to parse OPC UA update message', e);
          }
        });
      },
      onDisconnect: () => {
        this.connected = false;
        console.log('STOMP disconnected');
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame);
      }
    });

    this.client.activate();
  }

  disconnect() {
    /*
     * TSK-00021.2: Cleanly disconnect the STOMP client
     */
    if (this.client) {
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }

  sendPing(content: string) {
    /*
     * TSK-00021.2: Publish ping message to /app/ping
     */
    if (!this.client || !this.connected) {
      console.warn('Cannot send ping: STOMP client is not connected');
      return;
    }

    const payload = {
      sender: 'svelte-client',
      content: content,
      timestamp: Date.now()
    };

    this.client.publish({
      destination: '/app/ping',
      body: JSON.stringify(payload)
    });
    console.log('Sent ping:', payload);
  }

  async writeOpcUaValue(nodeId: string, value: string, type: string): Promise<boolean> {
    try {
      const response = await fetch(getApiUrl('/api/opcua/write'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ nodeId, value, type })
      });
      if (!response.ok) return false;
      const data = await response.json();
      return data.success;
    } catch (e) {
      console.error('Failed to write OPC-UA value:', e);
      return false;
    }
  }

  async invokeOpcUaMethod(objectId: string, methodId: string, args: string[]): Promise<{ success: boolean; result: string }> {
    try {
      const response = await fetch(getApiUrl('/api/opcua/invoke'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ objectId, methodId, arguments: args })
      });
      if (!response.ok) return { success: false, result: '' };
      const data = await response.json();
      return { success: data.success, result: data.result };
    } catch (e) {
      console.error('Failed to invoke OPC-UA method:', e);
      return { success: false, result: '' };
    }
  }
}

// Single shared instance for the application
export const wsManager = new WebSocketManager();
