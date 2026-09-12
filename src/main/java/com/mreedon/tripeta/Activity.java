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
import java.util.Set;
import java.util.regex.Pattern;
import net.runelite.api.gameval.AnimationID;

/**
 * A gathering activity the plugin understands: how to tell the player is doing it
 * (the local player's animation) and how to tell an item just arrived (a chat line).
 *
 * Animation sets mirror the core RuneLite plugins for each skill (Woodcutting,
 * MiningAnimation, and the Idle Notifier's fishing set), so anything core treats as
 * "doing the skill" counts as gathering time here too.
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
		new int[]{
			AnimationID.HUMAN_WOODCUTTING_BRONZE_AXE,
			AnimationID.HUMAN_WOODCUTTING_IRON_AXE,
			AnimationID.HUMAN_WOODCUTTING_STEEL_AXE,
			AnimationID.HUMAN_WOODCUTTING_BLACK_AXE,
			AnimationID.HUMAN_WOODCUTTING_MITHRIL_AXE,
			AnimationID.HUMAN_WOODCUTTING_ADAMANT_AXE,
			AnimationID.HUMAN_WOODCUTTING_RUNE_AXE,
			AnimationID.HUMAN_WOODCUTTING_GILDED_AXE,
			AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE,
			AnimationID.HUMAN_WOODCUTTING_INFERNAL_AXE,
			AnimationID.HUMAN_WOODCUTTING_3A_AXE,
			AnimationID.HUMAN_WOODCUTTING_CRYSTAL_AXE,
			AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE,
			AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_AXE_NO_INFERNAL,
			AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE,
			AnimationID.HUMAN_WOODCUTTING_TRAILBLAZER_RELOADED_AXE_NO_INFERNAL,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_BRONZE,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_IRON,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_STEEL,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_BLACK,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_MITHRIL,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_ADAMANT,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_RUNE,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_DRAGON,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL_INACTIVE,
			AnimationID.FORESTRY_2H_AXE_CHOPPING_3A,
		},
		// Same shape as the core Woodcutting plugin's pattern, plus the two bonus-log lines
		// (Kandarin headgear, nature offerings) that hand over a log without a "You get" line.
		Pattern.compile("^(?:You get (?:some|an)[\\w' ]+(?:logs?|mushrooms)"
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
		new int[]{
			AnimationID.HUMAN_MINING_BRONZE_PICKAXE,
			AnimationID.HUMAN_MINING_BRONZE_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_BRONZE_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_IRON_PICKAXE,
			AnimationID.HUMAN_MINING_IRON_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_IRON_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_STEEL_PICKAXE,
			AnimationID.HUMAN_MINING_STEEL_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_STEEL_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_BLACK_PICKAXE,
			AnimationID.HUMAN_MINING_BLACK_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_BLACK_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_MITHRIL_PICKAXE,
			AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_MITHRIL_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_ADAMANT_PICKAXE,
			AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_ADAMANT_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_RUNE_PICKAXE,
			AnimationID.HUMAN_MINING_RUNE_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_RUNE_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_GILDED_PICKAXE,
			AnimationID.HUMAN_MINING_GILDED_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_GILDED_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_DRAGON_PICKAXE_PRETTY_WALL,
			AnimationID.HUMAN_MINING_INFERNAL_PICKAXE,
			AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_INFERNAL_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_3A_PICKAXE,
			AnimationID.HUMAN_MINING_3A_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_3A_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE,
			AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_TRAILBLAZER_PICKAXE_NO_INFERNAL_WALL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_WALL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_TRAILBLAZER_RELOADED_PICKAXE_NO_INFERNAL_WALL,
			AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE,
			AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_NOREACHFORWARD,
			AnimationID.HUMAN_MINING_LEAGUE_TRAILBLAZER_PICKAXE_WALL,
		},
		// Ore lines, plus the bonus-ore lines (Varrock armour, Mining cape, celestial ring)
		// that hand over an ore without a "You manage to mine" line.
		Pattern.compile("^(?:You manage to mine (?:some|an?) [\\w' ]+"
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
		new int[]{
			AnimationID.HUMAN_FISHING_CASTING,
			AnimationID.HUMAN_FISHING_CASTING_BRUT,
			AnimationID.HUMAN_FISHING_CASTING_NPC,
			AnimationID.HUMAN_FISHING_CASTING_PEARL,
			AnimationID.HUMAN_FISHING_CASTING_PEARL_BRUT,
			AnimationID.HUMAN_FISHING_CASTING_PEARL_FLY,
			AnimationID.HUMAN_FISHING_CASTING_PEARL_OILY,
			AnimationID.HUMAN_FISHING_ONSPOT_BRUT,
			AnimationID.HUMAN_FISH_ONSPOT,
			AnimationID.HUMAN_FISH_ONSPOT_PEARL,
			AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT,
			AnimationID.HUMAN_FISH_ONSPOT_PEARL_FLY,
			AnimationID.HUMAN_FISH_ONSPOT_PEARL_OILY,
			AnimationID.HUMAN_HARPOON,
			AnimationID.HUMAN_HARPOON_BARBED,
			AnimationID.HUMAN_HARPOON_CRYSTAL,
			AnimationID.HUMAN_HARPOON_DRAGON,
			AnimationID.HUMAN_HARPOON_INFERNAL,
			AnimationID.HUMAN_HARPOON_TRAILBLAZER,
			AnimationID.HUMAN_HARPOON_TRAILBLAZER_NO_INFERNAL,
			AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED,
			AnimationID.HUMAN_HARPOON_TRAILBLAZER_RELOADED_NO_INFERNAL,
			AnimationID.HUMAN_HARPOON_LEAGUE_TRAILBLAZER,
			AnimationID.HUMAN_LARGENET,
			AnimationID.HUMAN_SMALLNET,
			AnimationID.BRUT_PLAYER_HAND_FISHING_END_BLANK,
		},
		// Core Fishing plugin's catch regex, the ice-gloves suffix, and the extra-fish lines
		// (Rada's blessing, angler's outfit) that hand over a fish without a "You catch" line.
		Pattern.compile("^(?:You catch (?:an?|some) [\\w' -]+?"
			+ "|Your cormorant returns with its catch"
			+ "|.+ enabled you to catch an extra fish)[.!]"
			+ "(?: It hardens as you handle it with your ice gloves\\.)?$"),
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
	private final Set<Integer> animations;
	private final Pattern itemMessage;
	private final Pattern rollWithoutItemMessage;

	Activity(String verb, String itemNoun, String offLabel, double itemChanceWithMisses, int rollTicks, int[] animationIds, Pattern itemMessage, Pattern rollWithoutItemMessage)
	{
		this.verb = verb;
		this.itemNoun = itemNoun;
		this.offLabel = offLabel;
		this.itemChanceWithMisses = itemChanceWithMisses;
		this.rollTicks = rollTicks;
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
