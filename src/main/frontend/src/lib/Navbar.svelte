<script lang="ts">
  /**
   * @component Navbar
   * @description Architecture Component: Application Navigation Header Layer.
   * Renders the top navigation header bar, coordinating the active workbenches state.
   * Coordinates dropdown menus, nested submenus, profile options, and global outside-click monitors.
   * 
   * Interactive Features:
   * - Responsive toggle menus: Workbenches dropdown, User dropdown.
   * - Nested Submenus: Values (List, Search, Tree Views) and Automation (Lua script panels).
   * - Outside Click Detection: Automatically closes menu popovers when clicking outside the navbar container.
   * 
   * System Requirements Satisfied:
   * - REQ-00025: Support for authentication interface (Login, Logout, Profile panels).
   * - REQ-00026: Offline build support/clean navigation (smooth client routes, no external CDN dependencies).
   * - TSK-00030.1: Create Navbar Component (modular navigation design).
   */

  import { onMount } from 'svelte';
  
  // Import SVG icons from lucide-svelte library
  import { 
    User as UserIcon,  // Repesents user profile controls
    Settings,          // Profile configurations
    LogIn,             // Authenticated sign-in triggers
    LogOut,            // Authenticated sign-out triggers
    Terminal,          // System logging terminal link
    Activity,          // Time-series trend chart indicator
    Tv,                // SVG Mimic Synoptic diagram indicator
    Sliders,           // Workbenches parent category icon
    ChevronDown,       // Dropdown expand arrow
    ChevronRight,      // Nested menu expand arrow
    List,              // Variable database lists
    Search,            // Tag search engine
    Network,           // Tree view node hierarchy
    Cpu,               // Automation section parent icon
    Code,              // Lua script editor trigger
    Database           // Address space values category
  } from '@lucide/svelte';

  // --- Component Props Schema ---
  // - `onSelect`: Callback function dispatched to propagate navigation actions to App.svelte.
  // - `currentTab`: String code representing the active visible workbench tab.
  // - `isLoggedIn`: Boolean indicating if user is authenticated.
  let { onSelect, currentTab, isLoggedIn = false }: { onSelect: (tab: string) => void; currentTab: string; isLoggedIn?: boolean } = $props();

  // --- Svelte 5 Local Runes States ---
  // Caches which main category dropdown is expanded ('workbenches', 'user', or null if closed)
  let openDropdown = $state<string | null>(null);
  
  // Caches which nested sub-category panel is expanded ('values', 'automation', or null if closed)
  let activeSubmenu = $state<string | null>(null);

  /**
   * Life Cycle Hook: `onMount`
   * Registers a global listener capturing mouse down clicks.
   * Compares the target target element against `.nav-item` tags. If the click occurred
   * outside, automatically collapses active menu elements.
   */
  onMount(() => {
    const handleOutsideClick = (e: MouseEvent) => {
      const target = e.target as HTMLElement;
      // Close dropdowns only if click target does not belong to the menu wrapper
      if (!target.closest('.nav-item')) {
        openDropdown = null;
        activeSubmenu = null;
      }
    };
    window.addEventListener('click', handleOutsideClick);
    
    // Svelte 5 return destructor: cleans up window event listeners to prevent memory leaks
    return () => window.removeEventListener('click', handleOutsideClick);
  });

  /**
   * Main dropdown toggle.
   * 
   * @param {string} menu - Dropdown code ('workbenches' or 'user').
   * @param {MouseEvent} event - Click trigger event used to restrict event propagation.
   */
  function toggleDropdown(menu: string, event: MouseEvent) {
    event.stopPropagation(); // Block window.click outside handler from firing
    if (openDropdown === menu) {
      openDropdown = null;
      activeSubmenu = null; // Close submenus on parent collapse
    } else {
      openDropdown = menu;
      activeSubmenu = null; // Reset submenus on switching menus
    }
  }

  /**
   * Menu choice click selector.
   * Triggers the navigation callback and collapses layout containers.
   * 
   * @param {string} tab - Selected tab identifier.
   * @param {MouseEvent} event - Click trigger event.
   */
  function handleSelect(tab: string, event: MouseEvent) {
    event.stopPropagation();
    onSelect(tab);
    openDropdown = null;
    activeSubmenu = null;
  }

  /**
   * Submenu toggle.
   * 
   * @param {string} submenu - Submenu code ('values' or 'automation').
   * @param {MouseEvent} event - Click trigger event.
   */
  function toggleSubmenu(submenu: string, event: MouseEvent) {
    event.stopPropagation();
    activeSubmenu = activeSubmenu === submenu ? null : submenu;
  }
</script>

