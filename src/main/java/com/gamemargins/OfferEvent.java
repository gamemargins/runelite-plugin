package com.gamemargins;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One change of a Grand Exchange slot, as gamemargins.com receives it (POST /api/runelite/offers).
 * Field names are the JSON names; Gson serialises this class as is. Mirrors OfferEvent in the site's
 * core (packages/core/src/osrs/runelite.ts).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfferEvent
{
	/** The game's account hash, as a decimal string: a Java long doesn't fit a JavaScript number. */
	private String account;
	/** The in-game name, when known. */
	private String name;
	/** 0 to 7. */
	private int slot;
	/** This plugin's own id for the offer: new when a slot goes from empty to buying or selling. */
	private String offerId;
	/** 0, 1, 2… per offer: the order of its events, so a resent batch changes nothing on the server. */
	private int seq;
	private int itemId;
	private boolean buy;
	/** EMPTY, BUYING, BOUGHT, CANCELLED_BUY, SELLING, SOLD or CANCELLED_SELL (RuneLite's GrandExchangeOfferState). */
	private String state;
	/** Asked for. */
	private int quantity;
	/** Filled so far. */
	private int filled;
	/** The offer's price per item. */
	private long price;
	/** Coins moved so far: spent on a buy, received (before tax) on a sell. */
	private long spent;
	/** When the slot changed, ms since the epoch. */
	private long at;
	/** The change was found at login: the fill happened some time while the plugin wasn't watching. */
	private boolean offline;
}
