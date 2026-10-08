package com.gamemargins;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

/**
 * GameMargins: Grand Exchange buy-limit timers and flip tracking, synced with gamemargins.com.
 * The skeleton only; the brief for the build is in the gamemargins repository's planning notes.
 */
@Slf4j
@PluginDescriptor(
	name = "GameMargins",
	description = "Grand Exchange buy-limit timers and flip tracking, synced with gamemargins.com",
	tags = {"flipping", "grand exchange", "ge", "buy limit", "timer", "merching", "profit"}
)
public class GameMarginsPlugin extends Plugin
{
	@Override
	protected void startUp()
	{
		log.debug("GameMargins started");
	}

	@Override
	protected void shutDown()
	{
		log.debug("GameMargins stopped");
	}

	@Provides
	GameMarginsConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GameMarginsConfig.class);
	}
}
