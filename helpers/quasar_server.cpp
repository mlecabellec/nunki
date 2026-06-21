/**
 * @file quasar_server.cpp
 * @brief Standalone OPC UA helper server for the Nunki integration test environment.
 *
 * This executable exposes a rich OPC UA address space used by the Nunki Java client
 * to exercise browsing, subscription, and method-call code paths.
 *
 * Address space structure
 * =======================
 *   Root (NamedObject)
 *   ├── LargeTree          – 1000-node, 5-level synthetic tree covering all OPC-UA-visible types
 *   ├── CounterControl     – Controllable integer with Increment / Decrement methods
 *   ├── FastCounters       – Branch of high-frequency ticking counters (1 Hz – 100 Hz)
 *   └── Data               – Legacy compatibility nodes (MyInt, MySwitch, ToggleSwitch)
 *
 * Node count breakdown for LargeTree
 * ------------------------------------
 *   Level 1 (root of branch) :   1  (LargeTree itself)
 *   Level 2                  :  10  objects
 *   Level 3                  :  50  objects  (5 per L2 node)
 *   Level 4                  : 400  objects  (8 per L3 node)
 *   Level 5                  : 539  leaf nodes distributed round-robin across L4
 *                                   Type rotation: NamedInteger<int32_t>, NamedBoolean,
 *                                                  NamedFloatingPoint<double>, NamedString,
 *                                                  NamedMethod (no-op placeholder)
 *   Total                    : 1 + 10 + 50 + 400 + 539 = 1 000 nodes  ✓
 *
 * OPC UA type mapping (defined in OpcUaServerService.cpp)
 * --------------------------------------------------------
 *   NamedInteger<T>       → UA Variable (Int32 / Int64 / …)
 *   NamedBoolean          → UA Variable (Boolean)
 *   NamedFloatingPoint<T> → UA Variable (Double / Float)
 *   NamedString           → UA Variable (String)
 *   NamedMethod           → UA Method node (JSON in / JSON out)
 *   NamedObject           → UA Object (folder) node
 *
 * Counter update strategy
 * -----------------------
 * Counters are updated cooperatively inside a thin wrapper around the server's
 * built-in "run" method (which calls UA_Server_run_iterate).  A 15-second
 * warm-up delay ensures the client can browse and subscribe before data starts
 * moving.  High-frequency counters are down-sampled to ≤ 10 Hz OPC UA writes
 * to avoid network packet floods; their increment step is scaled proportionally
 * to preserve the logical tick rate.
 *
 * @compliance [CS-0010.34] No auto keyword – explicit types everywhere.
 * @compliance [CS-0010.37] Bounded loops; no unbounded recursion in main logic.
 * @compliance [CS-0010.06] All heap objects via shared_ptr / factory create().
 * @compliance [CS-0010.45] All public symbols documented with Doxygen.
 * @feature    [TSK-20260311-005] OPC UA Address Space Mocking.
 */

#include "quasar/named/NamedObject.hpp"
#include "quasar/named/NamedInteger.hpp"
#include "quasar/named/NamedBoolean.hpp"
#include "quasar/named/NamedFloatingPoint.hpp"
#include "quasar/named/NamedString.hpp"
#include "quasar/named/NamedMethod.hpp"
#include "quasar/opcua/OpcUaServerService.hpp"

#include <chrono>
#include <csignal>
#include <cstdint>
#include <functional>
#include <iostream>
#include <string>
#include <thread>
#include <vector>

using namespace quasar::named;
using namespace quasar::opcua;

// ---------------------------------------------------------------------------
// Global termination flag – written by the signal handler, read by the main
// loop.  Declared volatile to prevent the compiler from caching the value in
// a register across loop iterations.
// ---------------------------------------------------------------------------
static volatile bool g_running = true; // NOLINT(cppcoreguidelines-avoid-non-const-global-variables)

