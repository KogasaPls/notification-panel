package com.notificationpanel.utils;

import java.awt.Font;
import java.awt.FontMetrics;
import java.util.HashMap;

public class FontMetricsCache
{
	private static final HashMap<Font, FontMetrics> fontMetrics = new HashMap<>();

	public static FontMetrics GetFontMetrics(Font font) throws RuntimeException
	{
		FontMetrics metrics = fontMetrics.get(font);
		if (metrics == null)
		{
			throw new RuntimeException("FontMetrics not computed for font " + font);
		}

		return metrics;
	}

	public static void AddFontMetrics(Font font, FontMetrics metrics)
	{
		if (fontMetrics.containsKey(font))
		{
			return;
		}

		fontMetrics.put(font, metrics);
	}

}
