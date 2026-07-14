<script lang="ts">
  /**
   * @component OpcUaTreeNode
   * @description Architecture Component: Dynamic Telemetry Tree / HMI Node View.
   * Renders a recursive tree node mapped to the remote OPC-UA address space.
   * Leverages Svelte 5 runes (`$props`, `$state`, `$derived`, `$effect`) to dynamically
   * update telemetry leaves in response to global WebSocket signals (`wsManager.opcUaUpdates`).
   * 
   * Supported NodeClasses:
   * - `Object`: Renders as folders with collapsible chevron expansion triggers and recursive sub-tree lists.
   * - `Variable`: Displays real-time tag values, flashes on telemetry changes, and provides inline write inputs.
   * - `Method`: Renders lightning symbol execution links with status outputs.
   */

  import { wsManager } from './websocket.svelte';
  // Import itself to support Svelte 5 recursive self-import components (replacing deprecated <svelte:self>)
  import OpcUaTreeNode from './OpcUaTreeNode.svelte';

  /**
   * OpcUaNode interface mapping standard schemas.
   */
  interface OpcUaNode {
    nodeId: string;       // Absolute OPC-UA Node Identifier (e.g. 'ns=1;s=Data/MyInt')
    name: string;         // Human readable identifier name (e.g. 'MyInt')
    nodeClass: string;    // OPC-UA class type classification ('Object' | 'Variable' | 'Method')
    value: string;        // Initial value payload string
    children: OpcUaNode[];// Nested children collection for Object folders
  }

  // Define component props using Svelte 5 `$props` rune
  let { node, parentId = '', isLoggedIn = false } = $props<{ node: OpcUaNode; parentId?: string; isLoggedIn?: boolean }>();

  // ==========================================
  // --- LOCAL Runes STATE PROPERTIES ---
  // ==========================================
  
  // Controls collapsible panel expansion status (default: true [expanded])
  let isOpen = $state(true);
  
  // Toggles inline edit mode editor inputs (default: false [read-only display])
  let isEditing = $state(false);
  
  // Caches keyboard text edits before submitting to REST controllers
  let editValue = $state('');
  
  // Spinner trigger indicating an active REST write request is processing
  let isSaving = $state(false);
  
  // Spinner trigger indicating a method execution is processing
  let isInvoking = $state(false);
  
  // Caches output details returned from method executions
  let invokeResult = $state<string | null>(null);

  /**
   * Reactive Derived state: Intercepts updates from wsManager.opcUaUpdates.
   * If a new value is broadcasted over the STOMP channel, overrides the default node.value.
   */
  let liveValue = $derived(
    wsManager.opcUaUpdates[node.nodeId]?.value !== undefined
      ? wsManager.opcUaUpdates[node.nodeId].value
      : node.value
  );

  // Animation flag indicating the value has changed
  let flash = $state(false);
  
  /**
   * Reactive effect tracking shifts in liveValue.
   * Triggers a momentary highlight flash (flash = true) and sets a timer to clear the state.
   */
  $effect(() => {
    const val = liveValue; // Establishes a reactive dependency on liveValue
    flash = true;
    const timer = setTimeout(() => {
      flash = false;
    }, 800);
    // Cleanup callback executed to reset pending timeouts if values transition rapidly
    return () => clearTimeout(timer);
  });

  /**
   * Collapses/Expands nested directory panels.
   */
  function toggleOpen() {
    isOpen = !isOpen;
  }

  /**
   * Activates edit mode and pre-populates state with current telemetry.
   */
  function startEditing() {
    editValue = liveValue;
    isEditing = true;
  }

  /**
   * Basic type heuristic helper.
   * Analyzes tag names and value strings to guess primitive datatypes.
   * Avoids writing incorrect datatypes to the Milo OPC-UA server.
   * 
   * Rules:
   * - Boolean: If value is 'true'/'false' or name contains 'switch'/'bool'.
   * - Double: If value is numeric and contains a decimal point '.'.
   * - Integer: If value is numeric but has no decimals.
   * - String: Default fallback datatype.
   * 
   * @param {string} val - String input.
   * @param {string} name - Tag name.
   * @returns {string} Inferred type string.
   */
  function inferType(val: string, name: string): string {
    const lowerName = name.toLowerCase();
    if (val === 'true' || val === 'false' || lowerName.includes('switch') || lowerName.includes('bool')) {
      return 'Boolean';
    }
    if (!isNaN(Number(val))) {
      if (val.includes('.')) return 'Double';
      return 'Integer';
    }
    return 'String';
  }

  /**
   * Handles REST Post value submission.
   * Dispatches details to the backend controller, updates loaders, and exits edit mode on success.
   */
  async function handleWrite() {
    isSaving = true;
    const type = inferType(editValue, node.name);
    const success = await wsManager.writeOpcUaValue(node.nodeId, editValue, type);
    isSaving = false;
    if (success) {
      isEditing = false;
    } else {
      alert('Failed to write value to OPC-UA server.');
    }
  }

  /**
   * Dispatches REST Method invocation calls using dynamically resolved parents.
   */
  async function handleInvoke() {
    isInvoking = true;
    invokeResult = null;
    const objectId = parentId || node.nodeId;
    const response = await wsManager.invokeOpcUaMethod(objectId, node.nodeId, ["null"]);
    isInvoking = false;
    if (response.success) {
      try {
        const parsed = JSON.parse(response.result);
        invokeResult = parsed.value !== undefined ? String(parsed.value) : 'Success';
      } catch (e) {
        invokeResult = response.result || 'Success';
      }
    } else {
      invokeResult = 'Failed';
    }
  }
