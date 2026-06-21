/**
 * @file main.ts
 * @description Entry point for the Nunki Svelte 5 frontend application.
 * Responsibility: Bootstraps and mounts the Svelte application onto the DOM.
 */

// Import Svelte's modern component mounting API introduced in Svelte 5
import { mount } from 'svelte'

// Import global CSS styling/design system rules
import './app.css'

// Import the main layout component of our dashboard
import App from './App.svelte'

/**
 * Mounts the root component (`App`) to the DOM.
 * Svelte 5 uses the `mount` function instead of legacy `new App(...)` class initialization.
 * 
 * - `target`: Specifies the HTML element where the app should be rendered. 
 *             We select the element with ID 'app' and assert it exists with the non-null assertion operator `!`.
 */
const app = mount(App, {
  target: document.getElementById('app')!,
})

// Export the application instance to allow interaction if needed or for testing
export default app

