/*
 * Copyright (c) 2026, KogasaPls
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notificationpanel.ui;

import com.notificationpanel.rules.NotificationRule;
import com.notificationpanel.rules.RuleConfigStore;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JColorChooser;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.ListCellRenderer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public final class RuleEditorPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_ERROR = "Rule editor mutations must run on the EDT.";

	private final RuleEditorController controller;
	private final BufferedImage navigationIcon;
	private RuleListView listView;
	private RuleEditView editView;
	private UUID editingId;

	public RuleEditorPanel(RuleEditorController controller)
	{
		requireEdt();
		this.controller = Objects.requireNonNull(controller, "controller");
		navigationIcon = createNavigationIcon();
		setLayout(new BorderLayout());
		renderList();
	}

	public BufferedImage getNavigationIcon()
	{
		requireEdt();
		return navigationIcon;
	}

	public void showNewRule()
	{
		requireEdt();
		if (controller.hasBlockingError() || controller.getRules().size() >= 100)
		{
			return;
		}
		editingId = null;
		renderEditor(controller.newDraft());
	}

	public void reload(RuleConfigStore.LoadResult loadResult)
	{
		requireEdt();
		controller.reload(loadResult);
		renderList();
	}

	void setDraftForTest(String name, String pattern, boolean enabled, Integer backgroundRgb,
		Integer opacityPercent, NotificationRule.Visibility visibility)
	{
		requireEdt();
		requireEditor().setDraft(name, pattern, enabled, backgroundRgb, opacityPercent, visibility);
	}

	boolean isSaveEnabledForTest()
	{
		requireEdt();
		return requireEditor().saveButton.isEnabled();
	}

	String getValidationTextForTest()
	{
		requireEdt();
		return requireEditor().validationLabel.getText();
	}

	void clickSaveForTest()
	{
		requireEdt();
		requireEditor().saveButton.doClick();
	}

	void clickCancelForTest()
	{
		requireEdt();
		requireEditor().cancelButton.doClick();
	}

	boolean isShowingListForTest()
	{
		requireEdt();
		return listView != null && editView == null;
	}

	void selectRuleForTest(UUID id)
	{
		requireEdt();
		requireList().select(id);
	}

	void showSelectedRuleForTest()
	{
		requireEdt();
		showSelectedRule();
	}

	void handleDeleteAnswerForTest(int answer)
	{
		requireEdt();
		handleDeleteAnswer(answer);
	}

	String getListTextForTest()
	{
		requireEdt();
		return requireList().visibleText();
	}

	boolean isEditEnabledForTest()
	{
		requireEdt();
		return requireList().editButton.isEnabled();
	}

	boolean isUpEnabledForTest()
	{
		requireEdt();
		return requireList().upButton.isEnabled();
	}

	boolean isDownEnabledForTest()
	{
		requireEdt();
		return requireList().downButton.isEnabled();
	}

	boolean isAddEnabledForTest()
	{
		requireEdt();
		return requireList().addButton.isEnabled();
	}

	boolean isBlockingBannerVisibleForTest()
	{
		requireEdt();
		return requireList().blockingBanner.isVisible();
	}

	boolean isResetVisibleForTest()
	{
		requireEdt();
		return requireList().resetButton.isVisible();
	}

	void clickResetForTest()
	{
		requireEdt();
		requireList().resetButton.doClick();
	}

	String getActionErrorTextForTest()
	{
		requireEdt();
		return requireList().actionError.getText();
	}

	private void renderList()
	{
		removeAll();
		editingId = null;
		editView = null;
		listView = new RuleListView(this, controller);
		add(listView, BorderLayout.CENTER);
		revalidate();
		repaint();
	}

	private void renderEditor(NotificationRule draft)
	{
		removeAll();
		listView = null;
		editView = new RuleEditView(this, draft);
		add(editView, BorderLayout.CENTER);
		validateEditor();
		revalidate();
		repaint();
	}

	private void showSelectedRule()
	{
		NotificationRule selected = selectedRule();
		if (selected == null)
		{
			return;
		}
		editingId = selected.getId();
		renderEditor(selected);
	}

	private void saveDraft()
	{
		RuleEditView editor = requireEditor();
		NotificationRule draft = editor.buildDraft();
		RuleEditorController.SaveResult result = editingId == null
			? controller.add(draft) : controller.edit(editingId, draft);
		if (result.isSuccess())
		{
			renderList();
		}
		else
		{
			editor.showErrors(result.getErrors());
		}
	}

	private void validateEditor()
	{
		if (editView == null)
		{
			return;
		}
		editView.showErrors(controller.validateForEditor(editView.buildDraft()));
	}

	private void toggleSelected()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			afterMutation(controller.setEnabled(selected.getId(), !selected.isEnabled()));
		}
	}

	private void moveSelectedUp()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			afterMutation(controller.moveUp(selected.getId()));
		}
	}

	private void moveSelectedDown()
	{
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			afterMutation(controller.moveDown(selected.getId()));
		}
	}

	private void confirmDelete()
	{
		NotificationRule rule = selectedRule();
		if (rule == null)
		{
			return;
		}
		int answer = JOptionPane.showConfirmDialog(
			this,
			"Delete rule \"" + rule.getName() + "\"?",
			"Delete notification rule",
			JOptionPane.OK_CANCEL_OPTION,
			JOptionPane.WARNING_MESSAGE);
		handleDeleteAnswer(answer);
	}

	private void handleDeleteAnswer(int answer)
	{
		if (answer != JOptionPane.OK_OPTION)
		{
			return;
		}
		NotificationRule selected = selectedRule();
		if (selected != null)
		{
			afterMutation(controller.delete(selected.getId()));
		}
	}

	private void resetRules()
	{
		RuleEditorController.SaveResult result = controller.reset();
		renderList();
		if (!result.isSuccess())
		{
			requireList().showActionErrors(result.getErrors());
		}
	}

	private void afterMutation(RuleEditorController.SaveResult result)
	{
		if (result.isSuccess())
		{
			renderList();
		}
		else
		{
			requireList().showActionErrors(result.getErrors());
		}
	}

	private NotificationRule selectedRule()
	{
		return listView == null ? null : listView.ruleList.getSelectedValue();
	}

	private RuleListView requireList()
	{
		if (listView == null)
		{
			throw new IllegalStateException("The rule list is not visible.");
		}
		return listView;
	}

	private RuleEditView requireEditor()
	{
		if (editView == null)
		{
			throw new IllegalStateException("The rule editor is not visible.");
		}
		return editView;
	}

	private static BufferedImage createNavigationIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		try
		{
			graphics.setColor(Color.WHITE);
			for (int y : new int[]{4, 8, 12})
			{
				graphics.fillRect(2, y, 12, 1);
				graphics.fillRect(2, y - 1, 2, 3);
			}
		}
		finally
		{
			graphics.dispose();
		}
		return icon;
	}

	private static void requireEdt()
	{
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
		}
	}

	private static final class RuleListView extends JPanel
	{
		private static final long serialVersionUID = 1L;

		private final RuleEditorPanel owner;
		private final DefaultListModel<NotificationRule> model = new DefaultListModel<>();
		private final JList<NotificationRule> ruleList = new JList<>(model);
		private final JButton addButton = new JButton("Add");
		private final JButton editButton = new JButton("Edit");
		private final JButton toggleButton = new JButton("Enable");
		private final JButton upButton = new JButton("Up");
		private final JButton downButton = new JButton("Down");
		private final JButton deleteButton = new JButton("Delete");
		private final JLabel blockingBanner = new JLabel();
		private final JButton resetButton = new JButton("Reset rules");
		private final JLabel actionError = new JLabel();

		private RuleListView(RuleEditorPanel owner, RuleEditorController controller)
		{
			this.owner = owner;
			setLayout(new BorderLayout(0, 6));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			JPanel heading = new JPanel();
			heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
			heading.setOpaque(false);
			JLabel title = new JLabel("Notification rules");
			title.setForeground(ColorScheme.TEXT_COLOR);
			heading.add(title);
			blockingBanner.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			blockingBanner.setText(controller.hasBlockingError()
				? controller.getBlockingError() : "");
			blockingBanner.setVisible(controller.hasBlockingError());
			heading.add(blockingBanner);
			resetButton.setVisible(controller.hasBlockingError());
			resetButton.addActionListener(event -> owner.resetRules());
			heading.add(resetButton);
			actionError.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			actionError.setVisible(false);
			heading.add(actionError);
			add(heading, BorderLayout.NORTH);

			for (NotificationRule rule : controller.getRules())
			{
				model.addElement(rule);
			}
			ruleList.setCellRenderer(renderer());
			ruleList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
			ruleList.addListSelectionListener(event ->
			{
				if (!event.getValueIsAdjusting())
				{
					updateButtons(controller.hasBlockingError());
				}
			});
			add(new JScrollPane(ruleList), BorderLayout.CENTER);

			JPanel actions = new JPanel(new GridLayout(3, 2, 4, 4));
			actions.setOpaque(false);
			actions.add(addButton);
			actions.add(editButton);
			actions.add(toggleButton);
			actions.add(upButton);
			actions.add(downButton);
			actions.add(deleteButton);
			add(actions, BorderLayout.SOUTH);

			addButton.addActionListener(event -> owner.showNewRule());
			editButton.addActionListener(event -> owner.showSelectedRule());
			toggleButton.addActionListener(event -> owner.toggleSelected());
			upButton.addActionListener(event -> owner.moveSelectedUp());
			downButton.addActionListener(event -> owner.moveSelectedDown());
			deleteButton.addActionListener(event -> owner.confirmDelete());
			addButton.setEnabled(!controller.hasBlockingError() && model.size() < 100);
			updateButtons(controller.hasBlockingError());
		}

		private void select(UUID id)
		{
			for (int index = 0; index < model.size(); index++)
			{
				if (model.get(index).getId().equals(id))
				{
					ruleList.setSelectedIndex(index);
					return;
				}
			}
			ruleList.clearSelection();
		}

		private void updateButtons(boolean blocked)
		{
			int index = ruleList.getSelectedIndex();
			boolean selected = !blocked && index >= 0;
			editButton.setEnabled(selected);
			toggleButton.setEnabled(selected);
			upButton.setEnabled(selected && index > 0);
			downButton.setEnabled(selected && index < model.size() - 1);
			deleteButton.setEnabled(selected);
			if (selected)
			{
				toggleButton.setText(model.get(index).isEnabled() ? "Disable" : "Enable");
			}
			else
			{
				toggleButton.setText("Enable");
			}
		}

		private void showActionErrors(List<String> errors)
		{
			actionError.setText(String.join(" ", errors));
			actionError.setVisible(true);
		}

		private String visibleText()
		{
			StringBuilder text = new StringBuilder();
			ListCellRenderer<? super NotificationRule> cellRenderer = ruleList.getCellRenderer();
			for (int index = 0; index < model.size(); index++)
			{
				Component component = cellRenderer.getListCellRendererComponent(ruleList,
					model.get(index), index, false, false);
				appendLabelText(component, text);
			}
			return text.toString();
		}

		private static ListCellRenderer<NotificationRule> renderer()
		{
			return (list, rule, index, selected, focused) ->
			{
				JPanel row = new JPanel();
				row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
				row.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
				row.setBackground(selected
					? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
				JLabel name = new JLabel((rule.isEnabled() ? "Enabled: " : "Disabled: ")
					+ safe(rule.getName()));
				name.setForeground(ColorScheme.TEXT_COLOR);
				row.add(name);
				JLabel pattern = new JLabel("Pattern: " + patternPreview(rule.getPattern()));
				pattern.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.add(pattern);
				JLabel style = new JLabel("Style: " + styleSummary(rule));
				style.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				row.add(style);
				if (rule.getMigrationNote() != null)
				{
					JLabel warning = new JLabel("Warning: " + safe(rule.getMigrationNote()));
					warning.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
					row.add(warning);
				}
				return row;
			};
		}

		private static void appendLabelText(Component component, StringBuilder text)
		{
			if (component instanceof JLabel)
			{
				text.append(((JLabel) component).getText()).append('\n');
			}
			if (component instanceof JPanel)
			{
				for (Component child : ((JPanel) component).getComponents())
				{
					appendLabelText(child, text);
				}
			}
		}

		private static String patternPreview(String pattern)
		{
			String escaped = safe(pattern)
				.replace("\\", "\\\\")
				.replace("\r", "\\r")
				.replace("\n", "\\n");
			int end = escaped.offsetByCodePoints(0,
				Math.min(48, escaped.codePointCount(0, escaped.length())));
			return escaped.substring(0, end)
				+ (end < escaped.length() ? "…" : "");
		}

		private static String styleSummary(NotificationRule rule)
		{
			StringBuilder summary = new StringBuilder();
			if (rule.getBackgroundRgb() != null)
			{
				summary.append(String.format("#%06X", rule.getBackgroundRgb()));
			}
			if (rule.getOpacityPercent() != null)
			{
				appendSeparator(summary);
				summary.append(rule.getOpacityPercent()).append('%');
			}
			if (rule.getVisibility() != NotificationRule.Visibility.INHERIT)
			{
				appendSeparator(summary);
				summary.append(rule.getVisibility());
			}
			return summary.length() == 0 ? "inherit defaults" : summary.toString();
		}

		private static void appendSeparator(StringBuilder summary)
		{
			if (summary.length() > 0)
			{
				summary.append(", ");
			}
		}

		private static String safe(String value)
		{
			return value == null ? "" : value;
		}
	}

	private static final class RuleEditView extends JPanel
	{
		private static final long serialVersionUID = 1L;

		private final RuleEditorPanel owner;
		private final UUID draftId;
		private final JTextField nameField = new JTextField();
		private final JCheckBox enabledCheckBox = new JCheckBox("Enabled");
		private final JTextField patternField = new JTextField();
		private final JCheckBox backgroundCheckBox = new JCheckBox("Background");
		private final JButton backgroundButton = new JButton("Choose color");
		private final JCheckBox opacityCheckBox = new JCheckBox("Opacity");
		private final JSpinner opacitySpinner =
			new JSpinner(new SpinnerNumberModel(100, 0, 100, 1));
		private final JComboBox<NotificationRule.Visibility> visibilityComboBox =
			new JComboBox<>(NotificationRule.Visibility.values());
		private final JLabel validationLabel = new JLabel();
		private final JButton saveButton = new JButton("Save");
		private final JButton cancelButton = new JButton("Cancel");
		private Color backgroundColor = Color.BLACK;

		private RuleEditView(RuleEditorPanel owner, NotificationRule draft)
		{
			this.owner = owner;
			draftId = draft.getId();
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBackground(ColorScheme.DARK_GRAY_COLOR);

			add(label("Name"));
			add(nameField);
			enabledCheckBox.setOpaque(false);
			add(enabledCheckBox);
			add(label("Pattern"));
			add(patternField);

			JPanel backgroundRow = row();
			backgroundRow.add(backgroundCheckBox);
			backgroundRow.add(backgroundButton);
			add(backgroundRow);
			JPanel opacityRow = row();
			opacityRow.add(opacityCheckBox);
			opacityRow.add(opacitySpinner);
			add(opacityRow);
			add(label("Visibility"));
			add(visibilityComboBox);

			validationLabel.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
			add(validationLabel);
			JPanel actions = row();
			actions.add(saveButton);
			actions.add(cancelButton);
			add(actions);

			nameField.setText(safe(draft.getName()));
			enabledCheckBox.setSelected(draft.isEnabled());
			patternField.setText(safe(draft.getPattern()));
			backgroundCheckBox.setSelected(draft.getBackgroundRgb() != null);
			if (draft.getBackgroundRgb() != null)
			{
				backgroundColor = new Color(draft.getBackgroundRgb());
			}
			opacityCheckBox.setSelected(draft.getOpacityPercent() != null);
			opacitySpinner.setValue(draft.getOpacityPercent() == null
				? 100 : draft.getOpacityPercent());
			visibilityComboBox.setSelectedItem(draft.getVisibility());
			updateOptionalControls();

			DocumentListener documentListener = new DocumentListener()
			{
				@Override
				public void insertUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}

				@Override
				public void removeUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}

				@Override
				public void changedUpdate(DocumentEvent event)
				{
					owner.validateEditor();
				}
			};
			nameField.getDocument().addDocumentListener(documentListener);
			patternField.getDocument().addDocumentListener(documentListener);
			enabledCheckBox.addChangeListener(event -> owner.validateEditor());
			backgroundCheckBox.addChangeListener(event ->
			{
				updateOptionalControls();
				owner.validateEditor();
			});
			opacityCheckBox.addChangeListener(event ->
			{
				updateOptionalControls();
				owner.validateEditor();
			});
			opacitySpinner.addChangeListener(event -> owner.validateEditor());
			visibilityComboBox.addActionListener(event -> owner.validateEditor());
			backgroundButton.addActionListener(event -> chooseBackground());
			saveButton.addActionListener(event -> owner.saveDraft());
			cancelButton.addActionListener(event -> owner.renderList());
		}

		private NotificationRule buildDraft()
		{
			return new NotificationRule(draftId, nameField.getText(), enabledCheckBox.isSelected(),
				patternField.getText(),
				backgroundCheckBox.isSelected() ? backgroundColor.getRGB() & 0xFFFFFF : null,
				opacityCheckBox.isSelected() ? (Integer) opacitySpinner.getValue() : null,
				(NotificationRule.Visibility) visibilityComboBox.getSelectedItem(), null);
		}

		private void setDraft(String name, String pattern, boolean enabled, Integer backgroundRgb,
			Integer opacityPercent, NotificationRule.Visibility visibility)
		{
			nameField.setText(safe(name));
			patternField.setText(safe(pattern));
			enabledCheckBox.setSelected(enabled);
			backgroundCheckBox.setSelected(backgroundRgb != null);
			if (backgroundRgb != null)
			{
				backgroundColor = new Color(backgroundRgb);
			}
			opacityCheckBox.setSelected(opacityPercent != null);
			if (opacityPercent != null)
			{
				opacitySpinner.setValue(opacityPercent);
			}
			visibilityComboBox.setSelectedItem(Objects.requireNonNull(visibility, "visibility"));
			updateOptionalControls();
			owner.validateEditor();
		}

		private void showErrors(List<String> errors)
		{
			validationLabel.setText(String.join(" ", errors));
			saveButton.setEnabled(errors.isEmpty());
		}

		private void chooseBackground()
		{
			Color chosen = JColorChooser.showDialog(this, "Choose rule background",
				backgroundColor);
			if (chosen != null)
			{
				backgroundColor = chosen;
				backgroundCheckBox.setSelected(true);
				owner.validateEditor();
			}
		}

		private void updateOptionalControls()
		{
			backgroundButton.setEnabled(backgroundCheckBox.isSelected());
			opacitySpinner.setEnabled(opacityCheckBox.isSelected());
		}

		private static JLabel label(String text)
		{
			JLabel label = new JLabel(text);
			label.setForeground(ColorScheme.TEXT_COLOR);
			return label;
		}

		private static JPanel row()
		{
			JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
			row.setOpaque(false);
			row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
			return row;
		}

		private static String safe(String value)
		{
			return value == null ? "" : value;
		}
	}
}