/**
 * @brief POSIX signal handler for graceful shutdown.
 *
 * Sets the global termination flag so that the main loop exits cleanly
 * the next time it evaluates the condition.
 *
 * @param signum The received signal number (SIGINT or SIGTERM).
 * @feature [TSK-20260311-005] Clean server shutdown sequence.
 */
static void signalHandler(int signum) {
    std::cout << "[quasar_server] Signal " << signum << " received – shutting down.\n";
    g_running = false;
}

// ---------------------------------------------------------------------------
// CounterConfig
// ---------------------------------------------------------------------------

/**
 * @struct CounterConfig
 * @brief Configuration and runtime state for a single ticking counter node.
 *
 * Each entry in the FastCounters branch has its own tick interval and an
 * increment step that ensures the logical frequency is preserved even when
 * the OPC UA write rate is capped.
 *
 * Fields
 * ------
 * @param node              Shared pointer to the underlying NamedInteger node.
 * @param interval          Wall-clock duration between successive OPC UA writes.
 * @param incrementPerTick  Value added to the node on each write.  For counters
 *                          faster than 10 Hz, this is > 1 to compensate for the
 *                          down-sampling.
 * @param lastTick          Timestamp of the most recent write; used to gate the
 *                          next write without a dedicated timer thread.
 */
struct CounterConfig {
    std::shared_ptr<NamedInteger<int32_t>>   node;
    std::chrono::microseconds                interval;
    int32_t                                  incrementPerTick;
    std::chrono::steady_clock::time_point    lastTick;
};

// ---------------------------------------------------------------------------
// buildLargeTree – fills the LargeTree branch with exactly 1 000 nodes
// ---------------------------------------------------------------------------

/**
 * @brief Constructs the five-level, 1 000-node synthetic LargeTree branch.
 *
 * All OPC-UA-visible leaf types are distributed in round-robin order across
 * the 539 Level-5 slots:
 *   slot % 5 == 0 → NamedInteger<int32_t>
 *   slot % 5 == 1 → NamedBoolean
 *   slot % 5 == 2 → NamedFloatingPoint<double>
 *   slot % 5 == 3 → NamedString
 *   slot % 5 == 4 → NamedMethod (no-op placeholder, mapped as UA Method node)
 *
 * Node count:
 *   L1 (largeTreeRoot) :   1
 *   L2                 :  10
 *   L3                 :  50
 *   L4                 : 400
 *   L5                 : 539
 *   Total              : 1 000  ✓
 *
 * @param root  The top-level NamedObject to attach the branch to.
 * @compliance [CS-0010.34] No auto; all types are explicit.
 * @compliance [CS-0010.37] Loops bounded by compile-time constants.
 */
