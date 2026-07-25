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
 * The sidebar itself: the record of what has arrived, and the rules that decide what does.
 *
 * <p>Built without {@code PluginPanel}'s own scroll pane, because each tab scrolls what it needs to
 * -- the log its rows, the rule editor its list and its form -- and an outer scroll pane over those
 * would size them to their preferred height instead of the sidebar's.</p>
 */
public final class NotificationSidebarPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;
	private static final String EDT_SUBJECT = "Sidebar mutations";

	/** What the sidebar needs from the plugin, which owns the config and the client thread. */
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
		// The answers come from this host -- selecting the Rules tab needs the tab group, and
		// asking whether a rule can be created needs the rule editor -- but they are the log tab's
		// questions and no business of anything holding the sidebar, so they answer from in here
		// rather than becoming methods on it.
		this.logPanel = new NotificationLogPanel(log, actions::clearNotifications, ruleActions);

		setLayout(new BorderLayout(0, 6));
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		// Unwrapped, PluginPanel applies none of its own chrome, so the padding every other sidebar
		// gets for free has to be set here or the content sits against the client's edge.
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

		// The gate explains why a batch of imported rules arrived switched off, and nothing else
		// ever says it, so it takes precedence over the tab this would otherwise open on.
		selectDefaultTab();
	}

	/**
	 * The width RuneLite expects, and deliberately no height.
	 *
	 * <p>An unwrapped {@code PluginPanel} is the component RuneLite puts in the sidebar itself, so
	 * the height it reports is a height the window has to find room for. Both tabs would report the
	 * size of everything they contain -- two hundred log rows came to some seven thousand pixels --
	 * and the client's window grew to fit it. A wrapped panel never does this because
	 * {@code PluginPanel} pins its own wrapper to a preferred height of zero and scrolls the
	 * content inside; this is the same contract, kept by hand because the tab strip has to stay put
	 * while the tab under it scrolls.</p>
	 *
	 * <p>Zero is not a minimum this panel wants to be drawn at. It says the panel has no height
	 * requirement of its own, so the sidebar hands it whatever height the window has and each tab
	 * scrolls its own contents within that.</p>
	 */
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
			// Only as the gate goes up, never while it stands. A migration is not confined to
			// startup -- config synced on login, a profile switch -- so one can be raised while the
			// user is looking at the log, and that is worth taking them to. But every config change
			// in this group reloads, so acting on "the gate is up" rather than "the gate just went
			// up" drags a user who has moved to Notifications back to Rules each time they nudge a
			// setting, until they acknowledge it.
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

	/** What the log tab asks of the rule tab, answered by the one thing that holds both. */
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
			// showNewRuleFor applies the same guards showNewRule already does, so switching tabs
			// first is safe: if a guard now fails the user still lands on the Rules tab and sees
			// why (the blocking banner or a full list) instead of nothing happening on the tab
			// they were on.
			select(rulesTab);
			rulePanel.showNewRuleFor(message);
		}

		/**
		 * Answered from the controller rather than through the rule panel, unlike the rest of
		 * these: which rules match a message is a question about the stored rules, and passing it
		 * through the panel only added a hop that had nothing to say.
		 */
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
			// Same order as createRule, and for the same reason: the tab switch is what the user
			// asked for even when the rule has been deleted since the menu was built and nothing
			// opens.
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

	/**
	 * Shows a tab.
	 *
	 * <p>Through the group rather than {@code MaterialTab.select()}, which only repaints the label
	 * it is called on: swapping the displayed content and unselecting the other tab are the group's
	 * job, so calling the tab directly would leave both looking selected over an empty display.</p>
	 */
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

	// Test hooks, package-private, grouped at the end rather than ahead of the behaviour they
	// reach into -- matching how RuleEditorPanel and NotificationLogPanel end.
	/**
	 * Reads what the display holds rather than the tab's own flag, because a tab can be lit without
	 * its content having been swapped in -- which is exactly the mistake {@link #select} avoids.
	 */
	boolean isShowingLogForTest()
	{
		requireEdt();
		return notificationsTab.isSelected() && display.getComponentCount() == 1
			&& display.getComponent(0) == logPanel;
	}

	/** The mirror of {@link #isShowingLogForTest()}, read the same way and for the same reason. */
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

	/**
	 * Whether the rule editor is showing its migration gate.
	 *
	 * <p>Public, unlike the other hooks here, because {@code NotificationPanelPlugin}'s own tests
	 * ask it from another package: the handoff between the two is the seam that dropped the gate
	 * when config arrived after startup. A boolean is what crosses that line -- the rule editor is
	 * an internal of this panel, and a sidebar handing one out to whoever holds it is an API this
	 * plugin does not have.</p>
	 */
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

	/** The log tab's own view of the rule tab, so a test drives what the log panel was handed. */
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
