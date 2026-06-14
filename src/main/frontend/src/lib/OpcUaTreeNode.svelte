<script lang="ts">
  import { wsManager } from './websocket.svelte';

  // Props definition
  interface OpcUaNode {
    nodeId: string;
    name: string;
    nodeClass: string;
    value: string;
    children: OpcUaNode[];
  }

  let { node } = $props<{ node: OpcUaNode }>();

  let isOpen = $state(true);
  let isEditing = $state(false);
  let editValue = $state('');
  let isSaving = $state(false);
  let isInvoking = $state(false);
  let invokeResult = $state<string | null>(null);

  // Deriving the live value reactively from wsManager STOMP updates state
  let liveValue = $derived(
    wsManager.opcUaUpdates[node.nodeId]?.value !== undefined
      ? wsManager.opcUaUpdates[node.nodeId].value
      : node.value
  );

  // Detect live changes and trigger a micro-animation flash
  let flash = $state(false);
  $effect(() => {
    const val = liveValue;
    flash = true;
    const timer = setTimeout(() => {
      flash = false;
    }, 800);
    return () => clearTimeout(timer);
  });

  function toggleOpen() {
    isOpen = !isOpen;
  }

  function startEditing() {
    editValue = liveValue;
    isEditing = true;
  }

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

  async function handleInvoke() {
    isInvoking = true;
    invokeResult = null;
    let objectId = 'ns=1;s=Data';
    if (node.nodeId.includes('/')) {
      objectId = node.nodeId.substring(0, node.nodeId.lastIndexOf('/'));
    }
    const response = await wsManager.invokeOpcUaMethod(objectId, node.nodeId, []);
    isInvoking = false;
    if (response.success) {
      invokeResult = response.result || 'Success';
    } else {
      invokeResult = 'Failed';
    }
  }
</script>

<div class="tree-node" class:folder={node.nodeClass === 'Object'}>
  <div class="node-header">
    {#if node.nodeClass === 'Object'}
      <button class="toggle-btn" onclick={toggleOpen}>
        <span class="chevron" class:open={isOpen}>▶</span>
        <span class="icon">📁</span>
        <span class="name bold">{node.name}</span>
      </button>
      <span class="node-id mono text-muted">{node.nodeId}</span>
    {:else if node.nodeClass === 'Variable'}
      <span class="icon">🔢</span>
      <span class="name">{node.name}</span>
      <span class="value-container" class:flash={flash}>
        <span class="value mono accent-color">{liveValue}</span>
      </span>
      <span class="node-id mono text-muted">{node.nodeId}</span>
      
      {#if isEditing}
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
        <button class="btn-action edit" onclick={startEditing}>Edit</button>
      {/if}
    {:else if node.nodeClass === 'Method'}
      <span class="icon">⚡</span>
      <span class="name method-name">{node.name}()</span>
      <span class="node-id mono text-muted">{node.nodeId}</span>
      
      <div class="method-actions">
        <button class="btn-action invoke" onclick={handleInvoke} disabled={isInvoking}>
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

  {#if node.nodeClass === 'Object' && isOpen && node.children && node.children.length > 0}
    <div class="node-children">
      {#each node.children as child}
        <svelte:self node={child} />
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

  @keyframes flash-bg {
    0% { background-color: var(--accent-bg); }
    100% { background-color: transparent; }
  }

  .value-container.flash {
    animation: flash-bg 0.8s ease-out;
  }

  .node-children {
    padding-left: 20px;
    border-left: 1px dashed var(--border);
    margin-left: 8px;
  }

  /* Buttons and controls */
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
