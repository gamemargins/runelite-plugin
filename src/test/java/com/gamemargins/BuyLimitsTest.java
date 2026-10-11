package com.gamemargins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class BuyLimitsTest
{
	private static final long H = 3_600_000;
	private static final long T = 1_791_700_000_000L;

	@Test
	public void theWindowStartsAtTheFirstPurchaseAndEndsFourHoursLaterWhateverCameAfter()
	{
		List<Purchase> buys = Arrays.asList(new Purchase(4151, 100, T), new Purchase(4151, 50, T + H + H / 2));
		BuyLimits.Window w = BuyLimits.window(4151, buys, T + 2 * H, 500);
		assertEquals(T, w.getStart());
		assertEquals(T + 4 * H, w.getResets());
		assertEquals(150, w.getUsed());
		assertEquals(350, w.getLeft());
		assertNull(BuyLimits.window(4151, buys, T + 4 * H, 500));
	}

	@Test
	public void aPurchaseAfterTheWindowStartsANewOne()
	{
		List<Purchase> buys = Arrays.asList(new Purchase(4151, 10, T - 4 * H), new Purchase(4151, 20, T - H), new Purchase(4151, 30, T + H));
		BuyLimits.Window w = BuyLimits.window(4151, buys, T + 2 * H, 100);
		assertEquals(T + H, w.getStart());
		assertEquals(30, w.getUsed());
		assertEquals(70, w.getLeft());
		// A purchase timed in the future counts as now; an unknown limit shows -1 left.
		List<Purchase> ahead = new ArrayList<>(buys);
		ahead.add(new Purchase(4151, 5, T + 10 * H));
		assertEquals(35, BuyLimits.window(4151, ahead, T + 2 * H, 100).getUsed());
		assertEquals(-1, BuyLimits.window(4151, buys, T + 2 * H, -1).getLeft());
		assertEquals(0, BuyLimits.window(4151, Arrays.asList(new Purchase(4151, 600, T)), T + 1, 500).getLeft());
	}

	@Test
	public void manyItemsAtOnceSoonestResetFirst()
	{
		List<Purchase> buys = Arrays.asList(new Purchase(4151, 3, T + H), new Purchase(560, 1000, T), new Purchase(4151, 2, T + 2 * H), new Purchase(995, 1, T - 5 * H));
		// Boxed on purpose: a conditional mixing int literals with null unboxes the null and throws.
		List<BuyLimits.Window> windows = BuyLimits.windows(buys, T + 2 * H, id -> id == 4151 ? Integer.valueOf(70) : id == 560 ? Integer.valueOf(25_000) : null);
		assertEquals(2, windows.size());
		assertEquals(560, windows.get(0).getItemId());
		assertEquals(4151, windows.get(1).getItemId());
		assertEquals(5, windows.get(1).getUsed());
		assertEquals(65, windows.get(1).getLeft());
	}

	@Test
	public void timeLeftInWords()
	{
		assertEquals("under a minute", BuyLimits.until(30_000));
		assertEquals("45 min", BuyLimits.until(45 * 60_000));
		assertEquals("2h 05m", BuyLimits.until(2 * H + 5 * 60_000));
	}
}
