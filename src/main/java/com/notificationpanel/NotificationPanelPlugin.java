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
package com.notificationpanel;

import com.google.inject.Provides;
import com.notificationpanel.rules.RuleConfigStore;
import com.notificationpanel.rules.RuleDocument;
import com.notificationpanel.rules.RuleSet;
import com.notificationpanel.state.NotificationState;
import com.notificationpanel.ui.NotificationLog;
import com.notificationpanel.ui.NotificationSidebarPanel;
import com.notificationpanel.ui.RuleEditorController;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NotificationFired;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Notification Panel",
	description = "Displays notifications in a movable overlay panel",
	tags = {"notification", "notifications", "alert", "popup", "overlay", "panel", "rules",
		"filter", "color"}
)
public class NotificationPanelPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "notificationpanel";
	private static final Logger log = LoggerFactory.getLogger(NotificationPanelPlugin.class);

	@Inject
	private NotificationPanelConfig config;
	@Inject
	private NotificationPanelOverlay overlay;
	@Inject
	private NotificationState state;
	@Inject
	private NotificationPolicyFactory policyFactory;
	@Inject
	private DefaultVisibilityMigrator defaultVisibilityMigrator;
	@Inject
	private RuleConfigStore ruleConfigStore;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ClientThread clientThread;

	private final AtomicLong starts = new AtomicLong();
	private volatile long activation;
	private final AtomicBoolean migratedThisSession = new AtomicBoolean();
	private final NotificationLog notificationLog = new NotificationLog();
	private RuleEditorController ruleEditorController;
	private NotificationSidebarPanel sidebarPanel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		long session = starts.incrementAndGet();
		activation = session;
		overlayManager.add(overlay);
		overlay.applyStartingSize();
		clientThread.invokeLater(() ->
		{
			if (isActive(session))
			{
				reloadPolicy();
			}
		});
		SwingUtilities.invokeLater(() -> syncSidebar(session));
	}

	@Override
	protected void shutDown()
	{
		activation = 0;
		SwingUtilities.invokeLater(() ->
		{
			removeSidebar();
			notificationLog.clear();
		});
		overlayManager.remove(overlay);
		clientThread.invokeLater(state::clear);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		String message = event.getMessage();
		long session = activation;
		clientThread.invokeLater(() ->
		{
			if (!isActive(session))
			{
				return;
			}
			NotificationState.Accepted accepted = state.accept(message);
			if (accepted != null)
			{
				SwingUtilities.invokeLater(() -> record(session, accepted));
			}
		});
	}

	private boolean isActive(long session)
	{
		return session != 0 && activation == session;
	}

	private void record(long session, NotificationState.Accepted accepted)
	{
		if (!isActive(session))
		{
			return;
		}
		notificationLog.add(accepted);
		if (sidebarPanel != null)
		{
			sidebarPanel.notificationLogged(accepted);
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		state.onGameTick();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()))
		{
			return;
		}
		long session = activation;
		clientThread.invokeLater(() ->
		{
			if (isActive(session))
			{
				reloadPolicy();
			}
		});
		SwingUtilities.invokeLater(() ->
		{
			boolean built = syncSidebar(session);
			if (!built && isActive(session) && sidebarPanel != null)
			{
				sidebarPanel.reload();
			}
		});
	}

	@Subscribe
	public void onOverlayMenuClicked(OverlayMenuClicked event)
	{
		OverlayMenuEntry entry = event.getEntry();
		if (entry.getMenuAction() == MenuAction.RUNELITE_OVERLAY
			&& event.getOverlay() == overlay
			&& NotificationPanelOverlay.CLEAR_ALL.equals(entry.getOption()))
		{
			state.clear();
		}
	}

	private void reloadPolicy()
	{
		defaultVisibilityMigrator.adoptLegacyValue();
		RuleConfigStore.LoadResult result = ruleConfigStore.load();
		if (result.wasMigrated())
		{
			announceMigration();
		}
		if (result.hasBlockingError())
		{
			log.warn("Notification rule data is corrupt; using no rules until it is reset.");
		}
		RuleDocument document = result.getDocument();
		RuleSet.CompileResult compiled = RuleSet.compile(document.getRules());
		int excluded = compiled.getErrors().size();
		if (excluded > 0)
		{
			log.warn("Excluded {} invalid enabled notification rule(s) during compilation.",
				excluded);
		}
		state.updatePolicy(policyFactory.create(config, compiled.getRuleSet()));
		state.setTestNotificationVisible(config.showTestNotification());
	}

	private void announceMigration()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (activation != 0 && sidebarPanel != null)
			{
				sidebarPanel.reload(true);
				return;
			}
			migratedThisSession.set(true);
		});
	}

	NotificationSidebarPanel.Actions sidebarActionsForTest()
	{
		return new SidebarActions();
	}

	NotificationSidebarPanel sidebarPanelForTest()
	{
		return sidebarPanel;
	}

	void syncSidebarForEarlierStartForTest()
	{
		syncSidebar(starts.get() - 1);
	}

	NotificationLog notificationLogForTest()
	{
		return notificationLog;
	}

	private final class SidebarActions implements NotificationSidebarPanel.Actions
	{
		@Override
		public void clearNotifications()
		{
			long session = activation;
			clientThread.invokeLater(() ->
			{
				if (isActive(session))
				{
					state.clear();
				}
			});
		}
	}

	private boolean syncSidebar(long session)
	{
		if (!isActive(session))
		{
			return false;
		}
		if (config.showSidebarButton())
		{
			if (sidebarPanel == null)
			{
				createSidebar();
				return true;
			}
		}
		else if (sidebarPanel != null)
		{
			removeSidebar();
		}
		return false;
	}

	private void createSidebar()
	{
		ruleEditorController = new RuleEditorController(ruleConfigStore);
		if (migratedThisSession.get())
		{
			ruleEditorController.markMigrated();
		}
		sidebarPanel = new NotificationSidebarPanel(ruleEditorController, notificationLog,
			new SidebarActions());
		migratedThisSession.set(false);
		navigationButton = NavigationButton.builder()
			.tooltip("Notification Panel")
			.icon(sidebarPanel.getNavigationIcon())
			.priority(5)
			.panel(sidebarPanel)
			.build();
		clientToolbar.addNavigation(navigationButton);
	}

	private void removeSidebar()
	{
		if (navigationButton != null)
		{
			clientToolbar.removeNavigation(navigationButton);
			navigationButton = null;
		}
		if (sidebarPanel != null && sidebarPanel.hasPendingMigration())
		{
			migratedThisSession.set(true);
		}
		sidebarPanel = null;
		ruleEditorController = null;
	}

	@Provides
	NotificationPanelConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(NotificationPanelConfig.class);
	}

	@Provides
	@Singleton
	NotificationState provideNotificationState()
	{
		return new NotificationState(Clock.systemUTC());
	}
}
