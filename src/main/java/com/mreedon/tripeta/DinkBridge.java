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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.PluginMessage;

/**
 * Hands notifications to the Dink plugin over RuneLite's plugin-message bus.
 *
 * Nothing here touches the network. Dink owns the webhook; this only posts an event
 * that Dink chooses to act on if the user has enabled External Plugin Notifications.
 * See https://github.com/pajlads/DinkPlugin/blob/master/docs/external-plugin-messaging.md
 *
 * The {@code metadata} map is Dink's escape hatch for non-Discord consumers: it is
 * serialised into the webhook body untouched, so a custom handler gets the raw trip
 * numbers and not just the sentence.
 */
@Slf4j
@Singleton
class DinkBridge
{
	private static final String NAMESPACE = "dink";
	private static final String NAME = "notify";
	private static final String SOURCE = "Trip ETA";

	private final EventBus eventBus;

	@Inject
	DinkBridge(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	void notifyLead(TripModel model, TripModel.Estimate est, int remaining, double offSeconds)
	{
		Activity a = model.getActivity();
		List<Map<String, Object>> fields = new ArrayList<>();
		fields.add(field("Left", remaining + " " + a.itemNoun));
		fields.add(field(a.verb + " left", TripModel.formatSeconds(est.lowSeconds) + " to " + TripModel.formatSeconds(est.highSeconds)));
		fields.add(field("Rate", rateText(model)));
		if (offSeconds > 0)
		{
			fields.add(field(a.offLabel, TripModel.formatSeconds(offSeconds)));
		}

		Map<String, Object> data = base(
			"Trip ETA",
			"%USERNAME%'s inventory fills in about " + TripModel.formatSeconds(est.midSeconds) + " of " + a.verb.toLowerCase(),
			fields
		);
		data.put("metadata", metadata("lead", model, est, remaining, offSeconds));
		post(data);
	}

	void notifyFull(TripModel model)
	{
		Activity a = model.getActivity();
		Map<String, Object> data = base(
			"Trip ETA",
			"%USERNAME%'s inventory is full: " + model.getItems() + " " + a.itemNoun + " this trip",
			List.of(
				field(a.verb, TripModel.formatSeconds(model.getGatherTicks() * TripModel.TICK_SECONDS)),
				field(a.offLabel, TripModel.formatSeconds(model.getOffTicks() * TripModel.TICK_SECONDS))
			)
		);
		data.put("metadata", metadata("full", model, null, 0, 0));
		post(data);
	}

	void notifyTripSummary(TripModel model)
	{
		Activity a = model.getActivity();
		double gather = model.getGatherTicks() * TripModel.TICK_SECONDS;
		double off = model.getOffTicks() * TripModel.TICK_SECONDS;
		List<Map<String, Object>> fields = new ArrayList<>();
		fields.add(field(capitalize(a.itemNoun), String.valueOf(model.getItems())));
		fields.add(field(a.verb, TripModel.formatSeconds(gather)));
		fields.add(field(a.offLabel, TripModel.formatSeconds(off)));
		if (model.getItems() > 0)
		{
			fields.add(field("Rate", String.format("%.1f s per item", gather / model.getItems())));
		}
		if (model.getRollsWithoutItem() > 0)
		{
			fields.add(field("Clean cuts", String.valueOf(model.getRollsWithoutItem())));
		}

		Map<String, Object> data = base(
			"Trip done",
			"%USERNAME% banked " + model.getItems() + " " + a.itemNoun + " after " + TripModel.formatSeconds(gather + off),
			fields
		);
		data.put("metadata", metadata("trip", model, null, 0, off));
		post(data);
	}

	private void post(Map<String, Object> data)
	{
		log.debug("dink request: {}", data.get("text"));
		eventBus.post(new PluginMessage(NAMESPACE, NAME, data));
	}

	private static Map<String, Object> base(String title, String text, List<Map<String, Object>> fields)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("sourcePlugin", SOURCE);
		data.put("title", title);
		data.put("text", text);
		data.put("fields", fields);
		data.put("imageRequested", false);
		return data;
	}

	private static String rateText(TripModel model)
	{
		if (model.itemChance() < 1)
		{
			return String.format("%.1f s per item (%.1f s per chop, %d clean cuts)",
				model.secondsPerItem(), model.secondsPerRoll(), model.getRollsWithoutItem());
		}
		return String.format("%.1f s per item", model.secondsPerItem());
	}

	private static Map<String, Object> metadata(String event, TripModel model, TripModel.Estimate est, int remaining, double offSeconds)
	{
		Map<String, Object> m = new HashMap<>();
		m.put("event", event);
		m.put("activity", model.getActivity().name());
		m.put("items", model.getItems());
		m.put("remaining", remaining);
		m.put("gatherTicks", model.getGatherTicks());
		m.put("offTicks", model.getOffTicks());
		m.put("offStreakSeconds", offSeconds);
		m.put("rolls", model.rolls());
		m.put("rollsWithoutItem", model.getRollsWithoutItem());
		m.put("itemChance", model.itemChance());
		m.put("perRollSuccessChance", model.perRollSuccessChance());
		m.put("secondsPerRoll", model.secondsPerRoll());
		m.put("secondsPerItem", model.secondsPerItem());
		m.put("prior", model.getPrior());
		m.put("tripStartedAt", model.getStartedAtMs());
		if (est != null)
		{
			m.put("etaLow", est.lowSeconds);
			m.put("etaMid", est.midSeconds);
			m.put("etaHigh", est.highSeconds);
		}
		return m;
	}

	private static Map<String, Object> field(String name, String value)
	{
		Map<String, Object> f = new HashMap<>();
		f.put("name", name);
		f.put("value", value);
		f.put("inline", true);
		return f;
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
