package com.notificationpanel.views;

import com.notificationpanel.IConfigurable;
import com.notificationpanel.ITickable;
import com.notificationpanel.NotificationPanelConfig;
import com.notificationpanel.viewmodels.ViewModel;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.api.events.GameTick;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;


public abstract class BaseView implements IConfigurable, ITickable, LayoutableRenderableEntity
{
	public Dimension lastDimension;
	public ViewModel viewModel;

	public BaseView(ViewModel viewModel) {
		this.viewModel = viewModel;
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!viewModel.shouldRender() && lastDimension != null)
		{
			return lastDimension;
		}

		viewModel.onBeforeRender(graphics);
		lastDimension = renderImpl(graphics);
		return lastDimension;
	}

	@Override
	public void setConfig(NotificationPanelConfig config)
	{
		viewModel.setConfig(config);
	}


	@Override
	public void onTick(GameTick tick)
	{
		viewModel.onTick(tick);
	}


	abstract Dimension renderImpl(Graphics2D graphics);
}
