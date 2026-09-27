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
import com.notificationpanel.state.NotificationState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.materialtabs.MaterialTab;
import net.runelite.client.ui.components.materialtabs.MaterialTabGroup;

/**
 * Sidebar panel containing notification log and rule editor tabs.
 */
public final class NotificationSidebarPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_SUBJECT = "Sidebar mutations";

	public interface Actions
	{
		void clearNotifications();
	}

	private final RuleEditorController controller;
	private final NotificationLogPanel.RuleActions ruleActions = new RuleTabActions();
	private final RuleEditorPanel rulePanel;
	private final NotificationLogPanel logPanel;
	private final MaterialTabGroup tabGroup;
	private final JPanel display;
	private final MaterialTab notificationsTab;
	private final MaterialTab rulesTab;
	private final BufferedImage navigationIcon;

	public NotificationSidebarPanel(RuleEditorController controller, NotificationLog log,
		Actions actions)
	{
		super(false);
		requireEdt();
		Objects.requireNonNull(actions, "actions");
		this.navigationIcon = createNavigationIcon();
		this.controller = Objects.requireNonNull(controller, "controller");
		this.rulePanel = new RuleEditorPanel(controller);
		this.logPanel = new NotificationLogPanel(log, actions::clearNotifications, ruleActions);

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(PluginPanel.BORDER_OFFSET,
			PluginPanel.BORDER_OFFSET, PluginPanel.BORDER_OFFSET, PluginPanel.BORDER_OFFSET));

		display = new JPanel(new BorderLayout());
		display.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabGroup = new MaterialTabGroup(display);
		notificationsTab = new MaterialTab("Notifications", tabGroup, logPanel);
		rulesTab = new MaterialTab("Rules", tabGroup, rulePanel);
		tabGroup.addTab(notificationsTab);
		tabGroup.addTab(rulesTab);
		add(tabGroup, BorderLayout.NORTH);
		add(display, BorderLayout.CENTER);

		selectDefaultTab();
	}

	@Override
	public Dimension getPreferredSize()
	{
		return new Dimension(super.getPreferredSize().width, 0);
	}

	@Override
	public Dimension getMinimumSize()
	{
		return new Dimension(super.getMinimumSize().width, 0);
	}

	public BufferedImage getNavigationIcon()
	{
		requireEdt();
		return navigationIcon;
	}

	public void reload()
	{
		reload(false);
	}

	public void reload(boolean migratedElsewhere)
	{
		requireEdt();
		boolean gateWasUp = rulePanel.hasPendingMigration();
		rulePanel.reload(migratedElsewhere);
		if (!gateWasUp && rulePanel.hasPendingMigration())
		{
			select(rulesTab);
		}
	}

	public boolean hasPendingMigration()
	{
		requireEdt();
		return rulePanel.hasPendingMigration();
	}

	public void notificationLogged(NotificationState.Accepted entry)
	{
		requireEdt();
		logPanel.entryLogged(entry);
	}

	private final class RuleTabActions implements NotificationLogPanel.RuleActions
	{
		@Override
		public boolean canCreateRule()
		{
			requireEdt();
			return rulePanel.canCreateRule();
		}

		@Override
		public void createRule(String message)
		{
			requireEdt();
			select(rulesTab);
			rulePanel.showNewRuleFor(message);
		}

		@Override
		public List<NotificationRule> matchingRules(String message)
		{
			requireEdt();
			return controller.matchingRules(message);
		}

		@Override
		public void openRule(UUID id)
		{
			requireEdt();
			select(rulesTab);
			rulePanel.showRule(id);
		}
	}

	private void selectDefaultTab()
	{
		if (rulePanel.hasPendingMigration())
		{
			select(rulesTab);
			return;
		}
		select(notificationsTab);
	}

	private void select(MaterialTab tab)
	{
		tabGroup.select(tab);
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
		Edt.require(EDT_SUBJECT);
	}

	boolean isShowingLogForTest()
	{
		requireEdt();
		return notificationsTab.isSelected() && display.getComponentCount() == 1
			&& display.getComponent(0) == logPanel;
	}

	boolean isShowingRulesForTest()
	{
		requireEdt();
		return rulesTab.isSelected() && display.getComponentCount() == 1
			&& display.getComponent(0) == rulePanel;
	}

	void selectRulesTabForTest()
	{
		requireEdt();
		select(rulesTab);
	}

	void selectNotificationsTabForTest()
	{
		requireEdt();
		select(notificationsTab);
	}

	public boolean isMigrationGateVisibleForTest()
	{
		requireEdt();
		return rulePanel.isMigrationGateVisibleForTest();
	}

	RuleEditorPanel ruleEditorForTest()
	{
		requireEdt();
		return rulePanel;
	}

	NotificationLogPanel.RuleActions ruleActionsForTest()
	{
		return ruleActions;
	}

	NotificationLogPanel logPanelForTest()
	{
		requireEdt();
		return logPanel;
	}
}
