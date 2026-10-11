package com.gamemargins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * The Grand Exchange's buy limits run in 4-hour windows: the window starts with the first item bought and
 * ends 4 hours later, whatever came after; then the whole limit is free again. The same rule as the site's
 * core buyLimitWindow, so the panel, the overlay and gamemargins.com agree.
 */
public final class BuyLimits
{
	public static final long WINDOW_MS = 4L * 60 * 60 * 1000;
	/** Purchases older than this can't be in a window that is still open. */
	public static final long LOOKBACK_MS = 24L * 60 * 60 * 1000;

	private BuyLimits()
	{
	}

	/** One item's open window. */
	@Data
	@AllArgsConstructor
	public static class Window
	{
		private final int itemId;
		/** When the window started: the first purchase in it (ms). */
		private final long start;
		/** When the limit frees up again (ms). */
		private final long resets;
		/** Items bought in the window. */
		private final int used;
		/** The item's limit, or -1 when unknown. */
		private final int limit;

		/** How many may still be bought; -1 when the limit is unknown. */
		public int getLeft()
		{
			return limit < 0 ? -1 : Math.max(0, limit - used);
		}
	}

	/**
	 * The window open at {@code now} for one item, or null when the last window has ended. Purchases are
	 * walked oldest first; one that falls after the running window's end starts a new one. A purchase timed
	 * after now (a clock that was ahead) counts as now.
	 */
	public static Window window(int itemId, List<Purchase> purchases, long now, int limit)
	{
		List<Purchase> sorted = new ArrayList<>();
		for (Purchase p : purchases)
		{
			if (p.getItemId() == itemId && p.getQuantity() > 0)
			{
				sorted.add(new Purchase(itemId, p.getQuantity(), Math.min(p.getAt(), now)));
			}
		}
		sorted.sort(Comparator.comparingLong(Purchase::getAt));
		long start = -1;
		int used = 0;
		for (Purchase p : sorted)
		{
			if (start < 0 || p.getAt() >= start + WINDOW_MS)
			{
				start = p.getAt();
				used = 0;
			}
			used += p.getQuantity();
		}
		if (start < 0 || now >= start + WINDOW_MS)
		{
			return null;
		}
		return new Window(itemId, start, start + WINDOW_MS, used, limit);
	}

	/** The open windows of every item with purchases, soonest reset first. {@code limitOf} gives an item's limit, or null when unknown. */
	public static List<Window> windows(List<Purchase> purchases, long now, IntFunction<Integer> limitOf)
	{
		Set<Integer> items = new LinkedHashSet<>();
		for (Purchase p : purchases)
		{
			items.add(p.getItemId());
		}
		List<Window> out = new ArrayList<>();
		for (int itemId : items)
		{
			Integer limit = limitOf.apply(itemId);
			Window w = window(itemId, purchases, now, limit == null ? -1 : limit);
			if (w != null)
			{
				out.add(w);
			}
		}
		out.sort(Comparator.comparingLong(Window::getResets));
		return out;
	}

	/** "2h 05m", "45 min", "under a minute". */
	public static String until(long ms)
	{
		if (ms < 60_000)
		{
			return "under a minute";
		}
		long minutes = (ms + 59_999) / 60_000;
		if (minutes < 60)
		{
			return minutes + " min";
		}
		return String.format("%dh %02dm", minutes / 60, minutes % 60);
	}
}
