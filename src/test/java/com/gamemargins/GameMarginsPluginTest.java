package com.gamemargins;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Starts a development RuneLite with this plugin loaded: `./gradlew run`. */
public class GameMarginsPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(GameMarginsPlugin.class);
		RuneLite.main(args);
	}
}
