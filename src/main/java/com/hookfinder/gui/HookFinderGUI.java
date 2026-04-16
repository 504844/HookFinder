package com.hookfinder.gui;

import com.hookfinder.core.*;
import com.hookfinder.util.RuneLiteLocator;

import com.formdev.flatlaf.FlatDarkLaf;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

public class HookFinderGUI extends JFrame {

    private static final String PREF_LAST_DIR = "hookfinder.lastDir";
    private final Preferences prefs = Preferences.userNodeForPackage(HookFinderGUI.class);

    private static final Color BG_DARK      = new Color(30, 30, 30);
    private static final Color BG_PANEL     = new Color(40, 40, 40);
    private static final Color ACCENT       = new Color(79, 140, 255);
    private static final Color ACCENT_GREEN = new Color(80, 200, 120);
    private static final Color ACCENT_ORANGE = new Color(255, 165, 50);
    private static final Color TEXT_DIM     = new Color(140, 140, 140);
    private static final Color BORDER_COLOR = new Color(60, 60, 60);

    private JTabbedPane tabbedPane;
    private JLabel globalStatus;
    private JPanel dropOverlay;

    // Pattern management
    private final PatternStore patternStore = new PatternStore();
    private List<PatternDefinition> patternDefinitions;
    private DefaultListModel<String> patternListModel;
    private JList<String> patternList;

