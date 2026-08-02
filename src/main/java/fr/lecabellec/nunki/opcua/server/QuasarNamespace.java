package fr.lecabellec.nunki.opcua.server;

/*
 * @requirement REQ-00010 Full-featured OPC-UA integration
 * @feature FR-00010 OPC-UA Address Space Mocking & Embedded Server Verbose Logging
 * @task TSK-20260311-005 OPC UA Address Space Mocking
 * @compliance CS-0010 Code Traceability
 * @compliance CS-0020 AI Agent Verification Standard
 * @compliance CS-0030 Java Programming Standard (Defensive checks, explicit types, bounded pools)
 */

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.milo.opcua.sdk.core.AccessLevel;
import org.eclipse.milo.opcua.sdk.core.ValueRanks;
import org.eclipse.milo.opcua.sdk.server.OpcUaServer;
import org.eclipse.milo.opcua.sdk.server.api.DataItem;
import org.eclipse.milo.opcua.sdk.server.api.ManagedNamespaceWithLifecycle;
import org.eclipse.milo.opcua.sdk.server.api.MonitoredItem;
import org.eclipse.milo.opcua.sdk.server.api.methods.AbstractMethodInvocationHandler;
import org.eclipse.milo.opcua.sdk.server.api.services.AttributeServices;
import org.eclipse.milo.opcua.sdk.server.api.services.MethodServices;
import org.eclipse.milo.opcua.sdk.server.nodes.UaFolderNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaMethodNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaNode;
import org.eclipse.milo.opcua.sdk.server.nodes.UaVariableNode;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.builtin.Variant;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.Argument;
import org.eclipse.milo.opcua.stack.core.types.structured.CallMethodRequest;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.WriteValue;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Milo Managed Namespace mirroring the C++ Quasar Server address space.
 * 
 * Exposes 4 main branches under Root:
 * 1. LargeTree: 1,000 synthetic nodes (5 levels, 539 L5 leaves with round-robin types).
 * 2. CounterControl: CounterValue integer with Increment & Decrement RPC methods.
 * 3. FastCounters: Ticking counters (1 Hz to 100 Hz) with 15s warmup delay.
 * 4. Data: Legacy variables (MyInt, MySwitch, PumpRunning, TankLevel), ToggleSwitch method,
 *    ExecuteScript Lua execution method, and 100ms process mimic simulation.
 * 
 * Includes verbose logging capabilities for read, write, and call operations.
 */
