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
 * Follows how many items are in a gathering container: a log basket or forestry basket
 * for logs, a fish barrel or fish sack barrel for raw fish. Each holds 28, each has an
 * open state that collects on its own and a closed one that needs a manual fill, and
 * each can be worn as well as carried.
 *
 * The game never exposes the contents directly. What it does give us:
 * - a distinct item ID for each of open/closed, basket/barrel,
 * - "The basket is full." / "Your basket is empty." / "The basket is empty.",
 * - the lines printed when emptying into the bank or the inventory,
 * - the "Check" summary, which lists "N x Some logs" per item type,
 * - and the fact that an item arriving with no inventory change went into an open container.
 *
 * The basket's lines are verbatim from the game. Of the barrel's only the full line is
 * known for sure ("The barrel is full. It may be emptied at a bank."); the rest are
 * taken to follow the basket's with the noun swapped, and the plugin logs any barrel
 * line it does not recognise so those guesses can be checked against play.
 *
 * After login the contents are unknown and assumed empty until one of those corrects it.
 */
class BasketTracker
{
	static final int CAPACITY = 28;

	enum Kind
	{
		LOG_BASKET("basket", Activity.WOODCUTTING),
		FISH_BARREL("barrel", Activity.FISHING);

		/** The word the game uses for it in chat and in the Check box. */
		final String noun;
		/** The activity whose items it collects. */
		final Activity activity;

		Kind(String noun, Activity activity)
		{
			this.noun = noun;
			this.activity = activity;
		}
	}

	enum Outcome
	{
		NONE,
		UPDATED,
		EMPTIED_TO_BANK,
	}

	private static final Pattern FULL = Pattern.compile("^The (?:basket|barrel) is full\\.(?: It may be emptied at a bank\\.)?$");
	private static final Pattern EMPTIED_TO_BANK = Pattern.compile("^You empty (?:your|the) (?:basket|barrel) into the bank\\.$");
	private static final String CONTAINERS_EMPTIED_TO_BANK = "You empty all of your containers into the bank.";
	private static final String CONTAINERS_ALREADY_EMPTY = "Your containers are already empty.";
	private static final Pattern IS_EMPTY = Pattern.compile("^(?:Your|The) (?:basket|barrel) is empty\\.$");
	private static final Pattern EMPTIED_TO_INVENTORY = Pattern.compile("^You empty (?:your|the) (?:basket|barrel)\\.$");
	/** Any line that talks about a basket or barrel, recognised or not. */
	private static final Pattern MENTIONS = Pattern.compile("\\b(?:basket|barrel)\\b", Pattern.CASE_INSENSITIVE);
	/** Entries in the "Check" item box look like "12 x Redwood logs". */
	private static final Pattern CHECK_ENTRY = Pattern.compile("(\\d+)\\s*[×x]\\s+(?=[A-Za-z])", Pattern.CASE_INSENSITIVE);

	private Kind kind;
	private boolean open;
	private boolean worn;
	private int used;

	/** Which container this item is, or null if it is not one. */
	static Kind kindOf(int itemId)
	{
		switch (itemId)
		{
			case ItemID.LOG_BASKET_CLOSED:
			case ItemID.LOG_BASKET_OPEN:
			case ItemID.FORESTRY_BASKET_CLOSED:
			case ItemID.FORESTRY_BASKET_OPEN:
				return Kind.LOG_BASKET;
			case ItemID.FISH_BARREL_CLOSED:
			case ItemID.FISH_BARREL_OPEN:
			case ItemID.FISH_SACK_BARREL_CLOSED:
			case ItemID.FISH_SACK_BARREL_OPEN:
				return Kind.FISH_BARREL;
			default:
				return null;
		}
	}

	static boolean isOpen(int itemId)
	{
		return itemId == ItemID.LOG_BASKET_OPEN || itemId == ItemID.FORESTRY_BASKET_OPEN
			|| itemId == ItemID.FISH_BARREL_OPEN || itemId == ItemID.FISH_SACK_BARREL_OPEN;
	}

	static boolean mentionsContainer(String message)
	{
		return MENTIONS.matcher(message).find();
	}