    public HookFinderGUI() {
        super("HookFinder");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 720);
        setMinimumSize(new Dimension(850, 500));
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);

        patternDefinitions = patternStore.load();
        initComponents();
        enableDragAndDrop();
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 0));

        // === TOP: Toolbar ===
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        toolbar.setBackground(BG_PANEL);
        toolbar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_COLOR));

        JButton loadBtn = createButton("\uD83D\uDCC2  Load JAR");
        loadBtn.addActionListener(e -> onLoadViaChooser());
        toolbar.add(loadBtn);

        JButton runeliteBtn = createButton("\uD83E\uDDA5  Load from RuneLite");
        runeliteBtn.setForeground(ACCENT_ORANGE);
        runeliteBtn.addActionListener(e -> onLoadFromRuneLite());
        toolbar.add(runeliteBtn);

        toolbar.add(Box.createHorizontalStrut(10));

        JLabel hint = new JLabel("Drag & drop JAR files anywhere");
        hint.setForeground(TEXT_DIM);
        hint.setFont(hint.getFont().deriveFont(Font.ITALIC, 12f));
        toolbar.add(hint);

        add(toolbar, BorderLayout.NORTH);

        // === LEFT: Pattern Manager ===
        JPanel patternManager = buildPatternManager();
        add(patternManager, BorderLayout.WEST);

        // === CENTER: Tabbed pane with drop overlay ===
        JLayeredPane layered = new JLayeredPane();
        layered.setLayout(new OverlayLayout(layered));

        tabbedPane = new JTabbedPane();
        tabbedPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabbedPane.setBackground(BG_DARK);
        layered.add(tabbedPane, JLayeredPane.DEFAULT_LAYER);

        dropOverlay = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(30, 30, 30, 200));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.setColor(ACCENT);
                g2.setStroke(new BasicStroke(3, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                        0, new float[]{12, 8}, 0));
                int pad = 40;
                g2.drawRoundRect(pad, pad, getWidth() - pad * 2, getHeight() - pad * 2, 20, 20);
                g2.setFont(getFont().deriveFont(Font.BOLD, 22f));
                String text = "Drop JAR files here";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(text, (getWidth() - fm.stringWidth(text)) / 2, getHeight() / 2);
                g2.dispose();
            }
        };
        dropOverlay.setOpaque(false);
        dropOverlay.setVisible(false);
        layered.add(dropOverlay, JLayeredPane.PALETTE_LAYER);

        add(layered, BorderLayout.CENTER);

        // === BOTTOM: Status bar ===
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBackground(BG_PANEL);
        statusBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                BorderFactory.createEmptyBorder(5, 10, 5, 10)));

        globalStatus = new JLabel("Ready \u2014 load a gamepack JAR or drag one in");
        globalStatus.setForeground(TEXT_DIM);
        globalStatus.setFont(globalStatus.getFont().deriveFont(12f));
        statusBar.add(globalStatus, BorderLayout.WEST);

        JLabel version = new JLabel("HookFinder v1.0  ");
        version.setForeground(new Color(80, 80, 80));
        version.setFont(version.getFont().deriveFont(11f));
        statusBar.add(version, BorderLayout.EAST);

        add(statusBar, BorderLayout.SOUTH);
    }

    // ==================== Pattern Manager ====================

    private JPanel buildPatternManager() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(BG_PANEL);
        panel.setPreferredSize(new Dimension(220, 0));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel("PATTERNS");
        title.setForeground(ACCENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 11f));
        panel.add(title, BorderLayout.NORTH);

        patternListModel = new DefaultListModel<>();
        refreshPatternList();

        patternList = new JList<>(patternListModel);
        patternList.setBackground(BG_DARK);
        patternList.setSelectionBackground(ACCENT);
        patternList.setSelectionForeground(Color.WHITE);
        patternList.setFixedCellHeight(28);
        patternList.setFont(patternList.getFont().deriveFont(12f));

        patternList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    onEditPattern();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(patternList);
        listScroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(listScroll, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new GridLayout(2, 2, 4, 4));
        buttons.setBackground(BG_PANEL);
        buttons.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));

        JButton addBtn = createSmallButton("+ New");
        addBtn.addActionListener(e -> onAddPattern());
        buttons.add(addBtn);

        JButton editBtn = createSmallButton("Edit");
        editBtn.addActionListener(e -> onEditPattern());
        buttons.add(editBtn);

        JButton renameBtn = createSmallButton("Rename");
        renameBtn.addActionListener(e -> onRenamePattern());
        buttons.add(renameBtn);

        JButton deleteBtn = createSmallButton("Delete");
        deleteBtn.setForeground(new Color(255, 100, 100));
        deleteBtn.addActionListener(e -> onDeletePattern());
        buttons.add(deleteBtn);

        panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshPatternList() {
        patternListModel.clear();
        for (PatternDefinition def : patternDefinitions) {
            String prefix = def.isEnabled() ? "\u2611 " : "\u2610 ";
            patternListModel.addElement(prefix + def.getName());
        }
    }

    private void onAddPattern() {
        PatternDefinition result = PatternEditorPanel.showDialog(this, "New Pattern", null);
        if (result != null) {
            patternDefinitions.add(result);
            patternStore.save(patternDefinitions);
            refreshPatternList();
            globalStatus.setText("\u2713 Pattern added: " + result.getName());
            globalStatus.setForeground(ACCENT_GREEN);
        }
    }

    private void onEditPattern() {
        int idx = patternList.getSelectedIndex();
        if (idx < 0) {
            globalStatus.setText("\u26A0 Select a pattern to edit");
            globalStatus.setForeground(Color.YELLOW);
            return;
        }
        PatternDefinition existing = patternDefinitions.get(idx);
        PatternDefinition result = PatternEditorPanel.showDialog(this, "Edit: " + existing.getName(), existing);
        if (result != null) {
            patternDefinitions.set(idx, result);
            patternStore.save(patternDefinitions);
            refreshPatternList();
            globalStatus.setText("\u2713 Pattern updated: " + result.getName());
            globalStatus.setForeground(ACCENT_GREEN);
        }
    }

    private void onRenamePattern() {
        int idx = patternList.getSelectedIndex();
        if (idx < 0) {
            globalStatus.setText("\u26A0 Select a pattern to rename");
            globalStatus.setForeground(Color.YELLOW);
            return;
        }
        PatternDefinition def = patternDefinitions.get(idx);
        String newName = JOptionPane.showInputDialog(this, "New name:", def.getName());
        if (newName != null && !newName.trim().isEmpty()) {
            def.setName(newName.trim());
            patternStore.save(patternDefinitions);
            refreshPatternList();
            globalStatus.setText("\u2713 Renamed to: " + newName.trim());
            globalStatus.setForeground(ACCENT_GREEN);
        }
    }

    private void onDeletePattern() {
        int idx = patternList.getSelectedIndex();
        if (idx < 0) {
            globalStatus.setText("\u26A0 Select a pattern to delete");
            globalStatus.setForeground(Color.YELLOW);
            return;
        }
        PatternDefinition def = patternDefinitions.get(idx);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Delete pattern '" + def.getName() + "'?", "Confirm Delete",
                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            patternDefinitions.remove(idx);
            patternStore.save(patternDefinitions);
            refreshPatternList();
            globalStatus.setText("Deleted: " + def.getName());
            globalStatus.setForeground(TEXT_DIM);
        }
    }

    // ==================== RuneLite Integration ====================

    private void onLoadFromRuneLite() {
        globalStatus.setText("Searching for RuneLite gamepacks...");
        globalStatus.setForeground(ACCENT);

        SwingWorker<List<File>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<File> doInBackground() {
                return RuneLiteLocator.findGamepacks();
            }

            @Override
            protected void done() {
                try {
                    List<File> gamepacks = get();

                    if (gamepacks.isEmpty()) {
                        // No gamepacks found — offer to browse manually
                        File rlDir = RuneLiteLocator.findRuneLiteDir();
                        String msg = "No gamepack JARs found in RuneLite cache.\n\n";
                        if (rlDir != null) {
                            msg += "RuneLite directory found at:\n" + rlDir.getAbsolutePath()
                                    + "\n\nBut no gamepack files were detected inside.";
                        } else {
                            msg += "RuneLite directory not found.\n"
                                    + "Checked: ~/.runelite/";
                        }
                        msg += "\n\nWould you like to browse manually?";

                        int choice = JOptionPane.showConfirmDialog(HookFinderGUI.this,
                                msg, "RuneLite Not Found",
                                JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

                        if (choice == JOptionPane.YES_OPTION) {
                            onLoadViaChooser();
                        }

                        globalStatus.setText("RuneLite gamepack not found");
                        globalStatus.setForeground(Color.YELLOW);
                        return;
                    }

                    if (gamepacks.size() == 1) {
                        // Only one found — load it directly
                        openGamepack(gamepacks.get(0));
                        return;
                    }

                    // Multiple found — show picker dialog
                    showGamepackPicker(gamepacks);

                } catch (Exception ex) {
                    globalStatus.setText("\u2717 RuneLite scan failed: " + ex.getMessage());
                    globalStatus.setForeground(Color.RED);
                }
            }
        };
        worker.execute();
    }

    private void showGamepackPicker(List<File> gamepacks) {
        JPanel pickerPanel = new JPanel(new BorderLayout(0, 8));
        pickerPanel.setBackground(BG_PANEL);
        pickerPanel.setPreferredSize(new Dimension(500, 300));

        JLabel info = new JLabel("Found " + gamepacks.size()
                + " gamepack(s) in RuneLite cache. Select one or more:");
        info.setForeground(TEXT_DIM);
        pickerPanel.add(info, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        for (File f : gamepacks) {
            model.addElement(RuneLiteLocator.getLabel(f));
        }

        JList<String> list = new JList<>(model);
        list.setBackground(BG_DARK);
        list.setSelectionBackground(ACCENT);
        list.setSelectionForeground(Color.WHITE);
        list.setFixedCellHeight(32);
        list.setFont(list.getFont().deriveFont(13f));
        list.setSelectedIndex(0);

        // Custom renderer with path tooltip
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> l, Object value,
                                                          int index, boolean sel, boolean focus) {
                JLabel lbl = (JLabel) super.getListCellRendererComponent(l, value, index, sel, focus);
                if (!sel) {
                    lbl.setBackground(index % 2 == 0 ? BG_DARK : BG_PANEL);
                }
                lbl.setToolTipText(gamepacks.get(index).getAbsolutePath());
                lbl.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
                return lbl;
            }
        });

        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        pickerPanel.add(scroll, BorderLayout.CENTER);

        // Path preview
        JLabel pathPreview = new JLabel(gamepacks.get(0).getAbsolutePath());
        pathPreview.setForeground(TEXT_DIM);
        pathPreview.setFont(pathPreview.getFont().deriveFont(11f));
        pickerPanel.add(pathPreview, BorderLayout.SOUTH);

        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int idx = list.getSelectedIndex();
                if (idx >= 0) {
                    pathPreview.setText(gamepacks.get(idx).getAbsolutePath());
                }
            }
        });

        int result = JOptionPane.showConfirmDialog(this, pickerPanel,
                "Select RuneLite Gamepack",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int[] selected = list.getSelectedIndices();
            if (selected.length == 0) {
                // Default to first
                openGamepack(gamepacks.get(0));
            } else {
                for (int idx : selected) {
                    openGamepack(gamepacks.get(idx));
                }
            }
        }
    }

    // ==================== Drag & Drop ====================

    private void enableDragAndDrop() {
        new DropTarget(this, DnDConstants.ACTION_COPY, new DropTargetAdapter() {
            @Override
            public void dragEnter(DropTargetDragEvent e) {
                dropOverlay.setVisible(true);
            }

            @Override
            public void dragExit(DropTargetEvent e) {
                dropOverlay.setVisible(false);
            }

            @Override
            public void drop(DropTargetDropEvent event) {
                dropOverlay.setVisible(false);
                try {
                    event.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> files = (List<File>) event.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    int loaded = 0;
                    for (File f : files) {
                        if (f.getName().endsWith(".jar")) {
                            openGamepack(f);
                            loaded++;
                        }
                    }
                    if (loaded == 0) {
                        globalStatus.setText("\u26A0 No JAR files in drop");
                        globalStatus.setForeground(Color.YELLOW);
                    }
                    event.dropComplete(true);
                } catch (Exception ex) {
                    event.dropComplete(false);
                    globalStatus.setText("\u2717 Drop failed: " + ex.getMessage());
                    globalStatus.setForeground(Color.RED);
                }
            }
        }, true);
    }

    // ==================== File Chooser ====================

    private void onLoadViaChooser() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("JAR Files (*.jar)", "jar"));
        chooser.setMultiSelectionEnabled(true);

        String lastDir = prefs.get(PREF_LAST_DIR, null);
        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.isDirectory()) {
                chooser.setCurrentDirectory(dir);
            }
        }

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;

        prefs.put(PREF_LAST_DIR, chooser.getCurrentDirectory().getAbsolutePath());
        for (File f : chooser.getSelectedFiles()) {
            openGamepack(f);
        }
    }

    // ==================== Gamepack Tabs ====================

    private void openGamepack(File jarFile) {
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            if (jarFile.getAbsolutePath().equals(tabbedPane.getToolTipTextAt(i))) {
                tabbedPane.setSelectedIndex(i);
                globalStatus.setText("Already open: " + jarFile.getName());
                globalStatus.setForeground(TEXT_DIM);
                return;
            }
        }

        GamepackTab tab = new GamepackTab(jarFile);
        int idx = tabbedPane.getTabCount();
        tabbedPane.addTab(jarFile.getName(), null, tab, jarFile.getAbsolutePath());
        tabbedPane.setTabComponentAt(idx, createTabHeader(jarFile.getName(), tab));
        tabbedPane.setSelectedIndex(idx);

        globalStatus.setText("\u2713 Opened: " + jarFile.getAbsolutePath());
        globalStatus.setForeground(ACCENT_GREEN);
    }

    private JPanel createTabHeader(String title, GamepackTab tab) {
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        header.setOpaque(false);

        JLabel label = new JLabel(title);
        label.setFont(label.getFont().deriveFont(12f));
        header.add(label);

        JButton closeBtn = new JButton("\u2715");
        closeBtn.setFont(closeBtn.getFont().deriveFont(Font.BOLD, 11f));
        closeBtn.setMargin(new Insets(0, 4, 0, 4));
        closeBtn.setFocusable(false);
        closeBtn.setBorderPainted(false);
        closeBtn.setContentAreaFilled(false);
        closeBtn.setForeground(TEXT_DIM);
        closeBtn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseEntered(java.awt.event.MouseEvent e) {
                closeBtn.setForeground(Color.RED);
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                closeBtn.setForeground(TEXT_DIM);
            }
        });
        closeBtn.addActionListener(e -> {
            int i = tabbedPane.indexOfComponent(tab);
            if (i != -1) {
                tab.cleanup();
                tabbedPane.removeTabAt(i);
                globalStatus.setText("Closed tab");
                globalStatus.setForeground(TEXT_DIM);
            }
        });
        header.add(closeBtn);
        return header;
    }

    // ==================== Per-Tab Panel ====================

    private class GamepackTab extends JPanel {
        private final File jarFile;
        private URLClassLoader classLoader;
        private HookFinder hookFinder;
        private DefaultTableModel tableModel;
        private JLabel tabStatus;
        private JButton runBtn;

        GamepackTab(File jarFile) {
            this.jarFile = jarFile;
            setLayout(new BorderLayout(0, 0));
            setBackground(BG_DARK);
            buildUI();
            loadJar();
        }

        private void buildUI() {
            tableModel = new DefaultTableModel(
                    new String[]{"Pattern", "Class", "Method", "Details"}, 0) {
                @Override
                public boolean isCellEditable(int r, int c) {
                    return false;
                }
            };

            JTable table = new JTable(tableModel);
            table.setAutoCreateRowSorter(true);
            table.setRowHeight(26);
            table.setShowGrid(false);
            table.setIntercellSpacing(new Dimension(0, 1));
            table.getColumnModel().getColumn(0).setPreferredWidth(160);
            table.getColumnModel().getColumn(1).setPreferredWidth(100);
            table.getColumnModel().getColumn(2).setPreferredWidth(80);
            table.getColumnModel().getColumn(3).setPreferredWidth(380);

            table.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
                @Override
                public Component getTableCellRendererComponent(JTable t, Object val,
                                                               boolean sel, boolean focus, int row, int col) {
                    Component c = super.getTableCellRendererComponent(t, val, sel, focus, row, col);
                    if (!sel) {
                        c.setBackground(row % 2 == 0 ? BG_DARK : BG_PANEL);
                    }
                    return c;
                }
            });

            JScrollPane tableScroll = new JScrollPane(table);
            tableScroll.setBorder(null);
            add(tableScroll, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new BorderLayout(10, 0));
            bottom.setBackground(BG_PANEL);
            bottom.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER_COLOR),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)));

            runBtn = createButton("  \uD83D\uDD0D  Run Analysis  ");
            runBtn.setFont(runBtn.getFont().deriveFont(Font.BOLD, 13f));
            runBtn.setEnabled(false);
            runBtn.addActionListener(e -> onRun());
            bottom.add(runBtn, BorderLayout.WEST);

            tabStatus = new JLabel("Loading...");
            tabStatus.setForeground(TEXT_DIM);
            tabStatus.setFont(tabStatus.getFont().deriveFont(12f));
            tabStatus.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 0));
            bottom.add(tabStatus, BorderLayout.CENTER);

            add(bottom, BorderLayout.SOUTH);
        }

        private void loadJar() {
            try {
                URL[] urls = {jarFile.toURI().toURL()};
                classLoader = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
                hookFinder = new HookFinder(classLoader, jarFile);
                hookFinder.registerBuiltinPatterns();
                hookFinder.registerCustomPatterns(patternDefinitions);

                runBtn.setEnabled(true);
                tabStatus.setText("Ready \u2014 " + jarFile.getName()
                        + " (" + hookFinder.getPatterns().size() + " patterns)");
                tabStatus.setForeground(ACCENT_GREEN);
            } catch (Exception ex) {
                tabStatus.setText("\u2717 Failed: " + ex.getMessage());
                tabStatus.setForeground(Color.RED);
            }
        }

        private void onRun() {
            if (hookFinder == null) return;

            hookFinder.getPatterns().clear();
            hookFinder.registerBuiltinPatterns();
            hookFinder.registerCustomPatterns(patternDefinitions);

            List<HookPattern> enabled = hookFinder.getPatterns();
            if (enabled.isEmpty()) {
                tabStatus.setText("\u26A0 No patterns available");
                tabStatus.setForeground(Color.YELLOW);
                return;
            }

            tableModel.setRowCount(0);
            runBtn.setEnabled(false);
            tabStatus.setText("\u23F3 Analyzing...");
            tabStatus.setForeground(ACCENT);

            SwingWorker<List<HookResult>, Void> worker = new SwingWorker<>() {
                long start;

                @Override
                protected List<HookResult> doInBackground() throws Exception {
                    start = System.currentTimeMillis();
                    return hookFinder.run(enabled);
                }

                @Override
                protected void done() {
                    try {
                        List<HookResult> results = get();
                        long elapsed = System.currentTimeMillis() - start;

                        for (HookResult r : results) {
                            StringBuilder details = new StringBuilder();
                            for (Map.Entry<String, String> e : r.getDetails().entrySet()) {
                                if (details.length() > 0) details.append("  \u2502  ");
                                details.append(e.getKey()).append(": ").append(e.getValue());
                            }
                            tableModel.addRow(new Object[]{
                                    r.getPatternName(), r.getClassName(),
                                    r.getMethodName(), details.toString()
                            });
                        }

                        tabStatus.setText(String.format(
                                "\u2713 %d classes (%d failed)  \u2502  %d hooks  \u2502  %.2fs",
                                hookFinder.getLoadedClassCount(),
                                hookFinder.getFailedClassCount(),
                                results.size(), elapsed / 1000.0));
                        tabStatus.setForeground(ACCENT_GREEN);

                        globalStatus.setText("\u2713 " + jarFile.getName()
                                + " \u2014 " + results.size() + " hooks found");
                        globalStatus.setForeground(ACCENT_GREEN);
                    } catch (Exception ex) {
                        tabStatus.setText("\u2717 Error: " + ex.getMessage());
                        tabStatus.setForeground(Color.RED);
                    } finally {
                        runBtn.setEnabled(true);
                    }
                }
            };
            worker.execute();
        }

        void cleanup() {
            try {
                if (classLoader != null) classLoader.close();
            } catch (Exception ignored) {
            }
        }
    }

    // ==================== Helpers ====================

    private static JButton createButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    private static JButton createSmallButton(String text) {
        JButton btn = new JButton(text);
        btn.setFocusPainted(false);
        btn.setFont(btn.getFont().deriveFont(11f));
        btn.putClientProperty("JButton.buttonType", "roundRect");
        return btn;
    }

    // ==================== Entry Point ====================

    public static void main(String[] args) {
        FlatDarkLaf.setup();

        UIManager.put("Component.arc", 8);
        UIManager.put("Button.arc", 8);
        UIManager.put("TextComponent.arc", 6);
        UIManager.put("TabbedPane.showTabSeparators", true);
        UIManager.put("TabbedPane.selectedBackground", BG_PANEL);
        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.thumbInsets", new Insets(2, 2, 2, 2));

        SwingUtilities.invokeLater(() -> new HookFinderGUI().setVisible(true));
    }
}