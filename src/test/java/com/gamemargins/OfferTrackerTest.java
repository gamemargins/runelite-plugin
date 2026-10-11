package com.gamemargins;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;
import org.junit.Before;
import org.junit.Test;

public class OfferTrackerTest
{
	private static final int WHIP = 4151;
	private static final long T = 1_791_700_000_000L;
	private static final long MIN = 60_000;

	private final AtomicLong clock = new AtomicLong(T);
	private final AtomicInteger ids = new AtomicInteger();
	private OfferTracker tracker;

	@Before
	public void setUp()
	{
		tracker = new OfferTracker("12345", clock::get, () -> "offer-" + ids.incrementAndGet());
		tracker.setName("Zezima");
	}

	private static FakeOffer buying(int total, int sold, long spent)
	{
		return new FakeOffer(WHIP, GrandExchangeOfferState.BUYING, total, sold, 1_500_000, spent);
	}

	@Test
	public void aBuyFillsInStepsAndEachStepIsAnEventAndAPurchase()
	{
		List<OfferEvent> first = tracker.onChange(2, buying(10, 0, 0));
		assertEquals(1, first.size());
		OfferEvent e0 = first.get(0);
		assertEquals("offer-1", e0.getOfferId());
		assertEquals(0, e0.getSeq());
		assertEquals("BUYING", e0.getState());
		assertEquals(2, e0.getSlot());
		assertEquals("12345", e0.getAccount());
		assertEquals("Zezima", e0.getName());
		assertTrue(e0.isBuy());
		assertFalse(e0.isOffline());

		// The same read again changes nothing.
		assertTrue(tracker.onChange(2, buying(10, 0, 0)).isEmpty());

		clock.set(T + 2 * MIN);
		List<OfferEvent> second = tracker.onChange(2, buying(10, 4, 6_000_000));
		assertEquals(1, second.size());
		assertEquals(1, second.get(0).getSeq());
		assertEquals(4, second.get(0).getFilled());
		assertEquals(6_000_000, second.get(0).getSpent());
		assertEquals(T + 2 * MIN, second.get(0).getAt());

		clock.set(T + 10 * MIN);
		List<OfferEvent> done = tracker.onChange(2, new FakeOffer(WHIP, GrandExchangeOfferState.BOUGHT, 10, 10, 1_500_000, 15_000_000));
		assertEquals("BOUGHT", done.get(0).getState());
		assertEquals(2, done.get(0).getSeq());

		List<Purchase> purchases = tracker.getPurchases();
		assertEquals(2, purchases.size());
		assertEquals(4, purchases.get(0).getQuantity());
		assertEquals(T + 2 * MIN, purchases.get(0).getAt());
		assertEquals(6, purchases.get(1).getQuantity());
		assertEquals(T + 10 * MIN, purchases.get(1).getAt());

		// Collected: the offer's last event, then the slot is free.
		clock.set(T + 12 * MIN);
		List<OfferEvent> collected = tracker.onChange(2, FakeOffer.empty());
		assertEquals("EMPTY", collected.get(0).getState());
		assertEquals("offer-1", collected.get(0).getOfferId());
		assertEquals(3, collected.get(0).getSeq());
		assertEquals(10, collected.get(0).getFilled());
		assertTrue(tracker.getSlots()[2].isEmpty());
		assertTrue(tracker.onChange(2, FakeOffer.empty()).isEmpty());
	}

	@Test
	public void aSaleIsNoPurchaseAndACancelKeepsWhatFilled()
	{
		tracker.onChange(0, new FakeOffer(WHIP, GrandExchangeOfferState.SELLING, 5, 0, 1_600_000, 0));
		tracker.onChange(0, new FakeOffer(WHIP, GrandExchangeOfferState.SELLING, 5, 2, 1_600_000, 3_200_000));
		assertTrue(tracker.getPurchases().isEmpty());
		List<OfferEvent> cancelled = tracker.onChange(0, new FakeOffer(WHIP, GrandExchangeOfferState.CANCELLED_SELL, 5, 2, 1_600_000, 3_200_000));
		assertEquals("CANCELLED_SELL", cancelled.get(0).getState());
		assertEquals(2, cancelled.get(0).getFilled());
		assertFalse(cancelled.get(0).isBuy());
	}

	@Test
	public void aNewOfferInTheSameSlotWithoutAnEmptyReadCollectsTheOldOne()
	{
		tracker.onChange(1, buying(10, 10, 15_000_000));
		// A different quantity: another offer; the old one gets its EMPTY first.
		List<OfferEvent> events = tracker.onChange(1, buying(20, 0, 0));
		assertEquals(2, events.size());
		assertEquals("EMPTY", events.get(0).getState());
		assertEquals("offer-1", events.get(0).getOfferId());
		assertEquals("BUYING", events.get(1).getState());
		assertEquals("offer-2", events.get(1).getOfferId());
		assertEquals(0, events.get(1).getSeq());
	}

