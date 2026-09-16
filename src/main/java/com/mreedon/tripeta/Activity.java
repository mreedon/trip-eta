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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A gathering activity the plugin understands: how to tell the player is doing it
 * (the local player's animation) and how to tell an item just arrived (a chat line).
 *
 * The animation ids themselves live in {@link GatheringAnimations}, so that what an
 * activity means stays readable without scrolling past every pickaxe in the game.
 */
enum Activity
{
	WOODCUTTING(
		"Chopping",
		"logs",
		"Off tree",
		// Felling axe with forester's rations: one successful chop in five is a clean cut with no log.
		0.8,
		// The game rolls for a log once every 4 ticks; the axe changes the success chance,
		// not the cadence. Confirmed from 9,848 chop-to-chop gaps in the author's own log:
		// 96% sit within 15% of a multiple of 2.4 s (a uniform spread would give 30%).
		4,
		// A tree keeps giving logs, so any pause is the player, not the work.
		0,
		GatheringAnimations.WOODCUTTING,
		// Same shape as the core Woodcutting plugin's pattern, plus the two bonus-log lines
		// (Kandarin headgear, nature offerings) that hand over a log without a "You get" line.
		Pattern.compile("^(?:You get (?:some|an) ?([\\w' -]*(?:logs?|mushrooms))"
			+ "|Your Kandarin headgear provides you with an additional log"
			+ "|The nature offerings enabled you to chop an extra log)\\.$"),
		// Felling axe with forester's rations: a successful chop that yields no log.
		Pattern.compile("^You strike a clean cut without gathering any material\\.$")
	),

	MINING(
		"Mining",
		"ores",
		"Off rock",
		// No known no-item outcome for a successful swing; the coin never applies.
		1.0,
		// Pickaxes change the swing cadence as well as the odds, so no single cycle; the
		// range falls back to the conservative end until this is measured.
		0,
		// Most rocks give one ore and empty, so every ore ends with a hop to the next rock
		// (a few ticks) or a wait for a respawn (iron: 9 ticks, 4 in the Mining Guild;
		// amethyst empties every 2-3 ores). 12 seconds covers those; longer is time away.
		20,
		GatheringAnimations.MINING,
		// Ore lines, plus the bonus-ore lines (Varrock armour, Mining cape, celestial ring)
		// that hand over an ore without a "You manage to mine" line. The hyphen matters:
		// Motherlode Mine gives "You manage to mine some pay-dirt."
		Pattern.compile("^(?:You manage to mine (?:some|an?) ([\\w' -]+)"
			+ "|The Varrock platebody enabled you to mine an additional ore"
			+ "|Your cape allows you to mine an additional ore"
			+ "|Your celestial ring allows you to mine an additional ore)\\.$",
			Pattern.CASE_INSENSITIVE),
		null
	),

	FISHING(
		"Fishing",
		"fish",
		"Off spot",
		1.0,
		// Fishing cadence varies by method; unknown until measured, so the range stays conservative.
		0,
		// A spot moves a few tiles now and then; 6 seconds covers the walk to it.
		10,
		GatheringAnimations.FISHING,
		// Core Fishing plugin's catch regex, the ice-gloves suffix, and the extra-fish lines
		// (Rada's blessing, angler's outfit) that hand over a fish without a "You catch" line.
		Pattern.compile("^(?:You catch (?:an?|some) ([\\w' -]+?)"
			+ "|Your cormorant returns with its catch"
			+ "|.+ enabled you to catch an extra fish)[.!]"
			+ "(?: It hardens as you handle it with your ice gloves\\.)?$"),
		// Deliberately none. A karambwan stealing the bait ("A karambwan deftly snatches
		// the karambwanji from your vessel.") is a failed catch, not a successful roll that
		// withheld a fish, and its odds are not known. Counting it as a roll would divide
		// the rate by a number this model has no constant for. Left alone, the time those
		// attempts take is absorbed into seconds per fish, which is what the estimate wants.
		null
	);

	/** Verb shown on the overlay, e.g. "Chopping". */
	final String verb;
	/** Plural noun for the gathered item, e.g. "logs". */
	final String itemNoun;
	/** Label for time spent not gathering, e.g. "Off tree". */
	final String offLabel;
	/** Chance a successful roll yields an item once the no-item outcome has been observed. */
	final double itemChanceWithMisses;
	/** Game ticks between the game's success rolls for this activity; 0 if not known. */
	final int rollTicks;
	/**
	 * Longest pause, in ticks, that is part of the work (a hop to the next rock, a spot
	 * moving) rather than time away. Credited to gathering once gathering resumes.
	 */
	final int pauseGraceTicks;
	private final Set<Integer> animations;
	private final Pattern itemMessage;
	private final Pattern rollWithoutItemMessage;

	Activity(String verb, String itemNoun, String offLabel, double itemChanceWithMisses, int rollTicks, int pauseGraceTicks, int[] animationIds, Pattern itemMessage, Pattern rollWithoutItemMessage)
	{
		this.verb = verb;
		this.itemNoun = itemNoun;
		this.offLabel = offLabel;
		this.itemChanceWithMisses = itemChanceWithMisses;
		this.rollTicks = rollTicks;
		this.pauseGraceTicks = pauseGraceTicks;
		Set<Integer> ids = new HashSet<>();
		for (int id : animationIds)
		{
			ids.add(id);
		}
		this.animations = Collections.unmodifiableSet(ids);
		this.itemMessage = itemMessage;
		this.rollWithoutItemMessage = rollWithoutItemMessage;
	}

	boolean isItemMessage(String message)
	{
		return itemMessage.matcher(message).matches();
	}

	/**
	 * What the message says arrived, normalised for use as a settings key: "redwood logs"
	 * becomes {@code redwood_logs}. Null when the line names nothing, which is how the
	 * bonus-item lines read ("Your Kandarin headgear provides you with an additional log")
	 * and how a clean cut reads. Those inherit whatever the trip has been naming.
	 */
	String itemTypeOf(String message)
	{
		Matcher m = itemMessage.matcher(message);
		if (!m.matches() || m.groupCount() < 1)
		{
			return null;
		}
		return normaliseType(m.group(1));
	}

	/** Lower case, single underscores, letters and digits only; null if nothing usable is left. */
	static String normaliseType(String raw)
	{
		if (raw == null)
		{
			return null;
		}
		String key = raw.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "_");
		key = key.replaceAll("^_+|_+$", "");
		// A settings key has to stay recognisable and bounded; anything odd is dropped
		// rather than written, so a surprising message cannot litter the profile.
		return key.isEmpty() || key.length() > 40 ? null : key;
	}

	boolean isRollWithoutItemMessage(String message)
	{
		return rollWithoutItemMessage != null && rollWithoutItemMessage.matcher(message).matches();
	}

	/** The activity whose animation this is, or null if the player is not gathering. */
	static Activity forAnimation(int animationId)
	{
		for (Activity a : values())
		{
			if (a.animations.contains(animationId))
			{
				return a;
			}
		}
		return null;
	}

	static Activity forItemMessage(String message)
	{
		return Arrays.stream(values())
			.filter(a -> a.isItemMessage(message))
			.findFirst()
			.orElse(null);
	}
}
