package com.gamemargins;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.EnumSet;
import net.runelite.api.WorldType;
import org.junit.Test;

public class WorldFilterTest
{
	@Test
	public void mainGameWorldsAreTracked()
	{
		assertTrue(WorldFilter.tracked(EnumSet.noneOf(WorldType.class)));
		assertTrue(WorldFilter.tracked(EnumSet.of(WorldType.MEMBERS)));
		assertTrue(WorldFilter.tracked(EnumSet.of(WorldType.MEMBERS, WorldType.PVP, WorldType.HIGH_RISK, WorldType.SKILL_TOTAL)));
		assertTrue(WorldFilter.tracked(null));
	}

	@Test
	public void worldsWithTheirOwnEconomyAreNot()
	{
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.MEMBERS, WorldType.SEASONAL)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.DEADMAN)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.TOURNAMENT_WORLD)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.BETA_WORLD)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.FRESH_START_WORLD)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.PVP_ARENA)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.QUEST_SPEEDRUNNING)));
		assertFalse(WorldFilter.tracked(EnumSet.of(WorldType.NOSAVE_MODE)));
	}
}
