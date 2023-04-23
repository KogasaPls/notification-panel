package com.notificationpanel;

import static com.notificationpanel.Constants.CLEAR_ALL;
import com.notificationpanel.utils.FontMetricsCache;
import com.notificationpanel.viewmodels.NotificationPanelViewModel;
import com.notificationpanel.views.NotificationPanelView;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import javax.inject.Inject;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

public class NotificationPanelOverlay extends OverlayPanel implements IConfigurable
{
	private final NotificationPanelView notificationPanelView;
	public boolean shouldUpdateFontMetricsCache = true;
	private NotificationPanelConfig config;

	@Inject
	private NotificationPanelOverlay(NotificationPanelConfig config)
	{
		NotificationPanelViewModel viewModel = new NotificationPanelViewModel(config);
		notificationPanelView = new NotificationPanelView(viewModel, panelComponent);

		setConfig(config);

		setResizable(true);
		setPosition(OverlayPosition.TOP_LEFT);
		setPriority(OverlayPriority.HIGH);
		setClearChildren(false);

		getMenuEntries().add(new OverlayMenuEntry(RUNELITE_OVERLAY, CLEAR_ALL,
			"Notification " + "panel"));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (shouldUpdateFontMetricsCache)
		{
			updateFontMetricsCache(graphics);
		}

		notificationPanelView.render(graphics);
		return super.render(graphics);
	}

	private void updateFontMetricsCache(Graphics2D graphics)
	{
		Font font = config.fontType().getFont();
		graphics.setFont(font);
		FontMetricsCache.AddFontMetrics(font, graphics.getFontMetrics());
		shouldUpdateFontMetricsCache = false;
	}

	@Override
	public void setConfig(NotificationPanelConfig config)
	{
		this.config = config;
		notificationPanelView.setConfig(config);
	}
}