static void buildLargeTree(std::shared_ptr<NamedObject> root) {
    // ------------------------------------------------------------------
    // Level 1 – branch root
    // ------------------------------------------------------------------
    std::shared_ptr<NamedObject> largeTreeRoot =
        NamedObject::create("LargeTree", root);

    // ------------------------------------------------------------------
    // Level 2 – 10 intermediate objects
    // ------------------------------------------------------------------
    constexpr int L2_COUNT = 10;
    std::vector<std::shared_ptr<NamedObject>> level2;
    level2.reserve(L2_COUNT);
    for (int i = 0; i < L2_COUNT; ++i) {
        std::shared_ptr<NamedObject> node =
            NamedObject::create("L2_" + std::to_string(i), largeTreeRoot);
        level2.push_back(node);
    }

    // ------------------------------------------------------------------
    // Level 3 – 50 intermediate objects (5 per L2 node)
    // ------------------------------------------------------------------
    constexpr int L3_COUNT = 50;
    std::vector<std::shared_ptr<NamedObject>> level3;
    level3.reserve(L3_COUNT);
    for (int i = 0; i < L3_COUNT; ++i) {
        // Parent index: integer division maps 5 consecutive L3 nodes to
        // the same L2 parent.
        std::shared_ptr<NamedObject> parent = level2[i / 5];
        std::shared_ptr<NamedObject> node =
            NamedObject::create("L3_" + std::to_string(i), parent);
        level3.push_back(node);
    }

    // ------------------------------------------------------------------
    // Level 4 – 400 intermediate objects (8 per L3 node)
    // ------------------------------------------------------------------
    constexpr int L4_COUNT = 400;
    std::vector<std::shared_ptr<NamedObject>> level4;
    level4.reserve(L4_COUNT);
    for (int i = 0; i < L4_COUNT; ++i) {
        // Parent index: 8 consecutive L4 nodes share an L3 parent.
        std::shared_ptr<NamedObject> parent = level3[i / 8];
        std::shared_ptr<NamedObject> node =
            NamedObject::create("L4_" + std::to_string(i), parent);
        level4.push_back(node);
    }

    // ------------------------------------------------------------------
    // Level 5 – 539 leaf nodes, distributed round-robin across L4
    // ------------------------------------------------------------------
    // 539 / 5 = 107 full rotations + 4 remainder, giving each type between
    // 107 and 108 instances – a balanced distribution.
    constexpr int L5_COUNT = 539;
    for (int i = 0; i < L5_COUNT; ++i) {
        // Spread leaves evenly across the 400 L4 parents.
        std::shared_ptr<NamedObject> parent = level4[static_cast<std::size_t>(i) % L4_COUNT];
        std::string name = "L5_" + std::to_string(i);

        // Round-robin type assignment: all four OPC-UA variable types plus
        // one NamedMethod to exercise the full range of server capabilities.
        int typeSlot = i % 5;
        switch (typeSlot) {
            case 0:
                // NamedInteger<int32_t> → OPC UA Int32 variable
                NamedInteger<int32_t>::create(name, static_cast<int32_t>(i), parent);
                break;

            case 1:
                // NamedBoolean → OPC UA Boolean variable
                NamedBoolean::create(name, (i % 2 == 0), parent);
                break;

            case 2:
                // NamedFloatingPoint<double> → OPC UA Double variable
                NamedFloatingPoint<double>::create(
                    name, static_cast<double>(i) * 0.1, parent);
                break;

            case 3:
                // NamedString → OPC UA String variable
                NamedString::create(name, "node_" + std::to_string(i), parent);
                break;

            default:
                // NamedMethod → OPC UA Method node (no-op placeholder)
                // The lambda captures nothing – it simply returns a null result.
                NamedMethod::create(
                    name,
                    [](std::shared_ptr<NamedObject> /*owner*/,
                       std::shared_ptr<NamedObject> /*args*/)
                        -> std::shared_ptr<NamedObject> {
                        // No-op placeholder method.  The server will expose
                        // this as a callable UA Method; it just returns null.
                        return nullptr;
                    },
                    parent);
                break;
        }
    }

    std::cout << "[quasar_server] LargeTree built: "
              << "1 + " << L2_COUNT << " + " << L3_COUNT << " + "
              << L4_COUNT << " + " << L5_COUNT << " = 1 000 nodes.\n";
}

// ---------------------------------------------------------------------------
// buildCounterControl – single integer with Increment / Decrement methods
// ---------------------------------------------------------------------------

/**
 * @brief Creates the CounterControl branch exposing a mutable integer.
 *
 * Structure:
 *   CounterControl  (NamedObject / UA Object)
 *   ├── CounterValue  (NamedInteger<int32_t> / UA Int32 variable)
 *   ├── Increment     (NamedMethod / UA Method – increments CounterValue by 1)
 *   └── Decrement     (NamedMethod / UA Method – decrements CounterValue by 1)
 *
 * The two methods return the updated value wrapped in a NamedInteger result
 * so the caller can inspect the new state without a separate read.
 *
 * @param root  The top-level NamedObject to attach the branch to.
 * @compliance [CS-0010.06] All nodes created via factory create().
 * @feature    [TSK-20260311-005] Interactive counter with named methods.
 */
