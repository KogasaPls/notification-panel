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

import com.notificationpanel.state.NotificationState;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
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
	private static final String EDT_ERROR = "Sidebar mutations must run on the EDT.";

	/** What the sidebar needs from the plugin, which owns the config and the client thread. */
	public interface Actions
	{
		void clearNotifications();
	}

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
		this.rulePanel = new RuleEditorPanel(controller);
		this.logPanel = new NotificationLogPanel(log, actions::clearNotifications);

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
		rulePanel.reload(migratedElsewhere);
		if (rulePanel.hasPendingMigration())
		{
			// A migration is not confined to startup -- config synced on login, a profile switch --
			// so a gate can be raised while the user is looking at the log.
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
		if (!SwingUtilities.isEventDispatchThread())
		{
			throw new IllegalStateException(EDT_ERROR);
		}
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
	 * Public, unlike the other hooks here, so {@code NotificationPanelPlugin}'s own tests can reach
	 * the rule editor's migration gate. That handoff is the seam that dropped the gate when config
	 * arrived after startup, and it now runs through this host.
	 */
	public RuleEditorPanel ruleEditorForTest()
	{
		requireEdt();
		return rulePanel;
	}

	NotificationLogPanel logPanelForTest()
	{
		requireEdt();
		return logPanel;
	}
}
