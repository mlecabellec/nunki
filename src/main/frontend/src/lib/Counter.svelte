<script lang="ts">
  /**
   * @component Counter
   * @description A simple counter component that showcases Svelte 5 reactive state.
   */

  // Declare reactive state using the Svelte 5 `$state` rune.
  // Changes to `count` will automatically trigger DOM updates wherever `count` is referenced.
  let count: number = $state(0)

  /**
   * Increments the count by 1.
   * This is a standard event handler function.
   */
  const increment = () => {
    count += 1
  }
</script>

<!-- 
  A standard button element styled as a counter.
  - `onclick`: Uses the Svelte 5 event listener attribute (replaces legacy `on:click`).
  - `{count}`: Standard Svelte text interpolation to output reactive state.
-->
<button type="button" class="counter" onclick={increment}>
  Count is {count}
</button>

