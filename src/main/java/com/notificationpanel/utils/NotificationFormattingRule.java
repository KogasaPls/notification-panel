package com.notificationpanel.utils;

import com.notificationpanel.Notification;
import com.notificationpanel.viewmodels.NotificationViewModel;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class NotificationFormattingRule
{
	public Predicate<Notification> predicate;
	public Consumer<NotificationViewModel> formatter;

	public NotificationFormattingRule()
	{
	}

	public NotificationFormattingRule(Predicate<Notification> predicate, Consumer<NotificationViewModel> formatter)
	{
		this.predicate = predicate;
		this.formatter = formatter;
	}

	public boolean format(NotificationViewModel notificationViewModel)
	{
		if (predicate.test(notificationViewModel.getNotification()))
		{
			formatter.accept(notificationViewModel);
			return true;
		}
		return false;
	}
}

