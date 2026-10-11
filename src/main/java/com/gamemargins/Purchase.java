package com.gamemargins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Items bought at a moment: what the buy-limit windows count (BuyLimits). Persisted with the account for a day. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Purchase
{
	private int itemId;
	private int quantity;
	/** ms since the epoch. */
	private long at;
}
