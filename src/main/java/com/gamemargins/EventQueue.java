package com.gamemargins;

import java.util.ArrayList;
import java.util.List;

/**
 * The events waiting to go to gamemargins.com, in order, persisted by the Store so nothing is lost when
 * RuneLite closes or the site is down. Sent in batches with a backoff that doubles on failure (5 s up to
 * 5 minutes). Bounded: past {@link #MAX} the oldest events are dropped, which the site tolerates (an offer
 * whose earlier events are missing is taken from its latest state).
 */
public class EventQueue
{
	public static final int MAX = 5_000;
	/** Events per batch; the site accepts up to 500. */
	public static final int BATCH = 200;
	public static final long FIRST_BACKOFF_MS = 5_000;
	public static final long MAX_BACKOFF_MS = 5 * 60_000;

	private final List<OfferEvent> events = new ArrayList<>();
	private long backoffMs = 0;
	private long notBefore = 0;
	private boolean inFlight;

	public synchronized void addAll(List<OfferEvent> fresh)
	{
		events.addAll(fresh);
		while (events.size() > MAX)
		{
			events.remove(0);
		}
	}

	/** Puts the events of the last session in front of whatever arrived since the plugin started. */
	public synchronized void restore(List<OfferEvent> saved)
	{
		events.addAll(0, saved);
		while (events.size() > MAX)
		{
			events.remove(0);
		}
	}

	public synchronized void clear()
	{
		events.clear();
		inFlight = false;
		backoffMs = 0;
		notBefore = 0;
	}

	public synchronized List<OfferEvent> snapshot()
	{
		return new ArrayList<>(events);
	}

	public synchronized int size()
	{
		return events.size();
	}

	public synchronized boolean isEmpty()
	{
		return events.isEmpty();
	}

	/** The next batch to send, or null when nothing is due (empty, a send in flight, or backing off). */
	public synchronized List<OfferEvent> take(long now)
	{
		if (inFlight || events.isEmpty() || now < notBefore)
		{
			return null;
		}
		inFlight = true;
		return new ArrayList<>(events.subList(0, Math.min(BATCH, events.size())));
	}

	/** The batch was accepted: drop it and send again at once. */
	public synchronized void sent(List<OfferEvent> batch)
	{
		events.removeAll(batch);
		inFlight = false;
		backoffMs = 0;
		notBefore = 0;
	}

	/** The batch was refused as malformed: it would never be accepted, so it goes. */
	public synchronized void dropped(List<OfferEvent> batch)
	{
		sent(batch);
	}

	/** The batch could not be delivered: keep it, wait longer each time. */
	public synchronized void failed(long now)
	{
		inFlight = false;
		backoffMs = backoffMs == 0 ? FIRST_BACKOFF_MS : Math.min(MAX_BACKOFF_MS, backoffMs * 2);
		notBefore = now + backoffMs;
	}

	public synchronized long getBackoffMs()
	{
		return backoffMs;
	}
}
