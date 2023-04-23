package com.notificationpanel.viewmodels;

import com.notificationpanel.IConfigurable;
import com.notificationpanel.ITickable;
import java.awt.Graphics2D;

public interface ViewModel extends IConfigurable, ITickable
{
	public void onBeforeRender(Graphics2D graphics);
	public boolean shouldRender();
	public void setPreferredLocation(java.awt.Point position);
	public void setPreferredSize(java.awt.Dimension dimension);
}
