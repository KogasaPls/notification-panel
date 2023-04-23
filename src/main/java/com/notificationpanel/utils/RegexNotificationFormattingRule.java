package com.notificationpanel.utils;

import com.notificationpanel.viewmodels.NotificationViewModel;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class RegexNotificationFormattingRule extends NotificationFormattingRule
{
	public final Pattern pattern;

	public RegexNotificationFormattingRule(String regex, Consumer<NotificationViewModel> formatter)
	{
		this.pattern = Pattern.compile(regex);
		this.predicate = notification -> pattern.asPredicate().test(notification.message);
	}

}
