package fr.lecabellec.nunki.ui;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 UI Browser Emulation Tests without OPC-UA Server (Graceful Fallback)
 * @task TSK-20260311-005 End-to-End Playwright UI Verification
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard
 */

import com.microsoft.playwright.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "opcua.embedded-server.enabled=false",
    "opcua.client.endpoint-url=opc.tcp://localhost:4849/opcua"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class DisabledOpcUaPlaywrightTest {

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
    @DisplayName("Verify Web UI loads cleanly without OPC-UA server and shows connection status UI elements")
    public void testUiWithoutOpcUaServer() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Verify page navbar header is rendered
        Locator navbarBrand = page.locator(".navbar-brand, header");
        assertTrue(navbarBrand.isVisible(), "Navbar brand/header should be visible");

        // Open Workbenches -> Values -> Tree view
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);
        page.click("button:has-text('Values')");
        page.waitForTimeout(200);
        page.click("button:has-text('Tree view')");

        // Verify Tree view header renders without crash
        Locator header = page.locator("h1:has-text('OPC-UA Address Space')");
        assertTrue(header.isVisible(), "Tree view header should be visible even when server is offline");

        // Open Workbenches -> Synoptics
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);
        page.click("button:has-text('Synoptics')");

        Locator synopticHeader = page.locator("h1:has-text('Process Synoptics')");
        assertTrue(synopticHeader.isVisible(), "Synoptics page should be visible even when server is offline");
    }
}
