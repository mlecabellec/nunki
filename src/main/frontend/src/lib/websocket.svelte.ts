import { Client } from '@stomp/stompjs';

/**
 * @file websocket.svelte.ts
 * @description Architecture Layer: Networking / State Synchronization Service.
 * 
 * This module is responsible for:
 * 1. Establishing and maintaining a persistent STOMP-over-WebSocket connection with the Spring Boot backend.
 * 2. Caching global application states (connection flags, STOMP responses, OPC-UA registers) using Svelte 5 Runes ($state).
 * 3. Dispatched client-to-server network queries (REST POST requests) for modifying tags and executing methods on the remote OPC-UA server.
 * 
 * System Requirements Satisfied:
 * - REQ-00014: Support for websocket/STOMP async exchanges (reconnection buffers, JSON serialization).
 * - TSK-00021.2: Build STOMP Service (handling connection handshake, message subscriptions, channel routing).
 */

/**
 * Helper function to dynamically compile the WebSocket Broker URL depending on the hosting port.
 * Handles the routing mismatch when running under Vite's development HMR server (port 5173).
 * 
 * Routing Logic:
 * - When window.location.port is '5173' (Vite's server): Redirects to port '8080' (Spring Boot broker) via ws://localhost:8080/ws-stomp.
 * - Otherwise (Production build served by Spring Boot): Dynamically parses window.location.host, using wss:// if hosted securely under HTTPS.
 * 
 * @returns {string} The computed absolute WebSocket endpoint URI.
 */
const getWsUrl = (): string => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  if (window.location.port === '5173') {
    return `ws://localhost:8080/ws-stomp`;
  }
  return `${protocol}//${window.location.host}/ws-stomp`;
};

/**
 * Helper function to compile the absolute REST API endpoint URI.
 * Resolves Cross-Origin Resource Sharing (CORS) boundaries during local development.
 * 
 * Routing Logic:
 * - Local Dev Server (port 5173): Prefixes queries with 'http://localhost:8080' to target backend controllers.
 * - Production: Keeps the relative route path intact, targeting the same host serving the dashboard assets.
 * 
 * @param {string} path - The relative api subpath (e.g. '/api/opcua/tree').
 * @returns {string} The fully compiled target API URL.
 */
export const getApiUrl = (path: string): string => {
  if (window.location.port === '5173') {
    return `http://localhost:8080${path}`;
  }
  return path;
};

/**
 * WebSocketManager
 * Singleton coordinator class handling real-time data flow.
 * Instantiates the @stomp/stompjs Client connection and exposes reactive properties.
 */
export class WebSocketManager {
  /**
   * Underlaying STOMP over WebSocket Client wrapper.
   * Handles frame transmission, heartbeat parsing, and message queues.
   */
  private client: Client | null = null;

  /**
   * Stored authorization header for Basic Auth.
   */
  authHeader = $state<string | null>(null);

  setAuthHeader(header: string | null) {
    this.authHeader = header;
  }

  getHeaders(customHeaders: Record<string, string> = {}): Record<string, string> {
    const headers: Record<string, string> = { ...customHeaders };
    if (this.authHeader) {
      headers['Authorization'] = this.authHeader;
    }
    return headers;
  }
  
  // ==========================================
  // --- SVELTE 5 REACTIVE Runes STATES ---
  // ==========================================
  
  /**
   * Connection State Indicator.
   * - `true`: Active STOMP session established, channels subscribed, ready for transactions.
   * - `false`: Disconnected, trying reconnection, or closed down.
   */
  connected = $state(false);
  
  /**
   * Historical buffer caching STOMP ping messages.
   * Stores records of type { id, message, timestamp }.
   * Limited to a maximum capacity of 50 items to prevent memory inflation.
   */
  pongs = $state<{ id: string; message: string; timestamp: number }[]>([]);
  
  /**
   * Reactive OPC-UA tag telemetry dictionary.
   * Keys: OPC-UA NodeIDs (e.g., 'ns=1;s=Data/MySwitch').
   * Values: Record schema { value, timestamp }.
   * Reactively feeds data directly into tree leaf views and animated synoptic graphics.
   */
  opcUaUpdates = $state<Record<string, { value: string; timestamp: number }>>({});

