package com.gamemargins;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * Turns what the eight slots report into events for gamemargins.com, for one account. Pure: it holds the
 * slots' last known state, takes offer reads and a clock, and gives back the events to queue, so it can be
 * tested without a client.
 *
 * <p>The rules it knows:
 * <ul>
 *   <li>A slot that goes from empty to buying or selling starts a new offer with a fresh id; the same item,
 *   side, quantity and price with a filled count that follows on is the same offer; anything else in a
 *   non-empty slot is a new offer (the empty read in between was missed).</li>
 *   <li>Every read that changes the offer (state, filled, spent) is an event with the next sequence number;
 *   a read that changes nothing is dropped.</li>
 *   <li>A slot that reads empty while an offer was known is the offer's collection: an EMPTY event, then
 *   the slot is free.</li>
 *   <li>Logging in replays every slot, and a slot can read EMPTY before its real state arrives. The plugin
 *   calls {@link #beginReplay()} at login and drops the reads that follow; once the client has settled
 *   ({@link #endReplay} with the live slots) the tracker reconciles each slot against what it knew: an offer
 *   that finished or filled further while logged out is reported with {@code offline} set and the time of
 *   the reconciliation (it can only be late, never early, so a buy-limit timer never says "free" too soon),
 *   one that vanished is collected, one never seen before is started.</li>
 *   <li>Each increase of a buy's filled count is a {@link Purchase} at that moment, for the limit windows.</li>
 * </ul>
 */
public class OfferTracker
{
	public static final int SLOTS = 8;

	private final String account;
	private final Supplier<Long> clock;
	private final Supplier<String> ids;
	private final SlotState[] slots = new SlotState[SLOTS];
	private final List<Purchase> purchases = new ArrayList<>();
	private String name;
	private boolean replaying;
	/** Whether the persisted state has been read (or there was none): the login reconciliation waits for it. */
	private volatile boolean restored;

	public OfferTracker(String account, Supplier<Long> clock)
	{
		this(account, clock, () -> UUID.randomUUID().toString());
	}

	public OfferTracker(String account, Supplier<Long> clock, Supplier<String> ids)
	{
		this.account = account;
		this.clock = clock;
		this.ids = ids;
		for (int i = 0; i < SLOTS; i++)
		{
			slots[i] = new SlotState();
		}
	}

	public String getAccount()
	{
		return account;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public SlotState[] getSlots()
	{
		return slots;
	}

	/** The purchases of the last day (older ones are dropped as they age out). */
	public List<Purchase> getPurchases()
	{
		return purchases;
	}

	/** Restores the persisted state (Store); null for an account without one. */
	public void restore(SlotState[] saved, List<Purchase> savedPurchases)
	{
		if (saved != null)
		{
			for (int i = 0; i < SLOTS && i < saved.length; i++)
			{
				if (saved[i] != null)
				{
					slots[i] = saved[i];
				}
			}
		}
		purchases.clear();
		if (savedPurchases != null)
		{
			purchases.addAll(savedPurchases);
		}
		prunePurchases();
		restored = true;
	}

	public boolean isRestored()
	{
		return restored;
	}

	public boolean isReplaying()
	{
		return replaying;
	}

	/** Login (or a world hop) is about to replay the slots: the reads that follow are dropped until {@link #endReplay}. */
	public void beginReplay()
	{
		replaying = true;
	}

	/**
	 * The client has settled after login: compares every slot with what the plugin knew and emits what
	 * happened while it wasn't watching. {@code live} is the client's current offers, in slot order.
	 */
	public List<OfferEvent> endReplay(GrandExchangeOffer[] live)
	{
		replaying = false;
		List<OfferEvent> events = new ArrayList<>();
		if (live == null)
		{
			return events;
		}
		for (int i = 0; i < SLOTS && i < live.length; i++)
		{
			events.addAll(read(i, live[i], true));
		}
		return events;
	}

	/** A live change of a slot: the events to queue (none when nothing changed, or while replaying). */
	public List<OfferEvent> onChange(int slot, GrandExchangeOffer offer)
	{
		if (replaying || slot < 0 || slot >= SLOTS)
		{
			return new ArrayList<>();
		}
		return read(slot, offer, false);
	}

	private List<OfferEvent> read(int slot, GrandExchangeOffer offer, boolean offline)
	{
		List<OfferEvent> events = new ArrayList<>();
		if (offer == null)
		{
			return events;
		}
		SlotState known = slots[slot];
		long now = clock.get();
		if (offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			if (!known.isEmpty())
			{
				// Collected (or gone while logged out): the offer's last event.
				known.setSeq(known.getSeq() + 1);
				known.setUpdatedAt(now);
				events.add(event(known, slot, "EMPTY", now, offline));
				slots[slot] = new SlotState();
			}
			return events;
		}
		if (!known.isEmpty() && !known.sameOffer(offer))
		{
			// Another offer sits there now: the known one was collected without us seeing the empty slot.
			known.setSeq(known.getSeq() + 1);
			known.setUpdatedAt(now);
			events.add(event(known, slot, "EMPTY", now, offline));
			slots[slot] = new SlotState();
			known = slots[slot];
		}
		boolean buy = SlotState.isBuyState(offer.getState());
		if (known.isEmpty())
		{
			SlotState fresh = new SlotState(ids.get(), 0, offer.getItemId(), buy, offer.getState().name(), offer.getTotalQuantity(), offer.getQuantitySold(), offer.getPrice(), offer.getSpent(), now, now);
			slots[slot] = fresh;
			if (buy && fresh.getFilled() > 0)
			{
				notePurchase(fresh.getItemId(), fresh.getFilled(), now);
			}
			events.add(event(fresh, slot, fresh.getState(), now, offline));
			return events;
		}
		boolean changed = !known.getState().equals(offer.getState().name()) || known.getFilled() != offer.getQuantitySold() || known.getSpent() != offer.getSpent();
		if (!changed)
		{
			return events;
		}
		int before = known.getFilled();
		known.setSeq(known.getSeq() + 1);
		known.setState(offer.getState().name());
		known.setFilled(Math.max(known.getFilled(), offer.getQuantitySold()));
		known.setSpent(Math.max(known.getSpent(), offer.getSpent()));
		known.setUpdatedAt(now);
		if (buy && known.getFilled() > before)
		{
			notePurchase(known.getItemId(), known.getFilled() - before, now);
		}
		events.add(event(known, slot, known.getState(), now, offline));
		return events;
	}

	private OfferEvent event(SlotState s, int slot, String state, long at, boolean offline)
	{
		return new OfferEvent(account, name, slot, s.getOfferId(), s.getSeq(), s.getItemId(), s.isBuy(), state, s.getQuantity(), s.getFilled(), s.getPrice(), s.getSpent(), at, offline);
	}

	private void notePurchase(int itemId, int quantity, long at)
	{
		purchases.add(new Purchase(itemId, quantity, at));
		prunePurchases();
	}

	/** Purchases older than a day can't be in any window that is still open (BuyLimits looks back that far). */
	private void prunePurchases()
	{
		long cutoff = clock.get() - BuyLimits.LOOKBACK_MS;
		purchases.removeIf(p -> p.getAt() < cutoff);
	}
}
