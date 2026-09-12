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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(TripEtaConfig.GROUP)
public interface TripEtaConfig extends Config
{
	String GROUP = "tripeta";

	@ConfigSection(
		name = "Notifications",
		description = "When and how to warn you that the inventory is about to fill",
		position = 10
	)
	String notifications = "notifications";

	@ConfigItem(
		keyName = "basketCapacity",
		name = "Extra capacity (log basket)",
		description = "How many extra logs your log basket holds on top of the inventory. Leave at 0 if you don't use one.",
		position = 1
	)
	@Range(min = 0, max = 28)
	default int basketCapacity()
	{
		return 0;
	}

	@ConfigItem(
		keyName = "showRange",
		name = "Show a range",
		description = "Show a low to high range instead of a single number. The range tightens as the trip goes on.",
		position = 2
	)
	default boolean showRange()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideWhenIdle",
		name = "Hide overlay between trips",
		description = "Only draw the overlay while a trip is in progress",
		position = 3
	)
	default boolean hideWhenIdle()
	{
		return true;
	}

	@ConfigItem(
		keyName = "leadMinutes",
		name = "Warn this long before full",
		description = "Send a warning once the remaining gathering time drops below this. 0 turns the warning off.",
		position = 11,
		section = notifications
	)
	@Range(min = 0, max = 30)
	@Units(Units.MINUTES)
	default int leadMinutes()
	{
		return 2;
	}

	@ConfigItem(
		keyName = "notifyRuneLite",
		name = "RuneLite notification",
		description = "Use RuneLite's own notification (tray, flash, sound, per your RuneLite settings)",
		position = 12,
		section = notifications
	)
	default boolean notifyRuneLite()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOnFull",
		name = "Also notify when full",
		description = "Send a second notification the moment the inventory (and basket) is full",
		position = 13,
		section = notifications
	)
	default boolean notifyOnFull()
	{
		return false;
	}

	@ConfigItem(
		keyName = "dinkNotify",
		name = "Send through Dink",
		description = "Also hand the warning to the Dink plugin, so it reaches Discord or whatever Dink is pointed at. Needs Dink installed with 'External Plugin Notifications' enabled.",
		position = 14,
		section = notifications
	)
	default boolean dinkNotify()
	{
		return false;
	}

	@ConfigItem(
		keyName = "dinkTripSummary",
		name = "Dink trip summary",
		description = "When a trip ends at the bank, send its totals (items, gathering time, time off the tree) through Dink",
		position = 15,
		section = notifications
	)
	default boolean dinkTripSummary()
	{
		return false;
	}
}
