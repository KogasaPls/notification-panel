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
import com.notificationpanel.ui.RuleEditorController;
import com.notificationpanel.ui.RuleEditorPanel;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicBoolean;
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
	private RuleConfigStore ruleConfigStore;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private ClientToolbar clientToolbar;
	@Inject
	private ClientThread clientThread;
	@Inject
	private ConfigManager configManager;

	private volatile boolean running;
	/** Set on the EDT when a migration happened before the sidebar existed to be told. */
	private final AtomicBoolean migratedThisSession = new AtomicBoolean();
	private RuleEditorController ruleEditorController;
	private RuleEditorPanel ruleEditorPanel;
	private NavigationButton navigationButton;

	// RuneLite starts and stops plugins on the EDT, so neither of these may touch the state
	// directly: it is client-thread-confined and the overlay iterates it while rendering.

	@Override
	protected void startUp()
	{
		running = true;
		overlayManager.add(overlay);
		overlay.applyStartingSize();
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				reloadPolicy();
			}
		});
		SwingUtilities.invokeLater(this::syncSidebar);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		SwingUtilities.invokeLater(this::removeSidebar);
		overlayManager.remove(overlay);
		clientThread.invokeLater(state::clear);
	}

	@Subscribe
	public void onNotificationFired(NotificationFired event)
	{
		String message = event.getMessage();
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				state.accept(message);
			}
		});
	}

	// These two touch the state directly, unlike everything above, because RuneLite posts both
	// events from the game loop and so both already arrive on the client thread. Hopping would
	// only defer them by a tick. The confinement here rests on RuneLite's posting thread rather
	// than on this plugin's own discipline, which is the reason to say so rather than leave the
	// next reader to work out whether it is a bug.

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
		clientThread.invokeLater(() ->
		{
			if (running)
			{
				reloadPolicy();
			}
		});
		SwingUtilities.invokeLater(() ->
		{
			syncSidebar();
			if (running && ruleEditorPanel != null)
			{
				// Any migration is reported separately by announceMigration, so this only has
				// to refresh what the sidebar shows.
				ruleEditorPanel.reload();
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

	/**
	 * Tells the sidebar that this load performed a legacy migration.
	 *
	 * <p>Whichever of this load and the editor's own runs first performs the migration; the other
	 * then sees none, so the winner has to say so. Reporting it through a queued task rather than
	 * a flag read elsewhere keeps it independent of thread interleaving: writing rulesV1 posts
	 * ConfigChanged synchronously, which queues its own sidebar reload, and this task is queued
	 * after it. A migration can also happen long after startup -- config synced on login, a
	 * profile switch, an imported profile -- so this is not confined to the first load.</p>
	 */
	private void announceMigration()
	{
		SwingUtilities.invokeLater(() ->
		{
			if (running && ruleEditorPanel != null)
			{
				ruleEditorPanel.reload(true);
				return;
			}
			// Either the sidebar is not built yet or the plugin stopped before this task ran.
			// Recording it in both cases is what keeps the gate from being lost: rulesV1 is already
			// written, so no later load reports the migration again, and the user would be left
			// with a batch of switched-off rules and no explanation. createSidebar consumes it.
			migratedThisSession.set(true);
		});
	}

	RuleEditorPanel.Actions sidebarActionsForTest()
	{
		return new SidebarActions();
	}

	RuleEditorPanel ruleEditorPanelForTest()
	{
		return ruleEditorPanel;
	}

	private final class SidebarActions implements RuleEditorPanel.Actions
	{
		/** The state is client-thread-confined, so this one hops. */
		@Override
		public void clearNotifications()
		{
			clientThread.invokeLater(() ->
			{
				if (running)
				{
					state.clear();
				}
			});
		}

		// These write config instead of touching state directly. The resulting ConfigChanged
		// reloads the policy on the client thread, which is the same path RuneLite's own config
		// panel used to take, so there is one way for a setting to reach the core.
		@Override
		public void saveDefaults(RuleEditorPanel.Defaults defaults)
		{
			// setConfiguration compares before storing and only posts ConfigChanged when a value
			// actually differs, so writing the whole set on every edit costs one event at most.
			configManager.setConfiguration(CONFIG_GROUP, "bgColor", defaults.getBackground());
			configManager.setConfiguration(CONFIG_GROUP, "opacity", defaults.getOpacityPercent());
		}

		@Override
		public void setTestNotificationVisible(boolean visible)
		{
			configManager.setConfiguration(CONFIG_GROUP, "showTestNotification", visible);
		}
	}

	/**
	 * Brings the toolbar button into line with its setting.
	 *
	 * <p>Only ever adds or removes, never rebuilds, so a config change from any other key -- and
	 * the sidebar writes several of them -- leaves an in-progress rule draft alone.</p>
	 *
	 * <p>A migration announced while the button is hidden is not lost: announceMigration already
	 * parks its flag whenever there is no panel to tell, and createSidebar consumes the flag, so
	 * the gate appears the first time the user turns the button back on.</p>
	 */
	private void syncSidebar()
	{
		if (running && config.showSidebarButton())
		{
			if (ruleEditorPanel == null)
			{
				createSidebar();
			}
		}
		else if (ruleEditorPanel != null)
		{
			removeSidebar();
		}
	}

	private void createSidebar()
	{
		if (!running)
		{
			return;
		}
		ruleEditorController = new RuleEditorController(ruleConfigStore);
		// Consuming the flag stops a later disable/re-enable, which reuses this plugin instance
		// but performs no new migration, from showing the gate a second time.
		if (migratedThisSession.getAndSet(false))
		{
			ruleEditorController.markMigrated();
		}
		ruleEditorPanel = new RuleEditorPanel(ruleEditorController, config, new SidebarActions());
		navigationButton = NavigationButton.builder()
			.tooltip("Notification Panel")
			.icon(ruleEditorPanel.getNavigationIcon())
			.priority(5)
			.panel(ruleEditorPanel)
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
		ruleEditorPanel = null;
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
