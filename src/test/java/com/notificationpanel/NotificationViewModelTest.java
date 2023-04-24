package com.notificationpanel;

import com.notificationpanel.viewmodels.NotificationViewModel;

public class NotificationViewModelTest
{

	public static void main(String[] args) throws Exception
	{
		testNotificationViewModel();
	}

	public static void testNotificationViewModel()
	{
		Notification notification = new Notification("The quick brown fox jumps over the lazy dog. Isn't that just the cutest thing you've ever seen?",5, NotificationPanelConfig.TimeUnit.SECONDS);

		NotificationViewModel notificationViewModel = new NotificationViewModel(notification, new DefaultNotificationPanelConfig());
		System.out.println("Font: " + notificationViewModel.font);
		System.out.println("Bounds: " + notificationViewModel.bounds);
		System.out.println("BackgroundColor: " + notificationViewModel.backgroundColor);
		System.out.println("ForegroundColor: " + notificationViewModel.foregroundColor);
		System.out.println("BorderColor: " + notificationViewModel.borderColor);
		System.out.println("Body: " + notificationViewModel.body);
		System.out.println("IsDisplayed: " + notificationViewModel.isDisplayed());
	}

	private static class DefaultNotificationPanelConfig implements NotificationPanelConfig
	{

	}
}
