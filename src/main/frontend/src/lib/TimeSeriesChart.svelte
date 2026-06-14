<script lang="ts">
  /*
   * REQ-00022 – ability to display graph of time series
   * TSK-00023.1 – SVG & Charting Prerequisites / Native SVG Chart
   */

  interface DataPoint {
    timestamp: number;
    value: number;
  }

  // Props
  let { data = [] }: { data: DataPoint[] } = $props();

  // Dimensions
  let width = $state(600);
  let height = $state(300);
  const padding = { top: 20, right: 20, bottom: 40, left: 50 };

  // Computed bounds and paths
  let minX = $derived(data.length > 0 ? Math.min(...data.map(d => d.timestamp)) : 0);
  let maxX = $derived(data.length > 0 ? Math.max(...data.map(d => d.timestamp)) : 100);
  let minY = $derived(data.length > 0 ? Math.min(0, ...data.map(d => d.value)) : 0);
  let maxY = $derived(data.length > 0 ? Math.max(100, ...data.map(d => d.value)) : 100);

  // Map values to coordinates
  const getX = (val: number) => {
    if (maxX === minX) return padding.left;
    return padding.left + ((val - minX) / (maxX - minX)) * (width - padding.left - padding.right);
  };

  const getY = (val: number) => {
    if (maxY === minY) return height - padding.bottom;
    return height - padding.bottom - ((val - minY) / (maxY - minY)) * (height - padding.top - padding.bottom);
  };

  // Generate SVG path points
  let points = $derived(data.map(d => ({ x: getX(d.timestamp), y: getY(d.value) })));

  let pathString = $derived(
    points.length > 0
      ? `M ${points[0].x} ${points[0].y} ` + points.slice(1).map(p => `L ${p.x} ${p.y}`).join(' ')
      : ''
  );

  let areaString = $derived(
    points.length > 0
      ? `${pathString} L ${points[points.length - 1].x} ${height - padding.bottom} L ${points[0].x} ${height - padding.bottom} Z`
      : ''
  );

  // Grid lines
  let yGridLines = $derived.by(() => {
    const lines = [];
    const step = (maxY - minY) / 5;
    for (let i = 0; i <= 5; i++) {
      const val = minY + step * i;
      lines.push({ val: val.toFixed(1), y: getY(val) });
    }
    return lines;
  });
</script>

<div class="chart-container" bind:clientWidth={width} bind:clientHeight={height}>
  {#if data.length === 0}
    <div class="no-data">No time series data available</div>
  {:else}
    <svg {width} {height}>
      <defs>
        <linearGradient id="chart-area-grad" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stop-color="var(--accent)" stop-opacity="0.3" />
          <stop offset="100%" stop-color="var(--accent)" stop-opacity="0.0" />
        </linearGradient>
      </defs>

      <!-- Grid lines -->
      {#each yGridLines as line}
        <line x1={padding.left} y1={line.y} x2={width - padding.right} y2={line.y} class="grid-line" />
        <text x={padding.left - 10} y={line.y + 4} class="axis-text axis-label-y">{line.val}</text>
      {/each}

      <!-- Bottom Axis -->
      <line x1={padding.left} y1={height - padding.bottom} x2={width - padding.right} y2={height - padding.bottom} class="axis-line" />

      <!-- Area under the curve -->
      <path d={areaString} fill="url(#chart-area-grad)" />

      <!-- The Line -->
      <path d={pathString} fill="none" stroke="var(--accent)" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" class="line-path" />

      <!-- Data Dots -->
      {#each points as pt}
        <circle cx={pt.x} cy={pt.y} r="4" fill="var(--bg)" stroke="var(--accent)" stroke-width="2" class="dot" />
      {/each}
    </svg>
  {/if}
</div>

<style>
  .chart-container {
    width: 100%;
    height: 100%;
    position: relative;
    background: var(--code-bg);
    border-radius: 8px;
    padding: 10px;
    box-sizing: border-box;
    border: 1px solid var(--border);
  }
  .no-data {
    display: flex;
    align-items: center;
    justify-content: center;
    height: 100%;
    color: var(--text);
    font-size: 14px;
  }
  svg {
    display: block;
    width: 100%;
    height: 100%;
    overflow: visible;
  }
  .grid-line {
    stroke: var(--border);
    stroke-dasharray: 4 4;
    stroke-width: 1;
  }
  .axis-line {
    stroke: var(--border);
    stroke-width: 2;
  }
  .axis-text {
    font-size: 11px;
    fill: var(--text);
    text-anchor: end;
    font-family: var(--mono);
  }
  .line-path {
    filter: drop-shadow(0 2px 8px rgba(170, 59, 255, 0.4));
  }
  .dot {
    transition: r 0.2s ease;
    cursor: pointer;
  }
  .dot:hover {
    r: 6;
  }
</style>