static void buildCounterControl(std::shared_ptr<NamedObject> root) {
    // The branch container – exposed as a UA Object / folder.
    std::shared_ptr<NamedObject> counterControl =
        NamedObject::create("CounterControl", root);

    // The mutable integer node.  Both methods capture a shared_ptr to it so
    // they can read and modify its value from inside the UA Method callback.
    std::shared_ptr<NamedInteger<int32_t>> counterValue =
        NamedInteger<int32_t>::create("CounterValue", 0, counterControl);

    // ------------------------------------------------------------------
    // Increment method – adds 1 to CounterValue and returns the result.
    // ------------------------------------------------------------------
    NamedMethod::create(
        "Increment",
        [counterValue](std::shared_ptr<NamedObject> /*owner*/,
                       std::shared_ptr<NamedObject> /*args*/)
            -> std::shared_ptr<NamedObject> {
            int32_t oldValue = counterValue->value();
            int32_t newValue = oldValue + 1;
            counterValue->setValue(newValue);
            std::cout << "[CounterControl] Increment: "
                      << oldValue << " → " << newValue << "\n";
            // Return the new value as a NamedInteger result object so the
            // caller can inspect it without a second read-request.
            return NamedInteger<int32_t>::create("Result", newValue, nullptr);
        },
        counterControl);

    // ------------------------------------------------------------------
    // Decrement method – subtracts 1 from CounterValue and returns the result.
    // ------------------------------------------------------------------
    NamedMethod::create(
        "Decrement",
        [counterValue](std::shared_ptr<NamedObject> /*owner*/,
                       std::shared_ptr<NamedObject> /*args*/)
            -> std::shared_ptr<NamedObject> {
            int32_t oldValue = counterValue->value();
            int32_t newValue = oldValue - 1;
            counterValue->setValue(newValue);
            std::cout << "[CounterControl] Decrement: "
                      << oldValue << " → " << newValue << "\n";
            return NamedInteger<int32_t>::create("Result", newValue, nullptr);
        },
        counterControl);

    std::cout << "[quasar_server] CounterControl branch ready "
              << "(CounterValue / Increment / Decrement).\n";
}

// ---------------------------------------------------------------------------
// buildFastCounters – branch of ticking counters from 1 Hz to 100 Hz
// ---------------------------------------------------------------------------

/**
 * @brief Creates the FastCounters branch and returns the counter descriptors.
 *
 * Each counter is an NamedInteger<int32_t> node whose value is incremented
 * periodically inside the server's cooperative run loop.  The branch structure is:
 *
 *   FastCounters  (NamedObject / UA Object)
 *   ├── Counter_1Hz    – increments by 1 every 1 000 ms
 *   ├── Counter_2Hz    – increments by 1 every   500 ms
 *   ├── Counter_5Hz    – increments by 1 every   200 ms
 *   ├── Counter_10Hz   – increments by 1 every   100 ms
 *   ├── Counter_20Hz   – increments by 2 every   100 ms  (down-sampled, ×2 step)
 *   ├── Counter_50Hz   – increments by 5 every   100 ms  (down-sampled, ×5 step)
 *   └── Counter_100Hz  – increments by 10 every  100 ms  (down-sampled, ×10 step)
 *
 * Down-sampling rationale: OPC UA write calls are cheap but not free.  Counters
 * above 10 Hz would generate more than 100 notifications per second on a single
 * subscription, which is unnecessary for test purposes.  We cap the write rate at
 * 10 Hz and scale the increment step so the logical tick frequency is preserved in
 * the value progression.
 *
 * @param root  The top-level NamedObject to attach the branch to.
 * @return      A vector of CounterConfig structs ready to be driven by the run loop.
 * @compliance  [CS-0010.34] No auto; all types explicit.
 */
