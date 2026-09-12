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
 * Adding mining or fishing later is a new constant here, nothing else.
 */
enum Activity
{
	WOODCUTTING(
		"Chopping",
		"logs",
		"Off tree",
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
		// Same shape as the core Woodcutting plugin's pattern.
		Pattern.compile("^You get (?:some|an)[\\w' ]+(?:logs?|mushrooms)\\.$"),
		// Felling axe with forester's rations: a successful chop that yields no log.
		Pattern.compile("^You strike a clean cut without gathering any material\\.$")
	);

	/** Verb shown on the overlay, e.g. "Chopping". */
	final String verb;
	/** Plural noun for the gathered item, e.g. "logs". */
	final String itemNoun;
	/** Label for time spent not gathering, e.g. "Off tree". */
	final String offLabel;
	private final Set<Integer> animations;
	private final Pattern itemMessage;
	private final Pattern rollWithoutItemMessage;

	Activity(String verb, String itemNoun, String offLabel, int[] animationIds, Pattern itemMessage, Pattern rollWithoutItemMessage)
	{
		this.verb = verb;
		this.itemNoun = itemNoun;
		this.offLabel = offLabel;
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
