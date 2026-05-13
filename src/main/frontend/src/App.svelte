<script lang="ts">
  import { onMount } from 'svelte';
  let activeTab = 'home';

  // Placeholder for websocket connection
  onMount(() => {
    console.log("Nunki Frontend Mounted - WebSocket setup pending");
  });
</script>

<main>
  <h1>Nunki Control Panel</h1>
  
  <nav>
    <button on:click={() => activeTab = 'home'} class:active={activeTab === 'home'}>Home</button>
    <button on:click={() => activeTab = 'diagrams'} class:active={activeTab === 'diagrams'}>SVG Diagrams</button>
    <button on:click={() => activeTab = 'timeseries'} class:active={activeTab === 'timeseries'}>Time Series</button>
    <button on:click={() => activeTab = 'tree'} class:active={activeTab === 'tree'}>Tree View</button>
  </nav>

  <section>
    {#if activeTab === 'home'}
      <p>Welcome to Nunki. Select a module from the menu.</p>
    {:else if activeTab === 'diagrams'}
      <div class="placeholder">Dynamic SVG Diagrams / Synoptics Placeholder</div>
      <div class="placeholder">PlantUML Diagram Placeholder</div>
    {:else if activeTab === 'timeseries'}
      <div class="placeholder">Graph of Time Series Placeholder</div>
    {:else if activeTab === 'tree'}
      <div class="placeholder">Tree View and Dynamic Tables Placeholder</div>
    {/if}
  </section>
</main>

<style>
  :global(body) { font-family: sans-serif; padding: 2rem; }
  nav { margin-bottom: 2rem; }
  button { margin-right: 0.5rem; padding: 0.5rem 1rem; cursor: pointer; border: 1px solid #ccc; background: #fff; }
  button.active { background: #007bff; color: white; border-color: #0056b3; }
  .placeholder { border: 2px dashed #ccc; padding: 2rem; margin-top: 1rem; text-align: center; color: #666; }
</style>