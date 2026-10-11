package com.gamemargins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class EventQueueTest
{
	private static OfferEvent event(int seq)
	{
		return new OfferEvent("1", null, 0, "offer", seq, 4151, true, "BUYING", 1, 0, 1, 0, 1_791_700_000_000L + seq, false);
	}

	private static List<OfferEvent> events(int n, int from)
	{
		List<OfferEvent> out = new ArrayList<>();
		for (int i = 0; i < n; i++)
		{
			out.add(event(from + i));
		}
		return out;
	}

	@Test
	public void batchesGoOutInOrderOneAtATimeAndAreDroppedOnceSent()
	{
		EventQueue q = new EventQueue();
		q.addAll(events(450, 0));
		List<OfferEvent> first = q.take(0);
		assertNotNull(first);
		assertEquals(EventQueue.BATCH, first.size());
		assertEquals(0, first.get(0).getSeq());
		assertNull("one in flight at a time", q.take(0));
		q.sent(first);
		assertEquals(250, q.size());
		List<OfferEvent> second = q.take(0);
		assertEquals(200, second.get(0).getSeq());
		q.sent(second);
		assertEquals(50, q.take(0).size());
	}

	@Test
	public void aFailureKeepsTheBatchAndBacksOffDoublingUpToFiveMinutes()
	{
		EventQueue q = new EventQueue();
		q.addAll(events(3, 0));
		List<OfferEvent> batch = q.take(0);
		q.failed(1_000);
		assertEquals(3, q.size());
		assertEquals(EventQueue.FIRST_BACKOFF_MS, q.getBackoffMs());
		assertNull("not before the backoff", q.take(1_000 + EventQueue.FIRST_BACKOFF_MS - 1));
		assertNotNull(q.take(1_000 + EventQueue.FIRST_BACKOFF_MS));
		q.failed(10_000);
		assertEquals(2 * EventQueue.FIRST_BACKOFF_MS, q.getBackoffMs());
		for (int i = 0; i < 10; i++)
		{
			q.take(1_000_000L * (i + 1));
			q.failed(1_000_000L * (i + 1));
		}
		assertEquals(EventQueue.MAX_BACKOFF_MS, q.getBackoffMs());
		q.take(100_000_000L);
		q.sent(batch);
		assertEquals(0, q.getBackoffMs());
		assertEquals(0, q.size());
	}

	@Test
	public void theQueueIsBoundedAndKeepsTheNewest()
	{
		EventQueue q = new EventQueue();
		q.addAll(events(EventQueue.MAX + 10, 0));
		assertEquals(EventQueue.MAX, q.size());
		assertEquals(10, q.snapshot().get(0).getSeq());
	}

	@Test
	public void theLastSessionsEventsGoInFrontOfNewOnes()
	{
		EventQueue q = new EventQueue();
		q.addAll(events(2, 100));
		q.restore(events(3, 0));
		assertEquals(5, q.size());
		assertEquals(0, q.snapshot().get(0).getSeq());
		assertEquals(100, q.snapshot().get(3).getSeq());
		q.clear();
		assertEquals(0, q.size());
	}
}
