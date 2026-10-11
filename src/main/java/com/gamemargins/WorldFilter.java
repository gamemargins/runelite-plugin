package com.gamemargins;

import java.util.Collection;
import java.util.EnumSet;
import net.runelite.api.WorldType;

/**
 * Worlds whose Grand Exchange is its own economy: offers there say nothing about the main game's buy limits
 * or prices, so the plugin ignores them (Leagues and other seasonals, Deadman, tournament and beta worlds,
 * fresh start worlds, the PvP Arena, speedrunning and no-save worlds).
 */
public final class WorldFilter
{
	private static final EnumSet<WorldType> OWN_ECONOMY = EnumSet.of(
		WorldType.SEASONAL,
		WorldType.DEADMAN,
		WorldType.TOURNAMENT_WORLD,
		WorldType.BETA_WORLD,
		WorldType.FRESH_START_WORLD,
		WorldType.PVP_ARENA,
		WorldType.QUEST_SPEEDRUNNING,
		WorldType.NOSAVE_MODE
	);

	private WorldFilter()
	{
	}

	/** Whether offers on a world of these types belong to the main game's economy. */
	public static boolean tracked(Collection<WorldType> types)
	{
		if (types == null)
		{
			return true;
		}
		for (WorldType t : types)
		{
			if (OWN_ECONOMY.contains(t))
			{
				return false;
			}
		}
		return true;
	}
}
