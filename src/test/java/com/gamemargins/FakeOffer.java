package com.gamemargins;

import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.GrandExchangeOfferState;

/** A slot as the client would report it, for the tests. */
class FakeOffer implements GrandExchangeOffer
{
	private final int itemId;
	private final GrandExchangeOfferState state;
	private final int total;
	private final int sold;
	private final long price;
	private final long spent;

	FakeOffer(int itemId, GrandExchangeOfferState state, int total, int sold, long price, long spent)
	{
		this.itemId = itemId;
		this.state = state;
		this.total = total;
		this.sold = sold;
		this.price = price;
		this.spent = spent;
	}

	static FakeOffer empty()
	{
		return new FakeOffer(0, GrandExchangeOfferState.EMPTY, 0, 0, 0, 0);
	}

	@Override
	public int getQuantitySold()
	{
		return sold;
	}

	@Override
	public int getItemId()
	{
		return itemId;
	}

	@Override
	public int getTotalQuantity()
	{
		return total;
	}

	@Override
	public long getPrice()
	{
		return price;
	}

	@Override
	public long getSpent()
	{
		return spent;
	}

	@Override
	public GrandExchangeOfferState getState()
	{
		return state;
	}
}