	/**
	 * Called whenever the inventory or worn equipment changes: which container is carried
	 * (null for none), whether it is open, and whether it is the worn one.
	 */
	void setPresent(Kind kind, boolean open, boolean worn)
	{
		if (kind != this.kind)
		{
			// A different container has its own contents; the count does not carry over.
			used = 0;
		}
		this.kind = kind;
		this.open = open;
		this.worn = worn;
	}

	boolean isPresent()
	{
		return kind != null;
	}

	Kind getKind()
	{
		return kind;
	}

	/** Whether the carried container collects what this activity gathers. */
	boolean takes(Activity activity)
	{
		return kind != null && kind.activity == activity;
	}

	/** "basket" or "barrel", for labels. */
	String noun()
	{
		return kind == null ? "basket" : kind.noun;
	}

	boolean isOpen()
	{
		return open;
	}

	boolean isWorn()
	{
		return worn;
	}

	int getUsed()
	{
		return used;
	}

	/** Items the container can still take; 0 when there is none. */
	int remaining()
	{
		return kind != null ? Math.max(0, CAPACITY - used) : 0;
	}

	Outcome onMessage(String message)
	{
		if (CONTAINERS_EMPTIED_TO_BANK.equals(message) || EMPTIED_TO_BANK.matcher(message).matches())
		{
			used = 0;
			return Outcome.EMPTIED_TO_BANK;
		}
		if (CONTAINERS_ALREADY_EMPTY.equals(message)
			|| IS_EMPTY.matcher(message).matches()
			|| EMPTIED_TO_INVENTORY.matcher(message).matches())
		{
			used = 0;
			return Outcome.UPDATED;
		}
		if (FULL.matcher(message).matches())
		{
			used = CAPACITY;
			return Outcome.UPDATED;
		}
		return Outcome.NONE;
	}

	/**
	 * The text of the item box the game opens for "Check". This is a widget, not a chat
	 * line: "The basket contains:" followed by one "N x Some logs" entry per item type,
	 * with line breaks as {@code <br>} tags. An empty container says so in words.
	 */
	Outcome onCheckText(String text)
	{
		if (text == null)
		{
			return Outcome.NONE;
		}
		String plain = text.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
		if (plain.contains("basket is empty") || plain.contains("barrel is empty"))
		{
			used = 0;
			return Outcome.UPDATED;
		}
		if (!plain.contains("The basket contains") && !plain.contains("The barrel contains"))
		{
			return Outcome.NONE;
		}
		Matcher m = CHECK_ENTRY.matcher(plain);
		int total = 0;
		boolean any = false;
		while (m.find())
		{
			any = true;
			total += Integer.parseInt(m.group(1));
		}
		if (!any)
		{
			return Outcome.NONE;
		}
		used = Math.min(CAPACITY, total);
		return Outcome.UPDATED;
	}

	/** Items that arrived (per the chat) but never took an inventory slot went into the container. */
	void onItemsWithoutInventoryGain(int n)
	{
		if (kind != null && n > 0)
		{
			used = Math.min(CAPACITY, used + n);
		}
	}

	/**
	 * An item took an inventory slot while the container was open. An open container takes
	 * every item until it is full, so this can only happen when it is: the count snaps to
	 * capacity. This is what recovers from the unknown-after-login state without a manual Check.
	 */
	void onItemsIntoInventoryWhileOpen(int n)
	{
		if (kind != null && open && n > 0)
		{
			used = CAPACITY;
		}
	}

	/** Inventory slots gained with no item arriving came out of the container ("empty as many as you can carry"). */
	void onInventoryGainWithoutItems(int n)
	{
		if (kind != null && n > 0)
		{
			used = Math.max(0, used - n);
		}
	}

	/** A large inventory drop away from any bank is the player filling the container by hand. */
	void onManualFill(int slotsDropped)
	{
		if (kind != null && slotsDropped > 0)
		{
			used = Math.min(CAPACITY, used + slotsDropped);
		}
	}

	void reset()
	{
		kind = null;
		open = false;
		worn = false;
		used = 0;
	}
}
