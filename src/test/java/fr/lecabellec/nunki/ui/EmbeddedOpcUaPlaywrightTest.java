package fr.lecabellec.nunki.ui;

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

    @Test
    @DisplayName("Verify UI login, tree value editing (write), and method invocation (call)")
    public void testUiLoginAndValueEditAndMethodInvoke() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // 1. Open Account modal and perform login
        page.click("button:has-text('Account')");
        page.waitForTimeout(200);
        page.click("button:has-text('Sign In')");
        page.waitForTimeout(200);

        // Verify login modal fields
        Locator loginModal = page.locator(".modal");
        assertTrue(loginModal.isVisible(), "Login modal should be visible");

        // Fill username and password and submit
        page.fill("input[placeholder='Username']", "admin");
        page.fill("input[placeholder='Password']", "admin");
        page.click("button.btn-primary:has-text('Login')");
        page.waitForTimeout(500);

        // 2. Open Workbenches -> Values -> Tree view
        page.click("button:has-text('Workbenches')");
        page.waitForTimeout(200);
        page.click("button:has-text('Values')");
        page.waitForTimeout(200);
        page.click("button:has-text('Tree view')");

        // Verify Data folder is displayed and expanded
        Locator dataFolder = page.locator(".tree-node .name:has-text('Data')");
        dataFolder.waitFor(new Locator.WaitForOptions().setTimeout(10000));
        assertTrue(dataFolder.isVisible(), "Data folder should be visible");

        // Verify Edit button is visible for variable nodes when logged in
        Locator editButton = page.locator(".tree-node:has-text('MyInt') .btn-action.edit");
        editButton.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        assertTrue(editButton.isVisible(), "Edit button should be visible on MyInt variable node");

        // 3. Edit MyInt value via UI
        editButton.click();
        page.waitForTimeout(200);

        Locator editInput = page.locator(".tree-node:has-text('MyInt') .edit-input");
        assertTrue(editInput.isVisible(), "Inline edit input should be visible");
        editInput.fill("123");
        page.click(".tree-node:has-text('MyInt') .btn-save");
        page.waitForTimeout(1000);

        // Verify value saved in UI
        Locator updatedValue = page.locator(".tree-node:has-text('MyInt') .value");
        assertEquals("123", updatedValue.textContent().trim(), "Updated value 123 should be displayed");

        // 4. Invoke Method ToggleSwitch via UI
        Locator invokeButton = page.locator(".tree-node:has-text('ToggleSwitch') .btn-action.invoke");
        assertTrue(invokeButton.isVisible(), "Invoke button should be visible on ToggleSwitch method node");
        invokeButton.click();
        page.waitForTimeout(1500);

        // Verify invocation result badge
        Locator badge = page.locator(".tree-node:has-text('ToggleSwitch') .invoke-badge");
        assertTrue(badge.isVisible(), "Result badge should be visible after invocation");
        assertEquals("True", badge.textContent().trim(), "Invocation badge should display 'True'");
    }

    @Test
    @DisplayName("Verify STOMP Ping/Pong interactive message exchange in UI")
    public void testStompPingPongInUi() {
        String url = "http://localhost:" + port;
        page.navigate(url);

        // Open Dashboard home
        page.click("button:has-text('Dashboard')");
        page.waitForTimeout(500);

        // Locate Ping input and send button
        Locator pingInput = page.locator("input[placeholder='Enter ping message...']");
        pingInput.waitFor(new Locator.WaitForOptions().setTimeout(10000));
        assertTrue(pingInput.isVisible(), "Ping text input should be visible");

        pingInput.fill("Hello STOMP Test");
        page.click("button:has-text('Send Ping')");
        page.waitForTimeout(1500);

        // Verify Pong response in message list
        Locator pongItem = page.locator(".log-item:has-text('Hello STOMP Test')");
        pongItem.waitFor(new Locator.WaitForOptions().setTimeout(5000));
        assertTrue(pongItem.isVisible(), "Pong response matching payload should be visible in UI logs");
    }
}

