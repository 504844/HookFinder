package com.hookfinder;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI panel for editing a single PatternDefinition.
 * Shown in a dialog when creating or editing a pattern.
 */
public class PatternEditorPanel extends JPanel {

    private final JTextField nameField;
    private final JTextArea descField;
    private final JComboBox<PatternDefinition.Visibility> visibilityCombo;
    private final JComboBox<PatternDefinition.Scope> scopeCombo;
    private final JCheckBox finalCheck;
    private final JComboBox<PatternDefinition.ReturnType> returnTypeCombo;
    private final JSpinner paramCountSpinner;
    private final JTextField paramTypesField;
    private final JSpinner minInstrSpinner;
    private final JSpinner maxInstrSpinner;
    private final JTextField fieldAccessField;
    private final JTextField methodCallsField;
    private final JCheckBox extractJunkCheck;

    private static final Color BG_PANEL     = new Color(40, 40, 40);
    private static final Color BG_INPUT     = new Color(50, 50, 50);
    private static final Color ACCENT       = new Color(79, 140, 255);
    private static final Color TEXT_DIM     = new Color(140, 140, 140);

    public PatternEditorPanel(PatternDefinition pattern) {
        setLayout(new BorderLayout(0, 10));
        setBackground(BG_PANEL);
        setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // === Identity section ===
        JPanel identityPanel = createSection("Identity");
        identityPanel.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        nameField = styledField(pattern.getName(), 20);
        addFormRow(identityPanel, gbc, 0, "Name:", nameField);

        descField = new JTextArea(pattern.getDescription(), 2, 20);
        descField.setBackground(BG_INPUT);
        descField.setLineWrap(true);
        descField.setWrapStyleWord(true);
        descField.setFont(nameField.getFont());
        JScrollPane descScroll = new JScrollPane(descField);
        descScroll.setPreferredSize(new Dimension(300, 50));
        addFormRow(identityPanel, gbc, 1, "Description:", descScroll);

        // === Signature section ===
        JPanel sigPanel = createSection("Method Signature");
        sigPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        visibilityCombo = styledCombo(PatternDefinition.Visibility.values(), pattern.getVisibility());
        addFormRow(sigPanel, gbc, 0, "Visibility:", visibilityCombo);

        scopeCombo = styledCombo(PatternDefinition.Scope.values(), pattern.getScope());
        addFormRow(sigPanel, gbc, 1, "Scope:", scopeCombo);

        finalCheck = new JCheckBox("Required", pattern.isRequireFinal());
        finalCheck.setBackground(BG_PANEL);
        addFormRow(sigPanel, gbc, 2, "Final:", finalCheck);

        returnTypeCombo = styledCombo(PatternDefinition.ReturnType.values(), pattern.getReturnType());
        addFormRow(sigPanel, gbc, 3, "Return Type:", returnTypeCombo);

        paramCountSpinner = new JSpinner(new SpinnerNumberModel(
                pattern.getParamCount(), -1, 50, 1));
        paramCountSpinner.setToolTipText("-1 = any count");
        addFormRow(sigPanel, gbc, 4, "Param Count:", paramCountSpinner);

        paramTypesField = styledField(String.join(", ", pattern.getParamTypes()), 25);
        paramTypesField.setToolTipText("Comma-separated: int, String, *, byte, long, object");
        addFormRow(sigPanel, gbc, 5, "Param Types:", paramTypesField);

        // === Bytecode section ===
        JPanel bcPanel = createSection("Bytecode Analysis");
        bcPanel.setLayout(new GridBagLayout());
        gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;

        minInstrSpinner = new JSpinner(new SpinnerNumberModel(
                pattern.getMinInstructionCount(), -1, 10000, 10));
        minInstrSpinner.setToolTipText("-1 = no minimum");
        addFormRow(bcPanel, gbc, 0, "Min Instructions:", minInstrSpinner);

        maxInstrSpinner = new JSpinner(new SpinnerNumberModel(
                pattern.getMaxInstructionCount(), -1, 10000, 10));
        maxInstrSpinner.setToolTipText("-1 = no maximum");
        addFormRow(bcPanel, gbc, 1, "Max Instructions:", maxInstrSpinner);

        fieldAccessField = styledField(String.join(", ", pattern.getRequiredFieldAccesses()), 25);
        fieldAccessField.setToolTipText("Comma-separated field names to require, e.g.: .ad, .bx");
        addFormRow(bcPanel, gbc, 2, "Required Fields:", fieldAccessField);

        methodCallsField = styledField(String.join(", ", pattern.getRequiredMethodCalls()), 25);
        methodCallsField.setToolTipText("Comma-separated method calls, e.g.: java/lang/Math.max");
        addFormRow(bcPanel, gbc, 3, "Required Calls:", methodCallsField);

        extractJunkCheck = new JCheckBox("Extract from bytecode", pattern.isExtractJunkValue());
        extractJunkCheck.setBackground(BG_PANEL);
        addFormRow(bcPanel, gbc, 4, "Junk Value:", extractJunkCheck);

        // Layout all sections vertically
        JPanel allSections = new JPanel();
        allSections.setLayout(new BoxLayout(allSections, BoxLayout.Y_AXIS));
        allSections.setBackground(BG_PANEL);
        allSections.add(identityPanel);
        allSections.add(Box.createVerticalStrut(6));
        allSections.add(sigPanel);
        allSections.add(Box.createVerticalStrut(6));
        allSections.add(bcPanel);

        JScrollPane scroll = new JScrollPane(allSections);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(BG_PANEL);
        add(scroll, BorderLayout.CENTER);
    }

