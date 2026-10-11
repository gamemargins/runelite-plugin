package com.gamemargins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/**
 * What the plugin last knew of one slot: the offer sitting in it, with the id this plugin gave it and how
 * many events it has sent about it. Persisted per account (Store), so a login after a while can tell what
 * finished while the plugin wasn't watching. A slot with no offer has {@code offerId == null}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SlotState
{
	private String offerId;
	private int seq;
	private int itemId;
	private boolean buy;
	private String state;
	private int quantity;
	private int filled;
	private long price;
	private long spent;
	/** When this plugin first saw the offer (ms). */
	private long startedAt;
	/** When the slot last changed (ms). */
	private long updatedAt;

	public boolean isEmpty()
	{
		return offerId == null;
	}

	/** Whether a read of this state's side is a buy. */
	public static boolean isBuyState(GrandExchangeOfferState state)
	{
		return state == GrandExchangeOfferState.BUYING || state == GrandExchangeOfferState.BOUGHT || state == GrandExchangeOfferState.CANCELLED_BUY;
	}

	/** The same offer, as far as a slot can tell: item, side, quantity and price, and a filled count that follows on. */
	public boolean sameOffer(GrandExchangeOffer offer)
	{
		if (isEmpty() || offer.getState() == GrandExchangeOfferState.EMPTY)
		{
			return false;
		}
		return offer.getItemId() == itemId
			&& isBuyState(offer.getState()) == buy
			&& offer.getTotalQuantity() == quantity
			&& offer.getPrice() == price
			&& offer.getQuantitySold() >= filled;
	}

	/** Whether the Grand Exchange is done with the offer: bought, sold or cancelled. */
	public boolean isFinished()
	{
		return "BOUGHT".equals(state) || "SOLD".equals(state) || "CANCELLED_BUY".equals(state) || "CANCELLED_SELL".equals(state);
	}

	public boolean isCancelled()
	{
		return "CANCELLED_BUY".equals(state) || "CANCELLED_SELL".equals(state);
	}
}