  /**
   * Connection Lifecycle Controller.
   * Instantiates a new STOMP Client session. Registers event handlers for connect/disconnect
   * states, heartbeat ticks, error frames, and target channel subscriptions.
   */
  connect() {
    // Guards against spawning duplicate client instances if connection is active
    if (this.client) return;

    const brokerURL = getWsUrl();
    console.log('[STOMP] Initializing connection to Broker at URL:', brokerURL);

    // Initialize STOMP options
    this.client = new Client({
      brokerURL,
      
      // Auto-reconnect delay in milliseconds. The client waits 5 seconds if connection drops.
      reconnectDelay: 5000,
      
      // Heartbeat configuration: sends packets every 4 seconds to verify link integrity.
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      
      /**
       * Connect Callback: Triggered on successful STOMP connection handshake.
       * Sets connection states, logs details, and establishes destination channel subscriptions.
       */
      onConnect: (frame) => {
        this.connected = true;
        console.log('[STOMP] Connection established successfully:', frame.headers);

        // 1. Subscribe to the public Ping Response Channel
        this.client?.subscribe('/topic/ping-response', (message) => {
          try {
            const body = JSON.parse(message.body);
            const pong = {
              id: Math.random().toString(36).substring(2, 9), // Generate a lightweight unique tag ID
              message: body.message,
              timestamp: body.timestamp
            };
            // Reactively prepend the newest pong to the list, capping it at a maximum of 50 logs
            this.pongs = [pong, ...this.pongs].slice(0, 50);
            console.log('[STOMP] Pong received and parsed:', pong);
          } catch (e) {
            console.error('[STOMP] Failed to parse ping-response message body:', e);
          }
        });

        // 2. Subscribe to the OPC-UA Live Node Update Channel
        this.client?.subscribe('/topic/opcua-tree', (message) => {
          try {
            const body = JSON.parse(message.body);
            console.log('[STOMP] OPC-UA telemetry update received:', body);
            
            // Reactively map the payload onto our global dictionary state
            this.opcUaUpdates = {
              ...this.opcUaUpdates,
              [body.nodeId]: {
                value: body.value,
                timestamp: body.timestamp
              }
            };
          } catch (e) {
            console.error('[STOMP] Failed to parse OPC-UA update message body:', e);
          }
        });
      },
      
      /**
       * Disconnect Callback: Triggered when the STOMP connection closes.
       */
      onDisconnect: () => {
        this.connected = false;
        console.log('[STOMP] Connection closed down.');
      },
      
      /**
       * STOMP Protocol Error Callback: Handles frame level failures.
       */
      onStompError: (frame) => {
        console.error('[STOMP] Protocol error encountered:', frame.body);
      }
    });

    // Activate the STOMP connection client
    this.client.activate();
  }

  /**
   * Disconnect Lifecycle Controller.
   * Closes active sockets and cleans up references.
   */
  disconnect() {
    if (this.client) {
      console.log('[STOMP] Deactivating connection...');
      this.client.deactivate();
      this.client = null;
      this.connected = false;
    }
  }

  /**
   * Sends a ping text frame payload to the Spring Boot message router.
   * 
   * @param {string} content - Message body string payload.
   */
  sendPing(content: string) {
    if (!this.client || !this.connected) {
      console.warn('[STOMP] Cannot send ping: client is not currently connected to WebSocket.');
      return;
    }

    const payload = {
      sender: 'svelte-client',
      content: content,
      timestamp: Date.now()
    };

    // Publishes payload mapping to /app/ping destination
    this.client.publish({
      destination: '/app/ping',
      body: JSON.stringify(payload)
    });
    console.log('[STOMP] Ping published to /app/ping:', payload);
  }

  /**
   * REST Interface Method: Writes a new value to a specific remote OPC-UA Variable register.
   * Matches the Java signature: `POST /api/opcua/write`
   * 
   * @param {string} nodeId - Target OPC-UA Node Identifier.
   * @param {string} value - New value string.
   * @param {string} type - DataType identifier (Boolean, Integer, Double, String).
   * @returns {Promise<boolean>} True if the Milo Client reported a successful write transaction.
   */
  async writeOpcUaValue(nodeId: string, value: string, type: string): Promise<boolean> {
    try {
      console.log(`[REST] Dispatching Write request for Node '${nodeId}' -> Value: '${value}' (${type})`);
      const response = await fetch(getApiUrl('/api/opcua/write'), {
        method: 'POST',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ nodeId, value, type })
      });
      if (!response.ok) {
        console.error(`[REST] Write call failed with status: ${response.status}`);
        return false;
      }
      const data = await response.json();
      return data.success;
    } catch (e) {
      console.error('[REST] Exception during write call execution:', e);
      return false;
    }
  }

  /**
   * REST Interface Method: Invokes a remote OPC-UA Method node.
   * Matches the Java signature: `POST /api/opcua/invoke`
   * 
   * @param {string} objectId - Parent Object Node Identifier.
   * @param {string} methodId - Target Method Node Identifier to execute.
   * @param {string[]} args - Invocation parameters.
   * @returns {Promise<{ success: boolean; result: string }>} Evaluation result and success state.
   */
  async invokeOpcUaMethod(objectId: string, methodId: string, args: string[]): Promise<{ success: boolean; result: string }> {
    try {
      console.log(`[REST] Dispatching Invoke request for Method '${methodId}' (Parent: '${objectId}')`);
      const response = await fetch(getApiUrl('/api/opcua/invoke'), {
        method: 'POST',
        headers: this.getHeaders({ 'Content-Type': 'application/json' }),
        body: JSON.stringify({ objectId, methodId, arguments: args })
      });
      if (!response.ok) {
        console.error(`[REST] Method invoke failed with status: ${response.status}`);
        return { success: false, result: '' };
      }
      const data = await response.json();
      return { success: data.success, result: data.result };
    } catch (e) {
      console.error('[REST] Exception during method invoke execution:', e);
      return { success: false, result: '' };
    }
  }
}

/**
 * Shared singleton instance representing the websocket client service.
 * Used universally across UI components to query real-time signals.
 */
export const wsManager = new WebSocketManager();


