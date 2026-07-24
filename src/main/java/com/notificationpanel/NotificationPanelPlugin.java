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

@PluginDescriptor(name = "Notification Panel")
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

	private volatile boolean running;
	private volatile boolean migratedThisSession;
	private RuleEditorController ruleEditorController;
	private RuleEditorPanel ruleEditorPanel;
	private NavigationButton navigationButton;

	@Override
	protected void startUp()
	{
		reloadPolicy();
		overlayManager.add(overlay);
		running = true;
		SwingUtilities.invokeLater(this::createSidebar);
	}

	@Override
	protected void shutDown()
	{
		running = false;
		SwingUtilities.invokeLater(this::removeSidebar);
		overlayManager.remove(overlay);
		state.clear();
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
			if (running && ruleEditorPanel != null)
			{
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
			// The policy load performs the migration and writes rulesV1, so the editor's own
			// load will no longer see a migration. Remember it so the editor can still show its
			// one-time summary banner.
			migratedThisSession = true;
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
	}

	private void createSidebar()
	{
		if (!running)
		{
			return;
		}
		ruleEditorController = new RuleEditorController(ruleConfigStore);
		if (migratedThisSession)
		{
			ruleEditorController.markMigrated();
		}
		ruleEditorPanel = new RuleEditorPanel(ruleEditorController);
		navigationButton = NavigationButton.builder()
			.tooltip("Notification Panel Rules")
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