static std::vector<CounterConfig>
buildFastCounters(std::shared_ptr<NamedObject> root) {
    std::shared_ptr<NamedObject> fastCounters =
        NamedObject::create("FastCounters", root);

    // Each entry defines: { frequency in Hz, display name suffix }
    // The suffix is appended to "Counter_" to form the node name.
    struct FreqSpec {
        double      frequencyHz;
        std::string suffix;
    };

    // Seven counter tiers spanning three orders of magnitude.
    const std::vector<FreqSpec> specs = {
        {   1.0,   "1Hz"  },
        {   2.0,   "2Hz"  },
        {   5.0,   "5Hz"  },
        {  10.0,  "10Hz"  },
        {  20.0,  "20Hz"  },
        {  50.0,  "50Hz"  },
        { 100.0, "100Hz"  },
    };

    // Maximum OPC UA write rate – counters faster than this are down-sampled.
    constexpr double MAX_WRITE_HZ = 10.0;

    std::vector<CounterConfig> counters;
    counters.reserve(specs.size());

    for (const FreqSpec& spec : specs) {
        // Create the OPC UA variable node.
        std::string nodeName = "Counter_" + spec.suffix;
        std::shared_ptr<NamedInteger<int32_t>> node =
            NamedInteger<int32_t>::create(nodeName, 0, fastCounters);

        // Compute the effective write interval and increment step.
        std::chrono::microseconds interval;
        int32_t incrementPerTick = 1;

        if (spec.frequencyHz <= MAX_WRITE_HZ) {
            // Write at the natural frequency – no down-sampling needed.
            int64_t periodUs =
                static_cast<int64_t>(1'000'000.0 / spec.frequencyHz);
            interval = std::chrono::microseconds(periodUs);
        } else {
            // Down-sample to MAX_WRITE_HZ; scale the step so that the
            // cumulative value progression matches the nominal frequency.
            //   e.g., 100 Hz capped to 10 Hz → step = 100/10 = 10
            interval = std::chrono::microseconds(
                static_cast<int64_t>(1'000'000.0 / MAX_WRITE_HZ));
            incrementPerTick =
                static_cast<int32_t>(spec.frequencyHz / MAX_WRITE_HZ);
        }

        CounterConfig cfg;
        cfg.node             = node;
        cfg.interval         = interval;
        cfg.incrementPerTick = incrementPerTick;
        cfg.lastTick         = std::chrono::steady_clock::now();
        counters.push_back(std::move(cfg));

        std::cout << "[quasar_server] FastCounter '" << nodeName << "' – "
                  << "write interval: "
                  << std::chrono::duration_cast<std::chrono::milliseconds>(interval).count()
                  << " ms, step: " << incrementPerTick << "\n";
    }

    return counters;
}

// ---------------------------------------------------------------------------
// buildLegacyData – backward-compatible nodes present before refactoring
// ---------------------------------------------------------------------------

/**
 * @brief Creates the legacy Data branch retained for backward compatibility.
 *
 * This branch predates the LargeTree and CounterControl additions.  It is kept
 * to avoid breaking any client code that already subscribes to these nodes.
 *
 *   Data           (NamedObject / UA Object)
 *   ├── MyInt      (NamedInteger<int32_t>) – static integer, initial value 42
 *   ├── MySwitch   (NamedBoolean)          – toggleable flag
 *   └── ToggleSwitch (NamedMethod)         – flips MySwitch; returns new state as string
 *
 * @param root  The top-level NamedObject to attach the branch to.
 * @compliance [CS-0010.06] All nodes via factory create().
 */
