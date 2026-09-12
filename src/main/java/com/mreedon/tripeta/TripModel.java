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
 * Two random things happen while gathering, and the model keeps them apart:
 *
 * 1. How often a chop succeeds. This depends on level, axe and tree, and it is what
 *    the trip measures: seconds of gathering per successful roll.
 * 2. Whether a successful chop hands over an item. With a felling axe and rations one
 *    in five successes is a "clean cut" with no log. That is a fixed coin with no
 *    memory, so a run of clean cuts says nothing about the next chop. Clean cuts
 *    therefore count as successful rolls for the rate, and the item chance is applied
 *    as a known constant instead of being re-estimated from a handful of flips.
 *
 * Pure Java, no RuneLite types, so it can be unit tested.
 */
class TripModel
{
	static final double TICK_SECONDS = 0.6;
	/**
	 * How many observed rolls the carried-over rate from earlier trips is worth. A full
	 * trip is about 70 rolls, and a dozen early rolls can easily run 30% fast or slow, so
	 * the prior has to outweigh the first few minutes to keep the estimate from chasing luck.
	 */
	static final int PRIOR_WEIGHT = 20;
	/** With no prior, wait for this many successful rolls before showing an estimate. */
	static final int MIN_ROLLS_FOR_ESTIMATE = 2;
	/** Only trips with at least this many successful rolls teach the prior. */
	static final int MIN_ROLLS_TO_LEARN = 10;
	/** Cap on the band's stretch factor: high = mid * (1 + s), low = mid / (1 + s). */
	static final double MAX_RELATIVE_SPREAD = 0.8;
	/** One-sided 90% z, used for the range. */
	private static final double Z = 1.28;

	static final class Estimate
	{
		final double lowSeconds;
		final double midSeconds;
		final double highSeconds;
		final int sampleRolls;

		Estimate(double lowSeconds, double midSeconds, double highSeconds, int sampleRolls)
		{
			this.lowSeconds = lowSeconds;
			this.midSeconds = midSeconds;
			this.highSeconds = highSeconds;
			this.sampleRolls = sampleRolls;
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
	/** Seconds of gathering per successful roll, carried over from earlier trips; 0 = none yet. */
	private double prior;
	/** Whether an earlier trip this session saw the no-item outcome, so the coin applies from the first chop. */
	private boolean missesSeenBefore;
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

	/** Successful rolls this trip: items plus clean cuts. */
	int rolls()
	{
		return items + rollsWithoutItem;
	}

	/**
	 * Chance a successful roll yields an item. 1 until the no-item outcome has been seen,
	 * then the activity's known constant. Not estimated from the trip: a few flips of an
	 * 80/20 coin would swing the ETA around for no reason.
	 */
	double itemChance()
	{
		if (activity == null)
		{
			return 1.0;
		}
		if (rollsWithoutItem > 0 || missesSeenBefore)
		{
			return activity.itemChanceWithMisses;
		}
		return 1.0;
	}

	/**
	 * Seconds of gathering per successful roll: this trip's observation blended with the
	 * prior, weighting the prior like {@link #PRIOR_WEIGHT} rolls. NaN when nothing is known.
	 */
	double secondsPerRoll()
	{
		int n = rolls();
		double observed = n > 0 ? gatherTicks * TICK_SECONDS / n : Double.NaN;
		if (prior > 0 && n > 0)
		{
			return (observed * n + prior * PRIOR_WEIGHT) / (n + PRIOR_WEIGHT);
		}
		if (prior > 0)
		{
			return prior;
		}
		return observed;
	}

	/** Seconds of gathering per item: per roll, divided by the chance a roll yields an item. */
	double secondsPerItem()
	{
		return secondsPerRoll() / itemChance();
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
		int n = rolls();
		if (prior <= 0 && n < MIN_ROLLS_FOR_ESTIMATE)
		{
			return null;
		}
		double spi = secondsPerItem();
		if (Double.isNaN(spi) || spi <= 0)
		{
			return null;
		}
		double mid = remaining * spi;

		// Two sources of spread, combined in quadrature.
		//
		// Estimation: how well the rate is known. The sample mean of n geometric waits
		// has relative sd sqrt(1-q)/sqrt(n), where q is the per-roll success chance.
		//
		// Process: even with the rate known exactly, the wait for the remaining items is
		// random. Each item is a geometric wait with per-roll chance q*p (a success that
		// also yields an item), so the sum over `remaining` items has relative sd
		// sqrt(1-q*p)/sqrt(remaining). On slow trees q is small and this term dominates
		// late in the trip: about 20% with 26 left, about 45% with 5 left.
		//
		// q comes from the measured rate: the game rolls every rollTicks, so
		// q = rollSeconds / secondsPerRoll. Unknown cadence (0) is read as q -> 0, the
		// conservative end.
		double p = itemChance();
		double q = perRollSuccessChance();
		int effectiveSample = n + (prior > 0 ? PRIOR_WEIGHT : 0);
		double rateSpread = Z * Math.sqrt(1 - q) / Math.sqrt(Math.max(1, effectiveSample));
		double processSpread = Z * Math.sqrt(1 - q * p) / Math.sqrt(Math.max(1, remaining));
		double s = Math.min(MAX_RELATIVE_SPREAD, Math.sqrt(rateSpread * rateSpread + processSpread * processSpread));
		// The wait is right-skewed (negative binomial), so the band is multiplicative:
		// the high side stretches further than the low side shrinks, and the low side
		// can never reach zero.
		return new Estimate(mid / (1 + s), mid, mid * (1 + s), n);
	}

	/** Chance a single game roll succeeds, from the measured rate; 0 if the cadence is unknown. */
	double perRollSuccessChance()
	{
		if (activity == null || activity.rollTicks <= 0)
		{
			return 0;
		}
		double spr = secondsPerRoll();
		if (Double.isNaN(spr) || spr <= 0)
		{
			return 0;
		}
		return Math.min(1.0, activity.rollTicks * TICK_SECONDS / spr);
	}

	/**
	 * End the trip. Folds this trip's observed roll rate into the prior when the trip was
	 * long enough to trust, then resets. Returns the prior after the update.
	 */
	double finish()
	{
		if (active && rolls() >= MIN_ROLLS_TO_LEARN && gatherTicks > 0)
		{
			double observed = gatherTicks * TICK_SECONDS / rolls();
			prior = prior > 0 ? 0.7 * prior + 0.3 * observed : observed;
			missesSeenBefore = rollsWithoutItem > 0;
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

	boolean isMissesSeenBefore()
	{
		return missesSeenBefore;
	}

	void setMissesSeenBefore(boolean missesSeenBefore)
	{
		this.missesSeenBefore = missesSeenBefore;
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