</script>

<div class="tree-node" class:folder={node.nodeClass === 'Object'}>
  <div class="node-header">
    {#if node.nodeClass === 'Object'}
      <!-- OBJECT: Collapsible directory header -->
      <button class="toggle-btn" onclick={toggleOpen}>
        <span class="chevron" class:open={isOpen}>▶</span>
        <span class="icon">📁</span>
        <span class="name bold">{node.name}</span>
      </button>
      <span class="node-id mono text-muted">{node.nodeId}</span>
    {:else if node.nodeClass === 'Variable'}
      <!-- VARIABLE: Displays real-time telemetry leaf -->
      <span class="icon">🔢</span>
      <span class="name">{node.name}</span>
      <span class="value-container" class:flash={flash}>
        <span class="value mono accent-color">{liveValue}</span>
      </span>
      <span class="node-id mono text-muted">{node.nodeId}</span>
      
      {#if isEditing}
        <!-- Inline input editor panel -->
        <div class="edit-inline">
          <input 
            type="text" 
            bind:value={editValue} 
            class="edit-input" 
            placeholder="New value..." 
            disabled={isSaving}
          />
          <button class="btn-save" onclick={handleWrite} disabled={isSaving}>
            {isSaving ? '...' : 'Save'}
          </button>
          <button class="btn-cancel" onclick={() => isEditing = false} disabled={isSaving}>
            ✕
          </button>
        </div>
      {:else}
        {#if isLoggedIn}
          <button class="btn-action edit" onclick={startEditing}>Edit</button>
        {/if}
      {/if}
    {:else if node.nodeClass === 'Method'}
      <!-- METHOD: Execution control deck -->
      <span class="icon">⚡</span>
      <span class="name method-name">{node.name}()</span>
      <span class="node-id mono text-muted">{node.nodeId}</span>
      
      <div class="method-actions">
        <button class="btn-action invoke" onclick={handleInvoke} disabled={isInvoking || !isLoggedIn}>
          {isInvoking ? 'Executing...' : 'Invoke'}
        </button>
        {#if invokeResult !== null}
          <span class="invoke-badge" class:error={invokeResult === 'Failed'}>
            {invokeResult}
          </span>
        {/if}
      </div>
    {/if}
  </div>

  <!-- RECURSION BRANCH: Render subfolders if nodeClass is 'Object' and node is expanded -->
  {#if node.nodeClass === 'Object' && isOpen && node.children && node.children.length > 0}
    <div class="node-children">
      {#each node.children as child}
        <OpcUaTreeNode node={child} parentId={node.nodeId} {isLoggedIn} />
      {/each}
    </div>
  {/if}
</div>

<style>
  .tree-node {
    margin: 4px 0;
    font-size: 15px;
    text-align: left;
  }

  .node-header {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 12px;
    border-radius: 6px;
    transition: background-color 0.2s;
    flex-wrap: wrap;
  }

  .node-header:hover {
    background-color: rgba(255, 255, 255, 0.05);
  }

  .toggle-btn {
    display: flex;
    align-items: center;
    gap: 6px;
    background: none;
    border: none;
    cursor: pointer;
    padding: 0;
    color: inherit;
    font-size: inherit;
  }

  /* Rotating arrow indicating directory status */
  .chevron {
    display: inline-block;
    font-size: 10px;
    transition: transform 0.2s;
    color: var(--text);
  }

  .chevron.open {
    transform: rotate(90deg);
  }

  .icon {
    font-size: 16px;
  }

  .name {
    color: var(--text-h);
  }

  .name.bold {
    font-weight: 600;
  }

  .method-name {
    font-style: italic;
    color: var(--accent);
  }

  .node-id {
    font-size: 12px;
    opacity: 0.6;
  }

  .value-container {
    padding: 2px 6px;
    border-radius: 4px;
    transition: background-color 0.8s ease-out;
  }

  /* Keyframe triggering neon glow highlight on live value shifts */
  @keyframes flash-bg {
    0% { background-color: var(--accent-bg); }
    100% { background-color: transparent; }
  }

  .value-container.flash {
    animation: flash-bg 0.8s ease-out;
  }

  /* Sidebar dashed border indent highlighting hierarchy depth */
  .node-children {
    padding-left: 20px;
    border-left: 1px dashed var(--border);
    margin-left: 8px;
  }

  /* UI Interactive action button shapes */
  .btn-action {
    font-size: 12px;
    padding: 2px 8px;
    border-radius: 4px;
    cursor: pointer;
    border: 1px solid var(--border);
    background: var(--social-bg);
    color: var(--text-h);
    transition: all 0.2s;
  }

  .btn-action:hover {
    border-color: var(--accent);
    background: var(--accent-bg);
    color: var(--accent);
  }

  .btn-action.invoke {
    border-color: var(--accent);
    background: var(--accent-bg);
    color: var(--accent);
  }

  .btn-action.invoke:hover {
    background: var(--accent);
    color: #fff;
  }

  .edit-inline {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .edit-input {
    font-size: 12px;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid var(--border);
    background: var(--code-bg);
    color: var(--text-h);
    width: 80px;
  }

  .btn-save, .btn-cancel {
    font-size: 12px;
    padding: 2px 6px;
    border-radius: 4px;
    cursor: pointer;
    border: 1px solid var(--border);
    background: var(--social-bg);
    color: var(--text-h);
  }

  .btn-save {
    background: var(--accent-bg);
    border-color: var(--accent);
    color: var(--accent);
  }

  .method-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  /* Execution badge metrics colors */
  .invoke-badge {
    font-size: 11px;
    padding: 1px 6px;
    border-radius: 4px;
    background-color: rgba(16, 185, 129, 0.15);
    color: #10b981;
    border: 1px solid rgba(16, 185, 129, 0.3);
  }

  .invoke-badge.error {
    background-color: rgba(239, 68, 68, 0.15);
    color: #ef4444;
    border: 1px solid rgba(239, 68, 68, 0.3);
  }

  .text-muted {
    color: var(--text);
  }
</style>


