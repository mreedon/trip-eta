/*
 * Copyright (c) 2026, mreedon
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.mreedon.tripeta;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.gameval.ItemID;

/**
 * Follows how many logs are in a log basket or forestry basket, from the messages the
 * game prints and from what happens to the inventory.
 *
 * The game never exposes the basket's contents directly. What it does give us:
 * - a distinct item ID for each of open/closed log basket and forestry basket,
 * - "The basket is full." / "Your basket is empty." / "The basket is empty.",
 * - the lines printed when emptying into the bank or the inventory,
 * - the "Check" summary, which lists "N x Some logs" per log type,
 * - and the fact that a log arriving with no inventory change went into an open basket.
 *
 * After login the contents are unknown and assumed empty until one of those corrects it.
 */
class BasketTracker
{
	static final int CAPACITY = 28;

	enum Outcome
	{
		NONE,
		UPDATED,
		EMPTIED_TO_BANK,
	}

	private static final String FULL = "The basket is full.";
	private static final String EMPTIED_TO_BANK = "You empty your basket into the bank.";
	private static final String CONTAINERS_EMPTIED_TO_BANK = "You empty all of your containers into the bank.";
	private static final String CONTAINERS_ALREADY_EMPTY = "Your containers are already empty.";
	private static final String IS_EMPTY = "Your basket is empty.";
	private static final String CHECKED_EMPTY = "The basket is empty.";
	private static final String EMPTIED_TO_INVENTORY = "You empty your basket.";
	/** Entries in the "Check" summary look like "12 x Redwood logs". */
	private static final Pattern CHECK_ENTRY = Pattern.compile("(\\d+)\\s*[×x]\\s*[A-Za-z][^,]*", Pattern.CASE_INSENSITIVE);

	private boolean present;
	private boolean open;
	private int used;

	static boolean isBasket(int itemId)
	{
		return itemId == ItemID.LOG_BASKET_CLOSED || itemId == ItemID.LOG_BASKET_OPEN
			|| itemId == ItemID.FORESTRY_BASKET_CLOSED || itemId == ItemID.FORESTRY_BASKET_OPEN;
	}

	static boolean isOpenBasket(int itemId)
	{
		return itemId == ItemID.LOG_BASKET_OPEN || itemId == ItemID.FORESTRY_BASKET_OPEN;
	}

	/** Called whenever the inventory changes, with whether a basket is in it and whether it is open. */
	void setPresent(boolean present, boolean open)
	{
		this.present = present;
		this.open = open;
	}

	boolean isPresent()
	{
		return present;
	}

	boolean isOpen()
	{
		return open;
	}

	int getUsed()
	{
		return used;
	}

	/** Logs the basket can still take; 0 when there is no basket. */
	int remaining()
	{
		return present ? Math.max(0, CAPACITY - used) : 0;
	}

	Outcome onMessage(String message)
	{
		switch (message)
		{
			case EMPTIED_TO_BANK:
			case CONTAINERS_EMPTIED_TO_BANK:
				used = 0;
				return Outcome.EMPTIED_TO_BANK;
			case CONTAINERS_ALREADY_EMPTY:
			case IS_EMPTY:
			case CHECKED_EMPTY:
			case EMPTIED_TO_INVENTORY:
				used = 0;
				return Outcome.UPDATED;
			case FULL:
				used = CAPACITY;
				return Outcome.UPDATED;
			default:
				break;
		}
		Matcher m = CHECK_ENTRY.matcher(message);
		int total = 0;
		boolean any = false;
		while (m.find())
		{
			any = true;
			total += Integer.parseInt(m.group(1));
		}
		if (any)
		{
			used = Math.min(CAPACITY, total);
			return Outcome.UPDATED;
		}
		return Outcome.NONE;
	}

	/** Items that arrived (per the chat) but never took an inventory slot went into the basket. */
	void onItemsWithoutInventoryGain(int n)
	{
		if (present && n > 0)
		{
			used = Math.min(CAPACITY, used + n);
		}
	}

	/**
	 * An item took an inventory slot while the basket was open. An open basket takes every
	 * item until it is full, so this can only happen when it is: the count snaps to capacity.
	 * This is what recovers from the unknown-after-login state without a manual Check.
	 */
	void onItemsIntoInventoryWhileOpen(int n)
	{
		if (present && open && n > 0)
		{
			used = CAPACITY;
		}
	}

	/** Inventory slots gained with no item arriving came out of the basket ("empty as many as you can carry"). */
	void onInventoryGainWithoutItems(int n)
	{
		if (present && n > 0)
		{
			used = Math.max(0, used - n);
		}
	}

	/** A large inventory drop away from any bank is the player filling the basket by hand. */
	void onManualFill(int slotsDropped)
	{
		if (present && slotsDropped > 0)
		{
			used = Math.min(CAPACITY, used + slotsDropped);
		}
	}

	void reset()
	{
		present = false;
		open = false;
		used = 0;
	}
}