	@Test
	public void theLoginReplayIsDroppedAndTheReconciliationReportsWhatHappenedOffline()
	{
		// Before logging out: a buy half filled in slot 3, a sell in slot 5, nothing in slot 0.
		tracker.onChange(3, buying(10, 4, 6_000_000));
		tracker.onChange(5, new FakeOffer(560, GrandExchangeOfferState.SELLING, 100, 0, 200, 0));
		int purchasesBefore = tracker.getPurchases().size();

		tracker.beginReplay();
		assertTrue(tracker.isReplaying());
		// The burst: slots read EMPTY first, then their real states; nothing comes out of it.
		assertTrue(tracker.onChange(3, FakeOffer.empty()).isEmpty());
		assertTrue(tracker.onChange(3, buying(10, 10, 15_000_000)).isEmpty());

		clock.set(T + 3 * 60 * MIN);
		GrandExchangeOffer[] live = new GrandExchangeOffer[8];
		for (int i = 0; i < 8; i++)
		{
			live[i] = FakeOffer.empty();
		}
		live[3] = new FakeOffer(WHIP, GrandExchangeOfferState.BOUGHT, 10, 10, 1_500_000, 15_000_000); // finished while away
		live[5] = FakeOffer.empty(); // collected on another client
		live[0] = new FakeOffer(560, GrandExchangeOfferState.SELLING, 50, 10, 210, 2_100); // placed elsewhere
		List<OfferEvent> events = tracker.endReplay(live);
		assertFalse(tracker.isReplaying());
		assertEquals(3, events.size());

		OfferEvent finished = events.stream().filter(e -> e.getSlot() == 3).findFirst().get();
		assertEquals("BOUGHT", finished.getState());
		assertEquals("offer-1", finished.getOfferId());
		assertTrue(finished.isOffline());
		assertEquals(T + 3 * 60 * MIN, finished.getAt());
		// The six that filled while away count as a purchase now, not earlier: the timer can only be late.
		assertEquals(purchasesBefore + 1, tracker.getPurchases().size());
		Purchase offline = tracker.getPurchases().get(purchasesBefore);
		assertEquals(6, offline.getQuantity());
		assertEquals(T + 3 * 60 * MIN, offline.getAt());

		OfferEvent gone = events.stream().filter(e -> e.getSlot() == 5).findFirst().get();
		assertEquals("EMPTY", gone.getState());
		assertEquals("offer-2", gone.getOfferId());
		assertTrue(tracker.getSlots()[5].isEmpty());

		OfferEvent fresh = events.stream().filter(e -> e.getSlot() == 0).findFirst().get();
		assertEquals("SELLING", fresh.getState());
		assertEquals("offer-3", fresh.getOfferId());
		assertEquals(0, fresh.getSeq());
		assertTrue(fresh.isOffline());
	}

	@Test
	public void aReplayWithNothingChangedEmitsNothing()
	{
		tracker.onChange(3, buying(10, 4, 6_000_000));
		tracker.beginReplay();
		GrandExchangeOffer[] live = new GrandExchangeOffer[8];
		for (int i = 0; i < 8; i++)
		{
			live[i] = FakeOffer.empty();
		}
		live[3] = buying(10, 4, 6_000_000);
		assertTrue(tracker.endReplay(live).isEmpty());
		assertEquals("offer-1", tracker.getSlots()[3].getOfferId());
	}

	@Test
	public void restoreBringsSlotsAndPurchasesBackAndDropsOldPurchases()
	{
		SlotState[] saved = new SlotState[8];
		saved[7] = new SlotState("saved-offer", 2, WHIP, true, "BUYING", 10, 3, 1_500_000, 4_500_000, T - MIN, T - MIN);
		List<Purchase> purchases = new java.util.ArrayList<>();
		purchases.add(new Purchase(WHIP, 3, T - MIN));
		purchases.add(new Purchase(WHIP, 1, T - 2 * BuyLimits.LOOKBACK_MS));
		assertFalse(tracker.isRestored());
		tracker.restore(saved, purchases);
		assertTrue(tracker.isRestored());
		assertEquals("saved-offer", tracker.getSlots()[7].getOfferId());
		assertEquals(1, tracker.getPurchases().size());
		// The next fill of the restored offer carries on its sequence.
		List<OfferEvent> events = tracker.onChange(7, buying(10, 10, 15_000_000));
		assertEquals(3, events.get(0).getSeq());
		assertEquals("saved-offer", events.get(0).getOfferId());
		assertNull(tracker.getSlots()[0].getOfferId());
	}
}
