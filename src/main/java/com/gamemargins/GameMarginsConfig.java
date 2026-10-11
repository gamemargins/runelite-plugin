package com.gamemargins;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;

@ConfigGroup(GameMarginsConfig.GROUP)
public interface GameMarginsConfig extends Config
{
	String GROUP = "gamemargins";
	/** The token the device flow gave this installation; set and cleared by the plugin, never typed. */
	String TOKEN_KEY = "token";

	@ConfigSection(
		name = "Notifications",
		description = "What the plugin tells you in the game",
		position = 10
	)
	String NOTIFICATIONS = "notifications";

	@ConfigItem(
		keyName = "siteUrl",
		name = "Site",
		description = "Where the plugin sends your Grand Exchange offers once you have linked it on the site: gamemargins.com. Change it only to test against a copy of the site.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 1
	)
	default String siteUrl()
	{
		return "https://gamemargins.com";
	}

	@ConfigItem(
		keyName = "overlay",
		name = "Buy limit in the offer screen",
		description = "When you set up a buy offer, show how much of the item's buy limit you have left and when it resets",
		position = 2
	)
	default boolean overlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyLimitReset",
		name = "Buy limit reset",
		description = "Tell me when a buy limit of an item I bought frees up again",
		section = NOTIFICATIONS,
		position = 11
	)
	default Notification notifyLimitReset()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "notifyOfferDone",
		name = "Offer finished",
		description = "Tell me when a buy or sell offer has fully filled",
		section = NOTIFICATIONS,
		position = 12
	)
	default Notification notifyOfferDone()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = TOKEN_KEY,
		name = "",
		description = "",
		hidden = true,
		secret = true
	)
	default String token()
	{
		return "";
	}
}
