package com.notificationpanel;

import java.time.Instant;
import java.util.ArrayList;
import lombok.Getter;
import lombok.Setter;

class Notification
{
	final String message;
	final Instant time;
	final long duration;
	final String[] words;
	@Getter
	@Setter
	ArrayList<String> wrapped = new ArrayList<>();
	@Getter
	@Setter
	int maxWordWidth = 0;
	@Getter
	@Setter
	int lineHeight = 18;

	Notification(final String message, long duration)
	{
		this.message = message;
		this.time = Instant.now();
		this.duration = duration;

		// split on spaces and slashes (to break up screenshot notifications)
		// message = "hello world/there"
		// words = ["hello", " ", "world", "/", "there"]
		final String[] splitMessage = message.split("(?<=[ \\\\/])|(?=[ \\\\/])+", -1);

		// ellipsize any word which is longer than 32 characters to prevent
		// the notification from growing too much
		this.words = ellipsize(splitMessage);
	}

	String[] ellipsize(String[] arr)
	{
		for (int i = 0; i < arr.length; i++)
		{
			if (arr[i].length() > 32)
			{
				arr[i] = arr[i].substring(0, 29) + "...";
			}
		}
		return arr;
	}

}