public class QuasarNamespace extends ManagedNamespaceWithLifecycle implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(QuasarNamespace.class);
    public static final String NAMESPACE_URI = "urn:quasar:opcua:server";

    private final ScheduledExecutorService executorService;
    private final long startupTimeMs;
    private final long warmupDelayMs;
    private final boolean enableProcessSimulation;

    // Fast Counters references & state
    private final List<FastCounterSpec> fastCounters = new ArrayList<>();

    // Process Mimic variable node references
    private UaVariableNode mySwitchNode;
    private UaVariableNode pumpRunningNode;
    private UaVariableNode tankLevelNode;
    private UaFolderNode rootFolderNode;

    private static final class FastCounterSpec {
        private final UaVariableNode node;
        private final long intervalMs;
        private final int step;
        private long lastTickMs;

        public FastCounterSpec(UaVariableNode node, long intervalMs, int step, long lastTickMs) {
            this.node = Objects.requireNonNull(node, "node must not be null");
            this.intervalMs = intervalMs;
            this.step = step;
            this.lastTickMs = lastTickMs;
        }

        public UaVariableNode getNode() { return node; }
        public long getIntervalMs() { return intervalMs; }
        public int getStep() { return step; }
        public long getLastTickMs() { return lastTickMs; }
        public void setLastTickMs(long lastTickMs) { this.lastTickMs = lastTickMs; }
    }

    public QuasarNamespace(OpcUaServer server, long warmupDelaySeconds, boolean enableProcessSimulation) {
        super(server, NAMESPACE_URI);
        Objects.requireNonNull(server, "server must not be null");

        this.warmupDelayMs = TimeUnit.SECONDS.toMillis(warmupDelaySeconds);
        this.enableProcessSimulation = enableProcessSimulation;
        this.startupTimeMs = System.currentTimeMillis();

        this.executorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread thread = new Thread(r, "QuasarNamespace-Ticker-" + threadNumber.getAndIncrement());
                thread.setDaemon(true);
                return thread;
            }
        });

        getLifecycleManager().addStartupTask(() -> {
            registerNodes();
            startTickerLoop();
        });

        getLifecycleManager().addShutdownTask(this::close);
    }

    public QuasarNamespace(OpcUaServer server) {
        this(server, 15, true);
    }

    private NodeId createNodeId(String identifier) {
        return new NodeId(getNamespaceIndex(), identifier);
    }

    // Verbose Operation Logging Overrides ------------------------------------

    @Override
    public void read(
            AttributeServices.ReadContext context,
            Double maxAge,
            TimestampsToReturn timestamps,
            List<ReadValueId> readValueIds) {
        if (logger.isInfoEnabled()) {
            logger.info("[OPC-UA SERVER VERBOSE READ] Reading {} node(s), maxAge={}, timestamps={}",
                readValueIds != null ? readValueIds.size() : 0, maxAge, timestamps);
            if (readValueIds != null) {
                for (ReadValueId rvi : readValueIds) {
                    logger.info("  -> Read Target NodeId: {}, AttributeId: {}", rvi.getNodeId(), rvi.getAttributeId());
                }
            }
        }
        super.read(context, maxAge, timestamps, readValueIds);
    }

    @Override
    public void write(
            AttributeServices.WriteContext context,
            List<WriteValue> writeValues) {
        if (logger.isInfoEnabled()) {
            logger.info("[OPC-UA SERVER VERBOSE WRITE] Writing {} node(s)", writeValues != null ? writeValues.size() : 0);
            if (writeValues != null) {
                for (WriteValue wv : writeValues) {
                    Object val = (wv.getValue() != null && wv.getValue().getValue() != null) ? wv.getValue().getValue().getValue() : null;
                    logger.info("  -> Write Target NodeId: {}, New Value: {}, AttributeId: {}", wv.getNodeId(), val, wv.getAttributeId());
                }
            }
        }
        super.write(context, writeValues);
    }

    @Override
    public void call(
            MethodServices.CallContext context,
            List<CallMethodRequest> requests) {
        if (logger.isInfoEnabled()) {
            logger.info("[OPC-UA SERVER VERBOSE CALL] Calling {} method(s)", requests != null ? requests.size() : 0);
            if (requests != null) {
                for (CallMethodRequest req : requests) {
                    logger.info("  -> Method Call Target: ObjectId={}, MethodId={}, ArgsCount={}",
                        req.getObjectId(), req.getMethodId(),
                        req.getInputArguments() != null ? req.getInputArguments().length : 0);
                }
            }
        }
        super.call(context, requests);
    }

    // Node Tree Construction --------------------------------------------------

    private void registerNodes() {
        // Create top-level Root folder under ObjectsFolder
        NodeId rootNodeId = createNodeId("Root");
        rootFolderNode = new UaFolderNode(
            getNodeContext(),
            rootNodeId,
            new QualifiedName(getNamespaceIndex(), "Root"),
            LocalizedText.english("Root")
        );
        getNodeManager().addNode(rootFolderNode);

        // Link Root folder under ObjectsFolder (ns=0)
        getServer().getAddressSpaceManager().getManagedNode(Identifiers.ObjectsFolder)
            .ifPresent(node -> {
                if (node instanceof UaFolderNode) {
                    ((UaFolderNode) node).addOrganizes(rootFolderNode);
                }
            });

        // Build all 4 branches
        buildLargeTree(rootFolderNode);
        buildCounterControl(rootFolderNode);
        buildFastCounters(rootFolderNode);
        buildLegacyData(rootFolderNode);
    }


    /**
     * Builds Level 1 to Level 5 synthetic tree with exactly 1,000 nodes.
     */
    private void buildLargeTree(UaFolderNode root) {
        // Level 1: LargeTree folder
        UaFolderNode largeTreeRoot = createFolder(root, "LargeTree");

        // Level 2: 10 intermediate objects
        final int L2_COUNT = 10;
        List<UaFolderNode> level2 = new ArrayList<>(L2_COUNT);
        for (int i = 0; i < L2_COUNT; i++) {
            UaFolderNode node = createFolder(largeTreeRoot, "L2_" + i);
            level2.add(node);
        }

        // Level 3: 50 intermediate objects (5 per L2 node)
        final int L3_COUNT = 50;
        List<UaFolderNode> level3 = new ArrayList<>(L3_COUNT);
        for (int i = 0; i < L3_COUNT; i++) {
            UaFolderNode parent = level2.get(i / 5);
            UaFolderNode node = createFolder(parent, "L3_" + i);
            level3.add(node);
        }

        // Level 4: 400 intermediate objects (8 per L3 node)
        final int L4_COUNT = 400;
        List<UaFolderNode> level4 = new ArrayList<>(L4_COUNT);
        for (int i = 0; i < L4_COUNT; i++) {
            UaFolderNode parent = level3.get(i / 8);
            UaFolderNode node = createFolder(parent, "L4_" + i);
            level4.add(node);
        }

        // Level 5: 539 leaf nodes distributed round-robin across L4
        final int L5_COUNT = 539;
        for (int i = 0; i < L5_COUNT; i++) {
            UaFolderNode parent = level4.get(i % L4_COUNT);
            String name = "L5_" + i;
            int typeSlot = i % 5;

            switch (typeSlot) {
                case 0:
                    createVariable(parent, name, Identifiers.Int32, new Variant(i));
                    break;
                case 1:
                    createVariable(parent, name, Identifiers.Boolean, new Variant(i % 2 == 0));
                    break;
                case 2:
                    createVariable(parent, name, Identifiers.Double, new Variant((double) i * 0.1));
                    break;
                case 3:
                    createVariable(parent, name, Identifiers.String, new Variant("node_" + i));
                    break;
                default:
                    createNoOpMethod(parent, name);
                    break;
            }
        }

        logger.info("[QuasarNamespace] LargeTree built: 1 + 10 + 50 + 400 + 539 = 1,000 nodes.");
    }

    /**
     * Builds CounterControl branch with CounterValue, Increment, and Decrement methods.
     */
    private void buildCounterControl(UaFolderNode root) {
        UaFolderNode counterControlFolder = createFolder(root, "CounterControl");
        UaVariableNode counterValueNode = createVariable(counterControlFolder, "CounterValue", Identifiers.Int32, new Variant(0));

        // Increment Method
        createMethod(counterControlFolder, "Increment", new Argument[0], new Argument[] {
            new Argument("Result", Identifiers.Int32, ValueRanks.Scalar, null, LocalizedText.english("Updated CounterValue"))
        }, (inputArgs) -> {
            synchronized (counterValueNode) {
                int current = ((Number) counterValueNode.getValue().getValue().getValue()).intValue();
                int next = current + 1;
                counterValueNode.setValue(new DataValue(new Variant(next)));
                logger.info("[CounterControl] Increment: {} -> {}", current, next);
                return new Variant[] { new Variant(next) };
            }
        });

        // Decrement Method
        createMethod(counterControlFolder, "Decrement", new Argument[0], new Argument[] {
            new Argument("Result", Identifiers.Int32, ValueRanks.Scalar, null, LocalizedText.english("Updated CounterValue"))
        }, (inputArgs) -> {
            synchronized (counterValueNode) {
                int current = ((Number) counterValueNode.getValue().getValue().getValue()).intValue();
                int next = current - 1;
                counterValueNode.setValue(new DataValue(new Variant(next)));
                logger.info("[CounterControl] Decrement: {} -> {}", current, next);
                return new Variant[] { new Variant(next) };
            }
        });

        logger.info("[QuasarNamespace] CounterControl branch ready.");
    }

    /**
     * Builds FastCounters branch (1 Hz to 100 Hz).
     */
    private void buildFastCounters(UaFolderNode root) {
        UaFolderNode fastCountersFolder = createFolder(root, "FastCounters");

        final double MAX_WRITE_HZ = 10.0;
        final double[] freqs = new double[] { 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0 };
        final String[] names = new String[] { "1Hz", "2Hz", "5Hz", "10Hz", "20Hz", "50Hz", "100Hz" };

        long now = System.currentTimeMillis();

        for (int i = 0; i < freqs.length; i++) {
            double freq = freqs[i];
            String name = "Counter_" + names[i];
            UaVariableNode node = createVariable(fastCountersFolder, name, Identifiers.Int32, new Variant(0));

            long intervalMs;
            int step = 1;

            if (freq <= MAX_WRITE_HZ) {
                intervalMs = (long) (1000.0 / freq);
            } else {
                intervalMs = (long) (1000.0 / MAX_WRITE_HZ);
                step = (int) (freq / MAX_WRITE_HZ);
            }

            fastCounters.add(new FastCounterSpec(node, intervalMs, step, now));
        }

        logger.info("[QuasarNamespace] FastCounters branch ready (7 counters initialized).");
    }

    /**
     * Builds legacy Data branch and process mimic nodes.
     */
    private void buildLegacyData(UaFolderNode root) {
        UaFolderNode dataFolder = createFolder(root, "Data");

        createVariable(dataFolder, "MyInt", Identifiers.Int32, new Variant(42));
        mySwitchNode = createVariable(dataFolder, "MySwitch", Identifiers.Boolean, new Variant(false));
        pumpRunningNode = createVariable(dataFolder, "PumpRunning", Identifiers.Boolean, new Variant(false));
        tankLevelNode = createVariable(dataFolder, "TankLevel", Identifiers.Double, new Variant(45.0));

        // ToggleSwitch Method
        createMethod(dataFolder, "ToggleSwitch", new Argument[0], new Argument[] {
            new Argument("Result", Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("New MySwitch state string"))
        }, (inputArgs) -> {
            synchronized (mySwitchNode) {
                boolean current = (Boolean) mySwitchNode.getValue().getValue().getValue();
                boolean next = !current;
                mySwitchNode.setValue(new DataValue(new Variant(next)));
                String resultStr = next ? "True" : "False";
                logger.info("[Data] ToggleSwitch: MySwitch = {}", resultStr);
                return new Variant[] { new Variant(resultStr) };
            }
        });

        // ExecuteScript Lua Execution Method
        createMethod(dataFolder, "ExecuteScript", new Argument[] {
            new Argument("LuaCode", Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("Lua script code"))
        }, new Argument[] {
            new Argument("Result", Identifiers.String, ValueRanks.Scalar, null, LocalizedText.english("JSON evaluation response"))
        }, (inputArgs) -> {
            if (inputArgs == null || inputArgs.length == 0 || inputArgs[0].getValue() == null) {
                String errJson = "{\"success\":false,\"result\":\"Error: No Lua script code provided.\",\"logs\":[]}";
                return new Variant[] { new Variant(errJson) };
            }

            String luaScript = inputArgs[0].getValue().toString();
            String responseJson = executeLuaScript(luaScript);
            return new Variant[] { new Variant(responseJson) };
        });

        logger.info("[QuasarNamespace] Legacy Data branch ready.");
    }

    /**
     * Executes Lua script within an isolated LuaJ environment with opcua.read/write bindings.
     */
    private String executeLuaScript(String userLuaCode) {
        List<String> logs = new ArrayList<>();
        boolean success = false;
        String resultStr = "";

        try {
            Globals globals = JsePlatform.standardGlobals();

            // Override print to capture logs
            globals.set("print", new VarArgFunction() {
                @Override
                public Varargs invoke(Varargs args) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i <= args.narg(); i++) {
                        if (i > 1) sb.append("\t");
                        sb.append(args.arg(i).tojstring());
                    }
                    logs.add(sb.toString());
                    return LuaValue.NONE;
                }
            });

            // Create opcua table
            LuaTable opcuaTable = new LuaTable();

            // opcua.read(nodeId)
            opcuaTable.set("read", new OneArgFunction() {
                @Override
                public LuaValue call(LuaValue arg) {
                    String nodeIdStr = arg.tojstring();
                    NodeId nodeId = parseNodeId(nodeIdStr);
                    DataValue dv = getNodeValue(nodeId);
                    if (dv == null || dv.getValue() == null || dv.getValue().getValue() == null) {
                        return LuaValue.NIL;
                    }
                    Object val = dv.getValue().getValue();
                    if (val instanceof Boolean) return LuaValue.valueOf((Boolean) val);
                    if (val instanceof Number) return LuaValue.valueOf(((Number) val).doubleValue());
                    return LuaValue.valueOf(val.toString());
                }
            });

            // opcua.write(nodeId, value)
            opcuaTable.set("write", new TwoArgFunction() {
                @Override
                public LuaValue call(LuaValue arg1, LuaValue arg2) {
                    String nodeIdStr = arg1.tojstring();
                    NodeId nodeId = parseNodeId(nodeIdStr);
                    setNodeValue(nodeId, arg2);
                    return LuaValue.TRUE;
                }
            });

            globals.set("opcua", opcuaTable);

            LuaValue chunk = globals.load(userLuaCode);
            LuaValue evalResult = chunk.call();
            success = true;
            resultStr = evalResult.isnil() ? "OK" : evalResult.tojstring();
        } catch (Exception e) {
            success = false;
            resultStr = "Execution error: " + e.getMessage();
            logger.error("[QuasarNamespace] Exception executing Lua script", e);
        }

        // Build JSON response manually
        StringBuilder jsonSb = new StringBuilder();
        jsonSb.append("{");
        jsonSb.append("\"success\":").append(success).append(",");
        jsonSb.append("\"result\":").append(escapeJson(resultStr)).append(",");
        jsonSb.append("\"logs\":[");
        for (int i = 0; i < logs.size(); i++) {
            if (i > 0) jsonSb.append(",");
            jsonSb.append(escapeJson(logs.get(i)));
        }
        jsonSb.append("]");
        jsonSb.append("}");

        return jsonSb.toString();
    }

    private NodeId parseNodeId(String s) {
        if (s.startsWith("ns=")) {
            return NodeId.parse(s);
        }
        // Fallback for path-based resolution e.g. "Data/MyInt" or "Root/Data/MyInt"
        String cleanPath = s.startsWith("Root/") ? s.substring(5) : s;
        return createNodeId(cleanPath);
    }

    private DataValue getNodeValue(NodeId nodeId) {
        Optional<UaNode> targetNode = getNodeManager().getNode(nodeId);
        if (targetNode.isPresent() && targetNode.get() instanceof UaVariableNode) {
            return ((UaVariableNode) targetNode.get()).getValue();
        }
        return null;
    }

    private void setNodeValue(NodeId nodeId, LuaValue val) {
        Optional<UaNode> targetNode = getNodeManager().getNode(nodeId);
        if (targetNode.isPresent() && targetNode.get() instanceof UaVariableNode) {
            UaVariableNode varNode = (UaVariableNode) targetNode.get();
            Object currentVal = varNode.getValue().getValue().getValue();
            Variant newVariant;
            if (currentVal instanceof Boolean) {
                newVariant = new Variant(val.toboolean());
            } else if (currentVal instanceof Integer) {
                newVariant = new Variant(val.toint());
            } else if (currentVal instanceof Double || currentVal instanceof Float) {
                newVariant = new Variant(val.todouble());
            } else {
                newVariant = new Variant(val.tojstring());
            }
            varNode.setValue(new DataValue(newVariant));
        }
    }

    private String escapeJson(String raw) {
        if (raw == null) return "\"\"";
        return "\"" + raw.replace("\\", "\\\\")
                         .replace("\"", "\\\"")
                         .replace("\n", "\\n")
                         .replace("\r", "\\r")
                         .replace("\t", "\\t") + "\"";
    }

    private void startTickerLoop() {
        executorService.scheduleAtFixedRate(this::tickLoop, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void tickLoop() {
        try {
            long now = System.currentTimeMillis();

            // 1. FastCounters Ticker (after 15-second warmup)
            if (now - startupTimeMs >= warmupDelayMs) {
                for (FastCounterSpec spec : fastCounters) {
                    if (now - spec.getLastTickMs() >= spec.getIntervalMs()) {
                        synchronized (spec.getNode()) {
                            int current = ((Number) spec.getNode().getValue().getValue().getValue()).intValue();
                            spec.getNode().setValue(new DataValue(new Variant(current + spec.getStep())));
                        }
                        spec.setLastTickMs(now);
                    }
                }

                // 2. Physical Process Mimic Simulation (100 ms loop)
                if (enableProcessSimulation && pumpRunningNode != null && mySwitchNode != null && tankLevelNode != null) {
                    synchronized (tankLevelNode) {
                        boolean pumpOn = (Boolean) pumpRunningNode.getValue().getValue().getValue();
                        boolean valveOn = (Boolean) mySwitchNode.getValue().getValue().getValue();
                        double currentLevel = ((Number) tankLevelNode.getValue().getValue().getValue()).doubleValue();

                        double dt = 0.1; // 100 ms
                        double inflow = pumpOn ? 8.0 : 0.0;
                        double outflow = valveOn ? 5.0 : 0.0;

                        double newLevel = currentLevel + (inflow - outflow) * dt;
                        if (newLevel < 0.0) newLevel = 0.0;
                        if (newLevel > 100.0) newLevel = 100.0;

                        tankLevelNode.setValue(new DataValue(new Variant(newLevel)));
                    }
                }
            }
        } catch (Exception e) {
            logger.error("[QuasarNamespace] Error in periodic ticker loop", e);
        }
    }

    // Helper Node Creators
    private UaFolderNode createFolder(UaFolderNode parent, String name) {
        NodeId nodeId = createNodeId(getRelativePath(parent, name));
        UaFolderNode folderNode = new UaFolderNode(
            getNodeContext(),
            nodeId,
            new QualifiedName(getNamespaceIndex(), name),
            LocalizedText.english(name)
        );
        getNodeManager().addNode(folderNode);
        parent.addOrganizes(folderNode);
        return folderNode;
    }

    private UaVariableNode createVariable(UaFolderNode parent, String name, NodeId dataType, Variant initialValue) {
        NodeId nodeId = createNodeId(getRelativePath(parent, name));
        UaVariableNode varNode = new UaVariableNode(
            getNodeContext(),
            nodeId,
            new QualifiedName(getNamespaceIndex(), name),
            LocalizedText.english(name)
        );
        varNode.setDataType(dataType);
        varNode.setValue(new DataValue(initialValue));
        varNode.setAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));
        varNode.setUserAccessLevel(AccessLevel.toValue(AccessLevel.READ_WRITE));

        getNodeManager().addNode(varNode);
        parent.addOrganizes(varNode);
        return varNode;
    }

    private void createNoOpMethod(UaFolderNode parent, String name) {
        createMethod(parent, name, new Argument[0], new Argument[0], (args) -> new Variant[0]);
    }

    @FunctionalInterface
    public interface MethodHandler {
        Variant[] invoke(Variant[] inputArgs) throws Exception;
    }

    private UaMethodNode createMethod(UaFolderNode parent, String name, Argument[] inputArgs, Argument[] outputArgs, MethodHandler handler) {
        NodeId nodeId = createNodeId(getRelativePath(parent, name));
        UaMethodNode methodNode = UaMethodNode.builder(getNodeContext())
            .setNodeId(nodeId)
            .setBrowseName(new QualifiedName(getNamespaceIndex(), name))
            .setDisplayName(LocalizedText.english(name))
            .setDescription(LocalizedText.english(name))
            .build();

        methodNode.setInputArguments(inputArgs);
        methodNode.setOutputArguments(outputArgs);

        methodNode.setInvocationHandler(new AbstractMethodInvocationHandler(methodNode) {
            @Override
            public Argument[] getInputArguments() {
                return inputArgs;
            }

            @Override
            public Argument[] getOutputArguments() {
                return outputArgs;
            }

            @Override
            protected Variant[] invoke(AbstractMethodInvocationHandler.InvocationContext invocationContext, Variant[] inputValues) throws UaException {
                try {
                    return handler.invoke(inputValues);
                } catch (Exception e) {
                    logger.error("[QuasarNamespace] Method execution failed for {}", name, e);
                    throw new UaException(StatusCodes.Bad_InternalError, e);
                }
            }
        });

        getNodeManager().addNode(methodNode);
        parent.addComponent(methodNode);
        return methodNode;
    }

    private String getRelativePath(UaFolderNode parent, String name) {
        String parentName = parent.getBrowseName().getName();
        if ("Root".equals(parentName)) {
            return name;
        }
        String parentIdStr = parent.getNodeId().getIdentifier().toString();
        return parentIdStr + "/" + name;
    }

    // MonitoredItemServices implementations
    @Override
    public void onDataItemsCreated(List<DataItem> dataItems) {}

    @Override
    public void onDataItemsModified(List<DataItem> dataItems) {}

    @Override
    public void onDataItemsDeleted(List<DataItem> dataItems) {}

    @Override
    public void onMonitoringModeChanged(List<MonitoredItem> monitoredItems) {}

    @Override
    public void close() {
        logger.info("[QuasarNamespace] Shutting down namespace ticker thread pool...");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
