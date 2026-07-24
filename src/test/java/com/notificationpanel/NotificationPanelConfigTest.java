package com.notificationpanel;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NotificationPanelConfigTest
{
	@Test
	public void preservesOrdinaryDefaults()
	{
		NotificationPanelConfig config = new NotificationPanelConfig() {};
		assertEquals(3, config.expireTime());
		assertEquals(NotificationPanelConfig.TimeUnit.SECONDS, config.timeUnit());
		assertEquals(1, config.numToShow());
		assertTrue(config.showTime());
		assertEquals(75, config.opacity());
		assertTrue(config.visibility());
		assertEquals("", config.rulesV1());
	}
}