static void buildLegacyData(std::shared_ptr<NamedObject> root) {
    std::shared_ptr<NamedObject> data = NamedObject::create("Data", root);

    // Static integer – can be written directly by the OPC UA client.
    NamedInteger<int32_t>::create("MyInt", 42, data);

    // Mutable boolean flag – toggled by the ToggleSwitch method below.
    std::shared_ptr<NamedBoolean> mySwitch =
        NamedBoolean::create("MySwitch", false, data);

    // ToggleSwitch: flips MySwitch and returns the new state as a string.
    NamedMethod::create(
        "ToggleSwitch",
        [mySwitch](std::shared_ptr<NamedObject> /*owner*/,
                   std::shared_ptr<NamedObject> /*args*/)
            -> std::shared_ptr<NamedObject> {
            bool newState = !mySwitch->booleanValue();
            mySwitch->setValue(newState);
            std::string stateStr = newState ? "True" : "False";
            std::cout << "[Data] ToggleSwitch: MySwitch = " << stateStr << "\n";
            return NamedString::create("Result", stateStr, nullptr);
        },
        data);

    std::cout << "[quasar_server] Legacy Data branch ready "
              << "(MyInt / MySwitch / ToggleSwitch).\n";
}

// ---------------------------------------------------------------------------
// installRunWrapper – replaces the server's built-in "run" method with one
// that also drives the FastCounters tick logic.
// ---------------------------------------------------------------------------

/**
 * @brief Wraps the server's built-in "run" method to inject counter updates.
 *
 * The OpcUaServerService exposes a NamedMethod called "run" that calls
 * UA_Server_run_iterate.  We detach that method from the server object and
 * replace it with a new lambda that:
 *   1. Checks whether the warm-up delay has elapsed.
 *   2. If so, iterates over all CounterConfig entries and increments the ones
 *      whose tick interval has expired.
 *   3. Calls the original "run" method to drive the OPC UA network stack.
 *
 * The counters vector is moved into the closure so no external ownership is
 * retained.
 *
 * @param server          The OpcUaServerService instance to patch.
 * @param counters        The list of counter descriptors to tick (moved in).
 * @param startupTime     The time point at which the server was created;
 *                        used to implement the warm-up delay.
 * @param warmupDelay     Duration to wait before counters start ticking.
 * @compliance [CS-0010.06] NamedMethod created via factory.
 * @compliance [CS-0010.34] No auto; explicit types.
 */