<header class="top-navbar">
  <div class="navbar-container">
    
    <!-- ==========================================
         --- LEFT NAV: BRANDING & WORKBENCHES ---
         ========================================== -->
    <div class="left-section">
      <div class="brand">
        <!-- Graphic brand block utilizing CSS color gradients -->
        <div class="brand-logo">N</div>
        <span class="brand-name">Nunki Dashboard</span>
      </div>

      <nav class="nav-menu">
        <!-- WORKBENCHES DIRECTORY PANEL -->
        <div class="nav-item">
          <!-- Button with expanded status attributes mapped to accessibility standard tools -->
          <button 
            class="nav-btn" 
            class:active={openDropdown === 'workbenches'} 
            onclick={(e) => toggleDropdown('workbenches', e)}
            aria-expanded={openDropdown === 'workbenches'}
            aria-haspopup="true">
            <Sliders size={16} />
            <span>Workbenches</span>
            <ChevronDown size={12} class="arrow-icon" />
          </button>
          
          {#if openDropdown === 'workbenches'}
            <div class="dropdown-menu large-dropdown left-align">
              <!-- Logs Panel Workbench Selection -->
              <button class="dropdown-item" class:selected={currentTab === 'home'} onclick={(e) => handleSelect('home', e)}>
                <Terminal size={14} />
                <span>Logs</span>
              </button>
              
              <!-- Charts Panel Workbench Selection -->
              <button class="dropdown-item" class:selected={currentTab === 'timeseries'} onclick={(e) => handleSelect('timeseries', e)}>
                <Activity size={14} />
                <span>Charts</span>
              </button>
              
              <!-- Synoptics Panel Workbench Selection -->
              <button class="dropdown-item" class:selected={currentTab === 'diagrams'} onclick={(e) => handleSelect('diagrams', e)}>
                <Tv size={14} />
                <span>Synoptics</span>
              </button>
              
              <!-- VALUES SUB-NAV CONTROLS -->
              <div class="submenu-container">
                <button class="dropdown-item has-submenu" onclick={(e) => toggleSubmenu('values', e)}>
                  <Database size={14} />
                  <span>Values</span>
                  <ChevronRight size={12} class="submenu-arrow {activeSubmenu === 'values' ? 'rotated' : ''}" />
                </button>
                
                {#if activeSubmenu === 'values'}
                  <!-- Indented nesting submenu panel -->
                  <div class="submenu-panel">
                    <button class="dropdown-item" class:selected={currentTab === 'values-list'} onclick={(e) => handleSelect('values-list', e)}>
                      <List size={12} />
                      <span>List view</span>
                    </button>
                    <button class="dropdown-item" class:selected={currentTab === 'values-search'} onclick={(e) => handleSelect('values-search', e)}>
                      <Search size={12} />
                      <span>Search view</span>
                    </button>
                    <button class="dropdown-item" class:selected={currentTab === 'tree'} onclick={(e) => handleSelect('tree', e)}>
                      <Network size={12} />
                      <span>Tree view</span>
                    </button>
                  </div>
                {/if}
              </div>

              <!-- AUTOMATION SUB-NAV CONTROLS -->
              <div class="submenu-container">
                <button class="dropdown-item has-submenu" onclick={(e) => toggleSubmenu('automation', e)}>
                  <Cpu size={14} />
                  <span>Automation</span>
                  <ChevronRight size={12} class="submenu-arrow {activeSubmenu === 'automation' ? 'rotated' : ''}" />
                </button>
                
                {#if activeSubmenu === 'automation'}
                  <div class="submenu-panel">
                    <button class="dropdown-item" class:selected={currentTab === 'automation-lua'} onclick={(e) => handleSelect('automation-lua', e)}>
                      <Code size={12} />
                      <span>Lua scripting</span>
                    </button>
                  </div>
                {/if}
              </div>
            </div>
          {/if}
        </div>
      </nav>
    </div>

    <!-- ==========================================
         --- RIGHT NAV: PROFILE & AUTH CONTROLS ---
         ========================================== -->
    <div class="right-section">
      <nav class="nav-menu">
        <div class="nav-item">
          <!-- Button with expanded status attributes mapped to accessibility standard tools -->
          <button 
            class="nav-btn" 
            class:active={openDropdown === 'user'} 
            onclick={(e) => toggleDropdown('user', e)}
            aria-expanded={openDropdown === 'user'}
            aria-haspopup="true">
            <UserIcon size={16} />
            <span>User</span>
            <ChevronDown size={12} class="arrow-icon" />
          </button>
          
          {#if openDropdown === 'user'}
            <div class="dropdown-menu right-align">
              {#if !isLoggedIn}
                <!-- Log In Trigger -->
                <button class="dropdown-item" onclick={(e) => handleSelect('login', e)}>
                  <LogIn size={14} />
                  <span>Log in</span>
                </button>
              {:else}
                <!-- Profile details trigger -->
                <button class="dropdown-item" onclick={(e) => handleSelect('profile', e)}>
                  <Settings size={14} />
                  <span>Profile</span>
                </button>
                <!-- Log Out Trigger -->
                <button class="dropdown-item" onclick={(e) => handleSelect('logout', e)}>
                  <LogOut size={14} />
                  <span>Log out</span>
                </button>
              {/if}
            </div>
          {/if}
        </div>
      </nav>
    </div>
  </div>
</header>

<style>
  /* Header Container */
  .top-navbar {
    height: 56px;
    background: var(--code-bg);
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    position: sticky;
    top: 0;
    z-index: 100;
    box-sizing: border-box;
    width: 100%;
  }

  .navbar-container {
    width: 100%;
    max-width: 100%;
    padding: 0 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    box-sizing: border-box;
  }

  .left-section {
    display: flex;
    align-items: center;
    gap: 32px;
  }

  .right-section {
    display: flex;
    align-items: center;
  }

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  /* BRAND LOGO: CSS linear gradients with violet neon drop shadows */
  .brand-logo {
    width: 28px;
    height: 28px;
    background: linear-gradient(135deg, var(--accent), #7928ca);
    border-radius: 6px;
    color: white;
    font-weight: bold;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 15px;
    box-shadow: 0 2px 6px rgba(170, 59, 255, 0.2);
  }

  .brand-name {
    font-size: 16px;
    font-weight: 600;
    color: var(--text-h);
    letter-spacing: 0.5px;
  }

  .nav-menu {
    display: flex;
    gap: 8px;
  }

  .nav-item {
    position: relative;
  }

  /* Navigation Button Styling */
  .nav-btn {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 8px 14px;
    border: 1px solid transparent;
    background: transparent;
    color: var(--text);
    border-radius: 6px;
    font-size: 14px;
    font-weight: 500;
    cursor: pointer;
    transition: all 0.15s ease;
  }

  .nav-btn:hover {
    background: var(--accent-bg);
    color: var(--accent);
  }

  .nav-btn.active {
    background: var(--bg);
    border-color: var(--border);
    color: var(--text-h);
  }

  .nav-btn :global(.arrow-icon) {
    opacity: 0.7;
    transition: transform 0.2s;
  }

  /* Chevron flips 180 degrees when category is active */
  .nav-btn.active :global(.arrow-icon) {
    transform: rotate(180deg);
  }

  /* Dropdown context wrapper styling */
  .dropdown-menu {
    position: absolute;
    top: calc(100% + 4px);
    background: var(--bg);
    border: 1px solid var(--border);
    border-radius: 8px;
    padding: 6px;
    min-width: 160px;
    box-shadow: var(--shadow);
    display: flex;
    flex-direction: column;
    gap: 2px;
    animation: slideDown 0.15s cubic-bezier(0.16, 1, 0.3, 1);
  }

  .dropdown-menu.left-align {
    left: 0;
  }

  .dropdown-menu.right-align {
    right: 0;
  }

  .large-dropdown {
    min-width: 190px;
  }

  /* Entry slide down and fade in interpolation */
  @keyframes slideDown {
    from {
      opacity: 0;
      transform: translateY(-4px);
    }
    to {
      opacity: 1;
      transform: translateY(0);
    }
  }

  .dropdown-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    padding: 8px 12px;
    border: none;
    background: transparent;
    color: var(--text);
    border-radius: 6px;
    font-size: 13px;
    font-weight: 500;
    text-align: left;
    cursor: pointer;
    box-sizing: border-box;
    transition: all 0.15s ease;
  }

  .dropdown-item:hover {
    background: var(--code-bg);
    color: var(--text-h);
  }

  .dropdown-item.selected {
    background: var(--accent-bg);
    color: var(--accent);
  }

  .submenu-container {
    position: relative;
    display: flex;
    flex-direction: column;
  }

  .has-submenu {
    justify-content: space-between;
  }

  .has-submenu :global(.submenu-arrow) {
    opacity: 0.7;
    transition: transform 0.2s;
  }

  .has-submenu :global(.submenu-arrow.rotated) {
    transform: rotate(90deg);
  }

  /* Indented sub-category panels */
  .submenu-panel {
    background: var(--code-bg);
    border-radius: 6px;
    margin: 2px 4px;
    padding: 4px;
    display: flex;
    flex-direction: column;
    gap: 2px;
    box-shadow: inset 0 1px 3px rgba(0, 0, 0, 0.05);
  }

  .submenu-panel .dropdown-item {
    padding: 6px 12px 6px 28px;
    font-size: 12px;
  }
</style>
