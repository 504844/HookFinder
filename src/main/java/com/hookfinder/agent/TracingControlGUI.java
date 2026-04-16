package com.hookfinder.agent;

import com.hookfinder.action.*;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.lang.instrument.Instrumentation;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class TracingControlGUI extends JFrame {

    private static final Color BG_DARK       = new Color(30, 30, 30);
    private static final Color BG_PANEL      = new Color(40, 40, 40);
    private static final Color ACCENT        = new Color(79, 140, 255);
    private static final Color ACCENT_GREEN  = new Color(80, 200, 120);
    private static final Color ACCENT_RED    = new Color(255, 100, 100);
    private static final Color ACCENT_ORANGE = new Color(255, 165, 50);
    private static final Color TEXT_DIM      = new Color(140, 140, 140);

    private final TracingTransformer transformer;
    private final Instrumentation    instrumentation;
    private final ActionTestSuite    testSuite;

    private JLabel             statusLabel;
    private JButton            traceBtn;
    private JSpinner           durationSpinner;
    private JComboBox<String>  actionCombo;
    private DefaultTableModel  methodTableModel;
    private DefaultTableModel  fieldTableModel;
    private DefaultListModel<String> testListModel;
    private JList<String>      testList;
    private JTextArea          analysisResultsArea;
    private Timer              refreshTimer;

    private String pendingActionName = null;

    public TracingControlGUI(TracingTransformer transformer, Instrumentation instrumentation) {
        super("HookFinder Tracer");
        this.transformer     = transformer;
        this.instrumentation = instrumentation;
        this.testSuite       = new ActionTestSuite();

        TransformerHolder.setTransformer(transformer);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setSize(1000, 650);
        setAlwaysOnTop(true);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        initComponents();
        startRefreshTimer();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { refreshTimer.stop(); }
        });
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 0));

        // === TOP: Controls ===
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        controls.setBackground(BG_PANEL);

        controls.add(new JLabel("Action:"));
        actionCombo = new JComboBox<>();
        actionCombo.setEditable(true);
        for (String action : ActionTestSuite.COMMON_ACTIONS) actionCombo.addItem(action);
        actionCombo.setPreferredSize(new Dimension(150, 28));
        controls.add(actionCombo);

        controls.add(Box.createHorizontalStrut(10));
        controls.add(new JLabel("Duration:"));
        durationSpinner = new JSpinner(new SpinnerNumberModel(500, 100, 5000, 100));
        controls.add(durationSpinner);
        controls.add(new JLabel("ms"));
        controls.add(Box.createHorizontalStrut(10));

        traceBtn = new JButton("\u25B6 Record Action");
        traceBtn.setFont(traceBtn.getFont().deriveFont(Font.BOLD, 12f));
        traceBtn.setForeground(ACCENT_GREEN);
        traceBtn.addActionListener(e -> toggleTrace());
        controls.add(traceBtn);

        add(controls, BorderLayout.NORTH);

        // === CENTER: Tabbed pane ===
        JTabbedPane tabs = new JTabbedPane();
        tabs.setBackground(BG_DARK);

        tabs.addTab("Live Trace",   buildLiveTracePanel());
        tabs.addTab("Saved Tests",  buildTestSuitePanel());
        tabs.addTab("Analysis",     buildAnalysisPanel());
        tabs.addTab("\u2699 Test Hooks", buildTestHookPanel());   // <-- NEW TAB

        add(tabs, BorderLayout.CENTER);

        // === BOTTOM: Status ===
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(BG_PANEL);
        statusBar.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statusLabel = new JLabel("Ready. Select an action, click 'Record', then perform it in-game.");
        statusLabel.setForeground(TEXT_DIM);
        statusBar.add(statusLabel, BorderLayout.WEST);

        JLabel infoLabel = new JLabel("Classes: " + transformer.getTransformedClasses().size());
        infoLabel.setForeground(TEXT_DIM);
        statusBar.add(infoLabel, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    // ==================== NEW: Test Hook Panel ====================

    private JPanel buildTestHookPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ---- Top: method info area ----
        JTextArea infoArea = new JTextArea(8, 50);
        infoArea.setEditable(false);
        infoArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        infoArea.setBackground(BG_PANEL);
        infoArea.setForeground(Color.WHITE);
        infoArea.setText("Click 'Resolve Method' to check if InvokeMenuAction is accessible at runtime.");

        JButton resolveBtn = new JButton("Resolve Method");
        resolveBtn.setForeground(ACCENT);
        resolveBtn.addActionListener(e -> {
            InvokeMenuActionTest.reset();
            infoArea.setText(InvokeMenuActionTest.getMethodInfo());
        });

        JPanel topPanel = new JPanel(new BorderLayout(6, 6));
        topPanel.setBackground(BG_DARK);
        topPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT),
                "InvokeMenuAction  \u2192  osrs.ChatIcon.method9367  |  junk = (byte)500 = -12",
                0, 0, null, ACCENT));
        topPanel.add(resolveBtn, BorderLayout.NORTH);
        topPanel.add(new JScrollPane(infoArea), BorderLayout.CENTER);

        // ---- Middle: param fields ----
        // Actual runtime signature:
        //   method9367(int p0, int p1, int opcode, int identifier, int itemId,
        //              int extra, String option, String target, int x, int y, byte junk)
        JPanel paramPanel = new JPanel(new GridLayout(0, 4, 8, 6));
        paramPanel.setBackground(BG_PANEL);
        paramPanel.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_ORANGE),
                "Parameters  \u2014  actual signature: (int,int,int,int,int,int,String,String,int,int,byte)",
                0, 0, null, ACCENT_ORANGE));

        // Fields — default preset is Walk Here
        JTextField p0Field         = styledField("0");        // scene tile X
        JTextField p1Field         = styledField("0");        // scene tile Y
        JTextField opcodeField     = styledField("1007");     // MenuAction.WALK
        JTextField identifierField = styledField("0");        // entity ID
        JTextField itemIdField     = styledField("0");        // item ID
        JTextField extraField      = styledField("0");        // [5] always 0
        JTextField optionField     = styledField("Walk here");
        JTextField targetField     = styledField("");
        JTextField xField          = styledField("0");        // canvas X
        JTextField yField          = styledField("0");        // canvas Y

        paramPanel.add(label("[0] p0  (scene tile X / slot):"));    paramPanel.add(p0Field);
        paramPanel.add(label("[1] p1  (scene tile Y / itemId):"));  paramPanel.add(p1Field);
        paramPanel.add(label("[2] opcode  (MenuAction type):"));    paramPanel.add(opcodeField);
        paramPanel.add(label("[3] identifier  (entity/widget):"));  paramPanel.add(identifierField);
        paramPanel.add(label("[4] itemId:"));                        paramPanel.add(itemIdField);
        paramPanel.add(label("[5] extra  (always 0):"));            paramPanel.add(extraField);
        paramPanel.add(label("[6] option  (menu text):"));          paramPanel.add(optionField);
        paramPanel.add(label("[7] target  (entity name):"));        paramPanel.add(targetField);
        paramPanel.add(label("[8] x  (canvas X):"));                paramPanel.add(xField);
        paramPanel.add(label("[9] y  (canvas Y):"));                paramPanel.add(yField);
        paramPanel.add(label("[10] junk  (fixed):"));               paramPanel.add(new JLabel("(byte)500 = -12"));

        // ---- Presets ----
        JPanel presetPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        presetPanel.setBackground(BG_DARK);
        presetPanel.add(label("Presets:"));

        JButton presetWalk = new JButton("Walk Here");
        presetWalk.addActionListener(e -> {
            p0Field.setText("0");
            p1Field.setText("0");
            opcodeField.setText("1007");
            identifierField.setText("0");
            itemIdField.setText("0");
            extraField.setText("0");
            optionField.setText("Walk here");
            targetField.setText("");
            xField.setText("0");
            yField.setText("0");
        });

        JButton presetAttack = new JButton("Attack NPC");
        presetAttack.addActionListener(e -> {
            // Set identifier field to NPC index manually
            p0Field.setText("0");
            p1Field.setText("0");
            opcodeField.setText("9");
            identifierField.setText("0");  // <-- user fills NPC index here
            itemIdField.setText("0");
            extraField.setText("0");
            optionField.setText("Attack");
            targetField.setText("");
            xField.setText("0");
            yField.setText("0");
        });

        JButton presetTalk = new JButton("Talk-to NPC");
        presetTalk.addActionListener(e -> {
            p0Field.setText("0");
            p1Field.setText("0");
            opcodeField.setText("11");
            identifierField.setText("0");
            itemIdField.setText("0");
            extraField.setText("0");
            optionField.setText("Talk-to");
            targetField.setText("");
            xField.setText("0");
            yField.setText("0");
        });

        JButton presetItem = new JButton("Inv Item (Eat/Use)");
        presetItem.addActionListener(e -> {
            // slot goes in p0, itemId in p1, opcode 25 = CC_OP
            p0Field.setText("0");         // inventory slot
            p1Field.setText("0");         // item ID
            opcodeField.setText("25");    // CC_OP
            identifierField.setText("9764864"); // packed INVENTORY widget ID
            itemIdField.setText("0");
            extraField.setText("0");
            optionField.setText("Use");
            targetField.setText("");
            xField.setText("0");
            yField.setText("0");
        });

        presetPanel.add(presetWalk);
        presetPanel.add(presetAttack);
        presetPanel.add(presetTalk);
        presetPanel.add(presetItem);

        // ---- Hint label ----
        JLabel hintLabel = new JLabel(
                "<html><font color='#888888'>Tip: right-click a tile in-game → Walk Here, " +
                        "then read p0/p1 from console log to get real scene coords.</font></html>"
        );
        hintLabel.setBorder(BorderFactory.createEmptyBorder(4, 2, 4, 2));

        // ---- Log area ----
        JTextArea logArea = new JTextArea(6, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
        logArea.setBackground(BG_DARK);
        logArea.setForeground(Color.WHITE);

        // ---- Action buttons ----
        JButton callBtn = new JButton("CALL InvokeMenuAction");
        callBtn.setForeground(ACCENT_GREEN);
        callBtn.setFont(callBtn.getFont().deriveFont(Font.BOLD, 12f));
        callBtn.addActionListener(e -> {
            try {
                int p0     = Integer.parseInt(p0Field.getText().trim());
                int p1     = Integer.parseInt(p1Field.getText().trim());
                int opcode = Integer.parseInt(opcodeField.getText().trim());
                int id     = Integer.parseInt(identifierField.getText().trim());
                int itemId = Integer.parseInt(itemIdField.getText().trim());
                int extra  = Integer.parseInt(extraField.getText().trim());
                String opt = optionField.getText();
                String tgt = targetField.getText();
                int x      = Integer.parseInt(xField.getText().trim());
                int y      = Integer.parseInt(yField.getText().trim());

                log(logArea, "Calling: invoke("
                        + p0 + ", " + p1 + ", " + opcode + ", " + id + ", " + itemId + ", " + extra
                        + ", \"" + opt + "\", \"" + tgt + "\", " + x + ", " + y + ")");

                boolean ok = InvokeMenuActionTest.invoke(p0, p1, opcode, id, itemId, extra, opt, tgt, x, y);
                log(logArea, ok ? "\u2713 Called! Watch the game." : "\u2717 Failed \u2014 check console");

            } catch (NumberFormatException ex) {
                log(logArea, "\u2717 Invalid number: " + ex.getMessage());
            }
        });

        JButton walkBtn = new JButton("Quick: Walk Here");
        walkBtn.setForeground(ACCENT_ORANGE);
        walkBtn.addActionListener(e -> {
            int p0 = parseOrDefault(p0Field, 0);
            int p1 = parseOrDefault(p1Field, 0);
            log(logArea, "Quick walkHere(sceneTileX=" + p0 + ", sceneTileY=" + p1 + ")");
            boolean ok = InvokeMenuActionTest.walkHere(p0, p1);
            log(logArea, ok ? "\u2713 Called!" : "\u2717 Failed \u2014 check console");
        });

        JButton attackBtn = new JButton("Quick: Attack NPC");
        attackBtn.setForeground(ACCENT_RED);
        attackBtn.addActionListener(e -> {
            int npcIndex = parseOrDefault(identifierField, 0);
            int x        = parseOrDefault(xField, 0);
            int y        = parseOrDefault(yField, 0);
            log(logArea, "Quick attackNpc(npcIndex=" + npcIndex + ")");
            boolean ok = InvokeMenuActionTest.attackNpc(npcIndex, x, y);
            log(logArea, ok ? "\u2713 Called!" : "\u2717 Failed \u2014 check console");
        });

        JButton clearBtn = new JButton("Clear Log");
        clearBtn.addActionListener(e -> logArea.setText(""));

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        btnRow.setBackground(BG_DARK);
        btnRow.add(callBtn);
        btnRow.add(walkBtn);
        btnRow.add(attackBtn);
        btnRow.add(clearBtn);

        // ---- Bottom panel ----
        JPanel bottomPanel = new JPanel(new BorderLayout(4, 4));
        bottomPanel.setBackground(BG_DARK);
        bottomPanel.add(presetPanel, BorderLayout.NORTH);
        bottomPanel.add(btnRow, BorderLayout.CENTER);
        bottomPanel.add(new JScrollPane(logArea), BorderLayout.SOUTH);

        // ---- Assemble ----
        JPanel centerPanel = new JPanel(new BorderLayout(4, 4));
        centerPanel.setBackground(BG_DARK);
        centerPanel.add(paramPanel, BorderLayout.CENTER);
        centerPanel.add(hintLabel, BorderLayout.SOUTH);

        panel.add(topPanel,    BorderLayout.NORTH);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        return panel;
    }

    // ==================== Helpers ====================

    private JTextField styledField(String text) {
        JTextField f = new JTextField(text);
        f.setBackground(BG_PANEL);
        f.setForeground(Color.WHITE);
        f.setCaretColor(Color.WHITE);
        return f;
    }

    private JLabel label(String text) {
        JLabel l = new JLabel(text);
        l.setForeground(TEXT_DIM);
        return l;
    }

    private void log(JTextArea area, String message) {
        SwingUtilities.invokeLater(() -> {
            area.append(message + "\n");
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    private int parseOrDefault(JTextField field, int def) {
        try { return Integer.parseInt(field.getText().trim()); }
        catch (NumberFormatException e) { return def; }
    }

    // ==================== Live Trace Panel ====================

    private JPanel buildLiveTracePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(BG_DARK);

        JTabbedPane innerTabs = new JTabbedPane();

        methodTableModel = new DefaultTableModel(
                new String[]{"Count", "Class", "Method", "Descriptor"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable methodTable = createStyledTable(methodTableModel);
        methodTable.getColumnModel().getColumn(0).setPreferredWidth(60);
        methodTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        methodTable.getColumnModel().getColumn(2).setPreferredWidth(100);
        methodTable.getColumnModel().getColumn(3).setPreferredWidth(220);
        innerTabs.addTab("Methods", new JScrollPane(methodTable));

        fieldTableModel = new DefaultTableModel(
                new String[]{"Count", "Class", "Field", "Type", "R/W"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable fieldTable = createStyledTable(fieldTableModel);
        innerTabs.addTab("Fields", new JScrollPane(fieldTable));

        panel.add(innerTabs, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        btnPanel.setBackground(BG_PANEL);

        JButton clearBtn = new JButton("Clear");
        clearBtn.addActionListener(e -> { transformer.clearTraces(); refreshTables(); });
        btnPanel.add(clearBtn);

        JButton saveBtn = new JButton("Save as Test");
        saveBtn.setForeground(ACCENT_GREEN);
        saveBtn.addActionListener(e -> saveCurrentTrace());
        btnPanel.add(saveBtn);

        panel.add(btnPanel, BorderLayout.SOUTH);
        return panel;
    }

    // ==================== Test Suite Panel ====================

    private JPanel buildTestSuitePanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel leftPanel = new JPanel(new BorderLayout(0, 6));
        leftPanel.setBackground(BG_PANEL);
        leftPanel.setPreferredSize(new Dimension(200, 0));
        leftPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JLabel title = new JLabel("SAVED TESTS");
        title.setForeground(ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        leftPanel.add(title, BorderLayout.NORTH);

        testListModel = new DefaultListModel<>();
        refreshTestList();
        testList = new JList<>(testListModel);
        testList.setBackground(BG_DARK);
        testList.setSelectionBackground(ACCENT);
        testList.setFixedCellHeight(28);
        leftPanel.add(new JScrollPane(testList), BorderLayout.CENTER);

        JPanel listBtns = new JPanel(new GridLayout(2, 2, 4, 4));
        listBtns.setBackground(BG_PANEL);

        JButton viewBtn = new JButton("View");
        viewBtn.addActionListener(e -> viewSelectedTest());
        listBtns.add(viewBtn);

        JButton deleteBtn = new JButton("Delete");
        deleteBtn.setForeground(ACCENT_RED);
        deleteBtn.addActionListener(e -> deleteSelectedTest());
        listBtns.add(deleteBtn);

        JButton exportBtn = new JButton("Export");
        exportBtn.addActionListener(e -> exportTests());
        listBtns.add(exportBtn);

        JButton importBtn = new JButton("Import");
        importBtn.addActionListener(e -> importTests());
        listBtns.add(importBtn);

        leftPanel.add(listBtns, BorderLayout.SOUTH);
        panel.add(leftPanel, BorderLayout.WEST);

        JTextArea detailsArea = new JTextArea();
        detailsArea.setEditable(false);
        detailsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        detailsArea.setBackground(BG_DARK);
        detailsArea.setForeground(Color.WHITE);

        testList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = testList.getSelectedIndex();
                if (idx >= 0 && idx < testSuite.getTests().size()) {
                    ActionTest test = testSuite.getTests().get(idx);
                    detailsArea.setText(formatTestDetails(test));
                    detailsArea.setCaretPosition(0);
                }
            }
        });

        panel.add(new JScrollPane(detailsArea), BorderLayout.CENTER);
        return panel;
    }

    // ==================== Analysis Panel ====================

    private JPanel buildAnalysisPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBackground(BG_DARK);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        btnPanel.setBackground(BG_PANEL);

        JButton commonBtn   = new JButton("Find Common Methods (Noise)");
        JButton uniqueBtn   = new JButton("Find Unique Methods (Hooks)");
        JButton compareBtn  = new JButton("Compare Two Tests");
        JButton generateBtn = new JButton("\u2B50 Generate Hook Candidates");
        generateBtn.setForeground(ACCENT_ORANGE);

        btnPanel.add(commonBtn);
        btnPanel.add(uniqueBtn);
        btnPanel.add(compareBtn);
        btnPanel.add(generateBtn);
        panel.add(btnPanel, BorderLayout.NORTH);

        analysisResultsArea = new JTextArea();
        analysisResultsArea.setEditable(false);
        analysisResultsArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        analysisResultsArea.setBackground(BG_DARK);
        analysisResultsArea.setForeground(Color.WHITE);
        panel.add(new JScrollPane(analysisResultsArea), BorderLayout.CENTER);

        commonBtn.addActionListener(e -> {
            if (testSuite.getTests().size() < 2) {
                analysisResultsArea.setText("Need at least 2 recorded tests to find common methods.");
                return;
            }
            List<ActionTest.MethodSignature> common = testSuite.findCommonMethods();
            StringBuilder sb = new StringBuilder();
            sb.append("=== COMMON METHODS (appear in ALL tests) ===\n");
            sb.append("These are noise: game loop, rendering, tick processing.\n");
            sb.append("Total: ").append(common.size()).append(" methods\n\n");
            for (ActionTest.MethodSignature m : common) sb.append("  ").append(m).append("\n");
            analysisResultsArea.setText(sb.toString());
            analysisResultsArea.setCaretPosition(0);
        });

        uniqueBtn.addActionListener(e -> {
            int idx = testList.getSelectedIndex();
            if (idx < 0 || idx >= testSuite.getTests().size()) {
                analysisResultsArea.setText("Select a test first from 'Saved Tests' tab.");
                return;
            }
            ActionTest test = testSuite.getTests().get(idx);
            List<ActionTest.MethodSignature> unique = testSuite.findUniqueMethods(test);
            StringBuilder sb = new StringBuilder();
            sb.append("=== UNIQUE METHODS for: ").append(test.getName()).append(" ===\n");
            sb.append("Total: ").append(unique.size()).append("\n\n");
            for (ActionTest.MethodSignature m : unique) sb.append("  ").append(m).append("\n");
            analysisResultsArea.setText(sb.toString());
            analysisResultsArea.setCaretPosition(0);
        });

        compareBtn.addActionListener(e -> {
            if (testSuite.getTests().size() < 2) {
                analysisResultsArea.setText("Need at least 2 tests to compare.");
                return;
            }
            String[] names = testSuite.getTests().stream()
                    .map(ActionTest::getName).toArray(String[]::new);
            String first  = (String) JOptionPane.showInputDialog(this,
                    "Select first test:", "Compare", JOptionPane.PLAIN_MESSAGE, null, names, names[0]);
            if (first == null) return;
            String second = (String) JOptionPane.showInputDialog(this,
                    "Select second test:", "Compare", JOptionPane.PLAIN_MESSAGE, null, names,
                    names.length > 1 ? names[1] : names[0]);
            if (second == null) return;

            ActionTest t1 = testSuite.getTestByName(first);
            ActionTest t2 = testSuite.getTestByName(second);
            if (t1 == null || t2 == null) { analysisResultsArea.setText("Tests not found."); return; }

            ActionTestSuite.ComparisonResult result = testSuite.compare(t1, t2);
            StringBuilder sb = new StringBuilder();
            sb.append("=== COMPARISON: ").append(first).append(" vs ").append(second).append(" ===\n\n");
            sb.append("--- Only in '").append(first).append("' (").append(result.onlyInFirst.size()).append(") ---\n");
            for (ActionTest.MethodSignature m : result.onlyInFirst) sb.append("  ").append(m).append("\n");
            sb.append("\n--- Only in '").append(second).append("' (").append(result.onlyInSecond.size()).append(") ---\n");
            for (ActionTest.MethodSignature m : result.onlyInSecond) sb.append("  ").append(m).append("\n");
            sb.append("\n--- In both: ").append(result.inBoth.size()).append(" methods ---\n");
            analysisResultsArea.setText(sb.toString());
            analysisResultsArea.setCaretPosition(0);
        });

        generateBtn.addActionListener(e -> {
            if (testSuite.getTests().size() < 2) {
                analysisResultsArea.setText(
                        "Record at least 2 different actions first.\n\n"
                                + "Example:\n"
                                + "  1. Record 'Walk Here'\n"
                                + "  2. Record 'Attack NPC'\n"
                                + "  3. Click Generate\n");
                return;
            }
            Map<String, List<ActionTest.MethodSignature>> candidates = testSuite.generateHookCandidates();
            StringBuilder sb = new StringBuilder();
            sb.append("=== \u2B50 HOOK CANDIDATES ===\n\n");
            for (Map.Entry<String, List<ActionTest.MethodSignature>> entry : candidates.entrySet()) {
                sb.append("### ").append(entry.getKey()).append(" ###\n");
                if (entry.getValue().isEmpty()) {
                    sb.append("  (no unique methods found)\n");
                } else {
                    for (ActionTest.MethodSignature m : entry.getValue()) sb.append("  ").append(m).append("\n");
                }
                sb.append("\n");
            }
            analysisResultsArea.setText(sb.toString());
            analysisResultsArea.setCaretPosition(0);
        });

        return panel;
    }

    // ==================== Trace Control ====================

    private void toggleTrace() {
        if (transformer.isTracingActive()) {
            transformer.stopTracing();
            traceBtn.setText("\u25B6 Record Action");
            traceBtn.setForeground(ACCENT_GREEN);
            statusLabel.setText("Trace complete. " + transformer.getMethodTraces().size() + " methods.");
            statusLabel.setForeground(ACCENT_GREEN);
            refreshTables();
            if (pendingActionName != null) { saveTraceAsTest(pendingActionName); pendingActionName = null; }
        } else {
            String actionName = (String) actionCombo.getSelectedItem();
            if (actionName == null || actionName.trim().isEmpty()) actionName = "Unnamed";
            pendingActionName = actionName.trim();
            int duration = (int) durationSpinner.getValue();
            transformer.startTracing(duration);
            traceBtn.setText("\u25A0 Recording...");
            traceBtn.setForeground(ACCENT_RED);
            statusLabel.setText("Recording '" + pendingActionName + "'... Perform the action in-game NOW!");
            statusLabel.setForeground(ACCENT_RED);
        }
    }

    // ==================== Save / Load ====================

    private void saveCurrentTrace() {
        String name = JOptionPane.showInputDialog(this, "Test name:", actionCombo.getSelectedItem());
        if (name != null && !name.trim().isEmpty()) saveTraceAsTest(name.trim());
    }

    private void saveTraceAsTest(String name) {
        ActionTest test = new ActionTest(name);
        test.setTraceDurationMs((int) durationSpinner.getValue());
        for (TracingTransformer.MethodTrace m : transformer.getMethodTraces())
            test.getMethods().add(new ActionTest.MethodSignature(m.owner, m.name, m.desc, m.count));
        for (TracingTransformer.FieldTrace f : transformer.getFieldTraces())
            test.getFields().add(new ActionTest.FieldSignature(
                    f.owner, f.name, f.desc, f.isWrite ? 0 : f.count, f.isWrite ? f.count : 0));
        testSuite.addTest(test);
        refreshTestList();
        statusLabel.setText("Saved: '" + name + "' ("
                + test.getMethods().size() + " methods)");
        statusLabel.setForeground(ACCENT_GREEN);
    }

    private void viewSelectedTest() {
        int idx = testList.getSelectedIndex();
        if (idx < 0 || idx >= testSuite.getTests().size()) return;
        ActionTest test = testSuite.getTests().get(idx);
        JTextArea ta = new JTextArea(formatTestDetails(test));
        ta.setEditable(false);
        ta.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scroll = new JScrollPane(ta);
        scroll.setPreferredSize(new Dimension(600, 400));
        JOptionPane.showMessageDialog(this, scroll, "Test: " + test.getName(), JOptionPane.PLAIN_MESSAGE);
    }

    private void deleteSelectedTest() {
        int idx = testList.getSelectedIndex();
        if (idx < 0 || idx >= testSuite.getTests().size()) return;
        ActionTest test = testSuite.getTests().get(idx);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete '" + test.getName() + "'?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) { testSuite.removeTest(test); refreshTestList(); }
    }

    private void exportTests() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File("hookfinder_tests.json"));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try { testSuite.exportToFile(chooser.getSelectedFile());
                statusLabel.setText("Exported to: " + chooser.getSelectedFile().getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void importTests() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try { testSuite.importFromFile(chooser.getSelectedFile()); refreshTestList();
                statusLabel.setText("Imported from: " + chooser.getSelectedFile().getName());
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Import failed: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ==================== Table / List Helpers ====================

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        table.setRowHeight(24);
        table.setShowGrid(false);
        table.setBackground(BG_DARK);
        table.setForeground(Color.WHITE);
        table.setSelectionBackground(ACCENT);
        return table;
    }

    private void refreshTables() {
        methodTableModel.setRowCount(0);
        for (TracingTransformer.MethodTrace m : transformer.getMethodTraces())
            methodTableModel.addRow(new Object[]{m.count, m.owner.replace('/', '.'), m.name, m.desc});
        fieldTableModel.setRowCount(0);
        for (TracingTransformer.FieldTrace f : transformer.getFieldTraces())
            fieldTableModel.addRow(new Object[]{f.count, f.owner.replace('/', '.'), f.name, f.desc,
                    f.isWrite ? "WRITE" : "READ"});
    }

    private void refreshTestList() {
        testListModel.clear();
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd HH:mm");
        for (ActionTest test : testSuite.getTests())
            testListModel.addElement(test.getName() + " [" + sdf.format(new Date(test.getTimestamp())) + "]");
    }

    private String formatTestDetails(ActionTest test) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name:     ").append(test.getName()).append("\n");
        sb.append("Recorded: ").append(new Date(test.getTimestamp())).append("\n");
        sb.append("Duration: ").append(test.getTraceDurationMs()).append("ms\n");
        sb.append("Methods:  ").append(test.getMethods().size()).append("\n");
        sb.append("Fields:   ").append(test.getFields().size()).append("\n\n");
        sb.append("=== TOP 30 METHODS ===\n");
        test.getTopMethods(30).forEach(m -> sb.append(String.format(
                "  [%5d] %s.%s%s\n", m.callCount, m.owner, m.name, m.desc)));
        sb.append("\n=== TOP 20 FIELDS ===\n");
        test.getFields().stream()
                .sorted((a, b) -> Integer.compare(
                        b.readCount + b.writeCount, a.readCount + a.writeCount))
                .limit(20)
                .forEach(f -> sb.append(String.format(
                        "  %s.%s (%s) R:%d W:%d\n", f.owner, f.name, f.desc, f.readCount, f.writeCount)));
        return sb.toString();
    }

    private void startRefreshTimer() {
        refreshTimer = new Timer(200, e -> {
            if (transformer.isTracingActive()) {
                statusLabel.setText("Recording... Methods: " + transformer.getMethodTraces().size()
                        + ", Fields: " + transformer.getFieldTraces().size());
                statusLabel.setForeground(ACCENT_RED);
            } else if (traceBtn.getText().contains("Recording")) {
                traceBtn.setText("\u25B6 Record Action");
                traceBtn.setForeground(ACCENT_GREEN);
                statusLabel.setText("Trace complete. " + transformer.getMethodTraces().size() + " methods.");
                statusLabel.setForeground(ACCENT_GREEN);
                refreshTables();
                if (pendingActionName != null) { saveTraceAsTest(pendingActionName); pendingActionName = null; }
            }
        });
        refreshTimer.start();
    }

    // ==================== Static Launch ====================

    public static void launch(TracingTransformer transformer, Instrumentation instrumentation) {
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> {
            TracingControlGUI gui = new TracingControlGUI(transformer, instrumentation);
            gui.setVisible(true);
        });
    }
}