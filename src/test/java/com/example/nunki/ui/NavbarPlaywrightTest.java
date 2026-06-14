package com.example.nunki.ui;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class NavbarPlaywrightTest {

    @LocalServerPort
    private int port;

    private Playwright playwright;
    private Browser browser;
    private BrowserContext context;
    private Page page;

    @BeforeAll
    void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
    }

    @AfterAll
    void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1920, 1080));
        page = context.newPage();
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void testNavbarWidthAndPosition() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // 1. Verify navbar is displayed
        Locator navbar = page.locator(".top-navbar");
        assertTrue(navbar.isVisible(), "Navbar should be visible");

        // 2. Verify navbar covers the full width of the screen (x=0 and width = viewport width)
        Object x = page.evaluate("() => document.querySelector('.top-navbar').getBoundingClientRect().x");
        Object width = page.evaluate("() => document.querySelector('.top-navbar').getBoundingClientRect().width");
        Object windowWidth = page.evaluate("() => window.innerWidth");

        assertEquals(0.0, ((Number) x).doubleValue(), 0.1, "Navbar should start at x=0");
        assertTrue(((Number) width).doubleValue() >= ((Number) windowWidth).doubleValue(),
                "Navbar width (" + width + ") should cover the screen width (" + windowWidth + ")");
    }

    @Test
    public void testNavbarItemsAccessibility() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Open Workbenches dropdown
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);

        // Verify primary workbench options
        assertTrue(page.isVisible("button:has-text('Logs')"), "Logs option should be visible");
        assertTrue(page.isVisible("button:has-text('Charts')"), "Charts option should be visible");
        assertTrue(page.isVisible("button:has-text('Synoptics')"), "Synoptics option should be visible");

        // Open Values submenu
        page.click("button:has-text('Values')");
        page.waitForTimeout(200);
        assertTrue(page.isVisible("button:has-text('List view')"), "List view should be visible");
        assertTrue(page.isVisible("button:has-text('Search view')"), "Search view should be visible");
        assertTrue(page.isVisible("button:has-text('Tree view')"), "Tree view should be visible");

        // Open Automation submenu
        page.click("button:has-text('Automation')");
        page.waitForTimeout(200);
        assertTrue(page.isVisible("button:has-text('Lua scripting')"), "Lua scripting should be visible");

        // Open User dropdown (closes Workbenches dropdown automatically)
        page.click("button:has-text('User')");
        page.waitForTimeout(200);
        assertTrue(page.isVisible("button:has-text('Log in')"), "Log in should be visible");
        assertTrue(page.isVisible("button:has-text('Log out')"), "Log out should be visible");
        assertTrue(page.isVisible("button:has-text('Profile')"), "Profile should be visible");
    }

    @Test
    public void testPingPongSystem() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Wait for WebSocket/STOMP connection to establish (indicated by 'LIVE' badge and enabled Send button)
        page.waitForSelector("button.btn-send:not([disabled])", new Page.WaitForSelectorOptions().setTimeout(15000));
        
        // Enter a unique ping message
        String uniqueMessage = "Playwright test ping value " + System.currentTimeMillis();
        page.fill("input[placeholder='Enter ping text']", uniqueMessage);

        // Click "Send Ping" button
        page.click("button.btn-send");

        // Wait for the pong response to be received and displayed in the Response Logs list
        Locator logItem = page.locator(".log-item >> text=" + uniqueMessage);
        logItem.waitFor(new Locator.WaitForOptions().setTimeout(10000));

        assertTrue(logItem.isVisible(), "Sent ping message should appear in Response Logs");
    }
}
