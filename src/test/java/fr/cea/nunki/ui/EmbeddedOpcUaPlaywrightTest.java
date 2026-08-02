package fr.cea.nunki.ui;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @requirement REQ-00021 Display dynamic SVG diagrams and synoptics
 * @feature FR-00010 UI Browser Emulation Tests against Integrated OPC-UA Server
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
    "opcua.embedded-server.enabled=true",
    "opcua.embedded-server.port=12689",
    "opcua.embedded-server.warmup-delay-seconds=0",
    "opcua.client.endpoint-url=opc.tcp://localhost:12689/opcua"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EmbeddedOpcUaPlaywrightTest {

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
    @DisplayName("Verify OPC-UA Tree View browsing and interaction with real embedded server")
    public void testOpcUaTreeInteraction() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Open Workbenches -> Values -> Tree view
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);
        page.click("button:has-text('Values')");
        page.waitForTimeout(200);
        page.click("button:has-text('Tree view')");

        // Verify Tree header
        Locator header = page.locator("h1:has-text('OPC-UA Address Space')");
        assertTrue(header.isVisible(), "Tree view header should be visible");

        // Verify Data folder is displayed in the tree
        Locator dataFolder = page.locator(".tree-node .name:has-text('Data')");
        dataFolder.waitFor(new Locator.WaitForOptions().setTimeout(10000));
        assertTrue(dataFolder.isVisible(), "Data folder should be visible in tree");

        // Verify LargeTree folder is displayed in the tree
        Locator largeTreeFolder = page.locator(".tree-node .name:has-text('LargeTree')");
        assertTrue(largeTreeFolder.isVisible(), "LargeTree folder should be visible in tree");

        // Verify CounterControl folder is displayed in the tree
        Locator counterFolder = page.locator(".tree-node .name:has-text('CounterControl')");
        assertTrue(counterFolder.isVisible(), "CounterControl folder should be visible in tree");
    }

    @Test
    @DisplayName("Verify Synoptics mimic view rendering with real embedded server")
    public void testSynopticsMimicView() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Open Workbenches -> Synoptics
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);
        page.click("button:has-text('Synoptics')");

        // Verify Process Synoptics header
        Locator header = page.locator("h1:has-text('Process Synoptics')");
        assertTrue(header.isVisible(), "Process Synoptics header should be visible");

        // Verify synoptic mimic SVG canvas container exists
        Locator mimicContainer = page.locator(".synoptic-container");
        assertTrue(mimicContainer.isVisible(), "Synoptic mimic SVG container should be visible");
    }
}