    /**
     * Reads the form fields back into a PatternDefinition.
     */
    public PatternDefinition toDefinition() {
        PatternDefinition def = new PatternDefinition();
        def.setName(nameField.getText().trim());
        def.setDescription(descField.getText().trim());
        def.setVisibility((PatternDefinition.Visibility) visibilityCombo.getSelectedItem());
        def.setScope((PatternDefinition.Scope) scopeCombo.getSelectedItem());
        def.setRequireFinal(finalCheck.isSelected());
        def.setReturnType((PatternDefinition.ReturnType) returnTypeCombo.getSelectedItem());
        def.setParamCount((int) paramCountSpinner.getValue());

        String ptText = paramTypesField.getText().trim();
        if (!ptText.isEmpty()) {
            def.setParamTypes(Arrays.asList(ptText.split("\\s*,\\s*")));
        }

        def.setMinInstructionCount((int) minInstrSpinner.getValue());
        def.setMaxInstructionCount((int) maxInstrSpinner.getValue());

        String faText = fieldAccessField.getText().trim();
        if (!faText.isEmpty()) {
            def.setRequiredFieldAccesses(Arrays.asList(faText.split("\\s*,\\s*")));
        }

        String mcText = methodCallsField.getText().trim();
        if (!mcText.isEmpty()) {
            def.setRequiredMethodCalls(Arrays.asList(mcText.split("\\s*,\\s*")));
        }

        def.setExtractJunkValue(extractJunkCheck.isSelected());
        def.setEnabled(true);
        return def;
    }

    /**
     * Shows this editor in a dialog. Returns the pattern if OK, null if cancelled.
     */
    public static PatternDefinition showDialog(Component parent, String title, PatternDefinition existing) {
        PatternEditorPanel editor = new PatternEditorPanel(
                existing != null ? existing : new PatternDefinition());

        int result = JOptionPane.showConfirmDialog(parent, editor, title,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            return editor.toDefinition();
        }
        return null;
    }

    // ==================== Helpers ====================

    private JPanel createSection(String title) {
        JPanel panel = new JPanel();
        panel.setBackground(BG_PANEL);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60)),
                title);
        border.setTitleColor(ACCENT);
        border.setTitleFont(getFont().deriveFont(Font.BOLD, 11f));
        panel.setBorder(BorderFactory.createCompoundBorder(
                border, BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        return panel;
    }

    private JTextField styledField(String text, int cols) {
        JTextField field = new JTextField(text, cols);
        field.setBackground(BG_INPUT);
        return field;
    }

    private <T> JComboBox<T> styledCombo(T[] values, T selected) {
        JComboBox<T> combo = new JComboBox<>(values);
        combo.setSelectedItem(selected);
        combo.setBackground(BG_INPUT);
        return combo;
    }

    private void addFormRow(JPanel panel, GridBagConstraints gbc, int row,
                            String label, Component field) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        JLabel lbl = new JLabel(label);
        lbl.setForeground(TEXT_DIM);
        panel.add(lbl, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        panel.add(field, gbc);
    }
}