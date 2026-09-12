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

/**
 * The state of one bank-to-bank trip and the estimate built from it.
 *
 * The only clock that feeds the estimate is ticks spent in the gathering animation.
 * Time off the tree (walking, waiting for a respawn, being away) is counted separately
 * and never shortens or lengthens the estimate. That is the whole point: the number
 * answers "how much more chopping is left", not "how long until you happen to be done".
 *
 * Pure Java, no RuneLite types, so it can be unit tested.
 */
class TripModel
{
	static final double TICK_SECONDS = 0.6;
	/** How many observed items the carried-over rate from earlier trips is worth. */
	static final int PRIOR_WEIGHT = 6;
	/** With no prior, wait for this many items before showing an estimate. */
	static final int MIN_ITEMS_FOR_ESTIMATE = 2;
	/** Only trips at least this long teach the prior. */
	static final int MIN_ITEMS_TO_LEARN = 10;
	/** Cap on how wide the range can get, as a fraction of the midpoint. */
	static final double MAX_RELATIVE_SPREAD = 0.6;

	static final class Estimate
	{
		final double lowSeconds;
		final double midSeconds;
		final double highSeconds;
		final int sampleItems;

		Estimate(double lowSeconds, double midSeconds, double highSeconds, int sampleItems)
		{
			this.lowSeconds = lowSeconds;
			this.midSeconds = midSeconds;
			this.highSeconds = highSeconds;
			this.sampleItems = sampleItems;
		}
	}

	private Activity activity;
	private boolean active;
	private boolean full;
	private long startedAtMs;
	private int gatherTicks;
	private int offTicks;
	private int offStreakTicks;
	private int items;
	private int rollsWithoutItem;
	/** Seconds per item carried over from earlier trips; 0 means none yet. */
	private double prior;
	private boolean leadNotified;
	private boolean fullNotified;

	void start(Activity activity, long nowMs)
	{
		if (active)
		{
			return;
		}
		this.activity = activity;
		active = true;
		full = false;
		startedAtMs = nowMs;
		gatherTicks = 0;
		offTicks = 0;
		offStreakTicks = 0;
		items = 0;
		rollsWithoutItem = 0;
		leadNotified = false;
		fullNotified = false;
	}

	/** One game tick passed; {@code gathering} is whether the gathering animation was playing. */
	void tick(boolean gathering)
	{
		if (!active || full)
		{
			return;
		}
		if (gathering)
		{
			gatherTicks++;
			offStreakTicks = 0;
		}
		else
		{
			offTicks++;
			offStreakTicks++;
		}
	}

	/** An item arrived. Starts a trip if the animation never announced one (plugin enabled mid-trip). */
	void onItem(Activity activity, long nowMs)
	{
		if (!active)
		{
			start(activity, nowMs);
		}
		items++;
	}

	/** A successful roll that produced no item (felling axe with rations). */
	void onRollWithoutItem()
	{
		if (active)
		{
			rollsWithoutItem++;
		}
	}

	void markFull()
	{
		full = true;
	}

	/**
	 * Seconds of gathering per item: this trip's observation blended with the prior,
	 * weighting the prior like {@link #PRIOR_WEIGHT} items. NaN when nothing is known.
	 */
	double secondsPerItem()
	{
		double observed = items > 0 ? gatherTicks * TICK_SECONDS / items : Double.NaN;
		if (prior > 0 && items > 0)
		{
			return (observed * items + prior * PRIOR_WEIGHT) / (items + PRIOR_WEIGHT);
		}
		if (prior > 0)
		{
			return prior;
		}
		return observed;
	}

	/**
	 * Gathering time left for {@code remaining} more items, as a range.
	 * Null until there is something to go on.
	 */
	Estimate estimate(int remaining)
	{
		if (!active)
		{
			return null;
		}
		if (prior <= 0 && items < MIN_ITEMS_FOR_ESTIMATE)
		{
			return null;
		}
		double spi = secondsPerItem();
		if (Double.isNaN(spi) || spi <= 0)
		{
			return null;
		}
		double mid = remaining * spi;
		int effectiveSample = items + (prior > 0 ? PRIOR_WEIGHT : 0);
		// Arrivals are close to geometric, so the relative error of the mean interval
		// shrinks like 1/sqrt(n). 1.28 is the one-sided 90% z.
		double spread = Math.min(MAX_RELATIVE_SPREAD, 1.28 / Math.sqrt(Math.max(1, effectiveSample)));
		return new Estimate(mid * (1 - spread), mid, mid * (1 + spread), items);
	}

	/**
	 * End the trip. Folds this trip's observed rate into the prior when the trip was
	 * long enough to trust, then resets. Returns the prior after the update.
	 */
	double finish()
	{
		if (active && items >= MIN_ITEMS_TO_LEARN && gatherTicks > 0)
		{
			double observed = gatherTicks * TICK_SECONDS / items;
			prior = prior > 0 ? 0.7 * prior + 0.3 * observed : observed;
		}
		reset();
		return prior;
	}

	/** Drop the current trip without learning from it. The prior survives. */
	void reset()
	{
		active = false;
		full = false;
		gatherTicks = 0;
		offTicks = 0;
		offStreakTicks = 0;
		items = 0;
		rollsWithoutItem = 0;
		leadNotified = false;
		fullNotified = false;
	}

	double getPrior()
	{
		return prior;
	}

	void setPrior(double prior)
	{
		this.prior = prior > 0 ? prior : 0;
	}

	Activity getActivity()
	{
		return activity;
	}

	boolean isActive()
	{
		return active;
	}

	boolean isFull()
	{
		return full;
	}

	long getStartedAtMs()
	{
		return startedAtMs;
	}

	int getGatherTicks()
	{
		return gatherTicks;
	}

	int getOffTicks()
	{
		return offTicks;
	}

	int getOffStreakTicks()
	{
		return offStreakTicks;
	}

	int getItems()
	{
		return items;
	}

	int getRollsWithoutItem()
	{
		return rollsWithoutItem;
	}

	boolean isLeadNotified()
	{
		return leadNotified;
	}

	void setLeadNotified(boolean leadNotified)
	{
		this.leadNotified = leadNotified;
	}

	boolean isFullNotified()
	{
		return fullNotified;
	}

	void setFullNotified(boolean fullNotified)
	{
		this.fullNotified = fullNotified;
	}

	static String formatSeconds(double seconds)
	{
		long s = Math.max(0, Math.round(seconds));
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