static void installRunWrapper(
    std::shared_ptr<OpcUaServerService>     server,
    std::vector<CounterConfig>              counters,
    std::chrono::steady_clock::time_point   startupTime,
    std::chrono::seconds                    warmupDelay)
{
    // Detach the existing built-in "run" child from the server object so we
    // can re-add a wrapper with the same name.
    std::shared_ptr<NamedObject>  oldRunObj = server->getChild("run");
    std::shared_ptr<NamedMethod>  oldRun    =
        std::dynamic_pointer_cast<NamedMethod>(oldRunObj);

    if (oldRun) {
        // Detaching by setting parent to nullptr removes it from the server's
        // children list.  We keep the shared_ptr alive in the closure below
        // so the lambda can delegate to it.
        oldRun->setParent(nullptr);
        std::cout << "[quasar_server] Original 'run' method detached.\n";
    } else {
        std::cerr << "[quasar_server] Warning: could not find built-in 'run' method.\n";
    }

    // Replace with the wrapper.  counters and oldRun are moved/captured into
    // the mutable lambda to avoid unnecessary copies.
    NamedMethod::create(
        "run",
        [oldRun,
         startupTime,
         warmupDelay,
         counters = std::move(counters)](
            std::shared_ptr<NamedObject> /*owner*/,
            std::shared_ptr<NamedObject> /*args*/) mutable
            -> std::shared_ptr<NamedObject>
        {
            std::chrono::steady_clock::time_point now =
                std::chrono::steady_clock::now();

            // Only start ticking once the warm-up delay has elapsed.  This
            // gives the Nunki client enough time to browse the address space
            // and establish its subscriptions before values begin changing.
            if (now - startupTime >= warmupDelay) {
                for (std::size_t idx = 0; idx < counters.size(); ++idx) {
                    CounterConfig& cfg = counters[idx];

                    if (now - cfg.lastTick >= cfg.interval) {
                        // Advance the counter value.
                        int32_t current = cfg.node->value();
                        cfg.node->setValue(current + cfg.incrementPerTick);

                        // Advance lastTick by exactly one interval to keep the
                        // schedule in phase.  If we have fallen behind by more
                        // than one interval (e.g. after a hiccup) we snap to
                        // now to avoid a burst of catch-up ticks.
                        cfg.lastTick += cfg.interval;
                        if (now - cfg.lastTick > cfg.interval) {
                            cfg.lastTick = now;
                        }
                    }
                }
            }

            // Delegate to the original UA_Server_run_iterate wrapper.
            if (oldRun) {
                oldRun->execute(nullptr);
            }
            return nullptr;
        },
        server);

    std::cout << "[quasar_server] Run wrapper installed "
              << "(warm-up: " << warmupDelay.count() << " s).\n";
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

/**
 * @brief Entry point for the Quasar OPC UA helper server.
 *
 * Execution flow:
 *   1. Register signal handlers for SIGINT and SIGTERM.
 *   2. Build the address space (LargeTree, CounterControl, FastCounters, Data).
 *   3. Create the OpcUaServerService and bind the root object.
 *   4. Wrap the server's "run" method to inject counter-tick logic.
 *   5. Start the server; set a responsive 10 ms cycle time.
 *   6. Block on a 1-second sleep loop until a stop signal is received.
 *   7. Request a graceful shutdown and exit.
 *
 * @return 0 on success.
 * @compliance [CS-0010.34] No auto; explicit return type.
 * @compliance [CS-0010.37] Main loop bounded by the g_running flag.
 * @feature    [TSK-20260311-005] OPC UA Address Space Mocking.
 */
int main() {
    // Register POSIX signal handlers so that Ctrl-C and SIGTERM both trigger
    // a clean shutdown through the g_running flag.
    std::signal(SIGINT,  signalHandler);
    std::signal(SIGTERM, signalHandler);

    std::cout << "[quasar_server] Starting Quasar OPC UA Server…\n";

    // ------------------------------------------------------------------
    // Build the address space
    // ------------------------------------------------------------------
    std::shared_ptr<NamedObject> root = NamedObject::create("Root");

    buildLargeTree(root);
    buildCounterControl(root);
    std::vector<CounterConfig> counters = buildFastCounters(root);
    buildLegacyData(root);

    // ------------------------------------------------------------------
    // Create and configure the server service
    // ------------------------------------------------------------------
    std::shared_ptr<OpcUaServerService> server =
        OpcUaServerService::create("OpcUaServer");
    server->setPort(4840);
    server->setRootObject(root);

    // Record the moment the server object was created; used to implement the
    // counter warm-up delay.
    std::chrono::steady_clock::time_point startupTime =
        std::chrono::steady_clock::now();

    // Inject the counter-tick logic into the server's run loop.
    // 15 seconds of warm-up gives the Nunki client ample time to browse the
    // 1 000-node tree and register all subscriptions.
    installRunWrapper(
        server,
        std::move(counters),
        startupTime,
        std::chrono::seconds(15));

    // ------------------------------------------------------------------
    // Start the server
    // ------------------------------------------------------------------
    server->start();

    // Reduce the cycle time to 10 ms for responsive network event handling
    // (the OpcUaServerService::start() sets 1 ms by default; we relax it to
    // balance CPU usage vs. latency).
    server->setCycleTime(std::chrono::milliseconds(10));

    std::cout << "[quasar_server] Server is running on port 4840.\n"
              << "[quasar_server] Press Ctrl-C or send SIGTERM to stop.\n";

    // ------------------------------------------------------------------
    // Main supervision loop – sleeps 1 second at a time
    // ------------------------------------------------------------------
    while (g_running && server->isRunning()) {
        std::this_thread::sleep_for(std::chrono::seconds(1));
    }

    // ------------------------------------------------------------------
    // Graceful shutdown
    // ------------------------------------------------------------------
    std::cout << "[quasar_server] Stopping server…\n";
    server->stop();
    std::cout << "[quasar_server] Stopped. Goodbye.\n";

    return 0;
}
