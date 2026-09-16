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
 * One allowance, and it touches the rate only: a pause no longer than the activity's
 * grace joins the gathering clock once gathering resumes. A rock gives one ore and you
 * hop to the next; a fishing spot moves. Those gaps are a fixed share of every item, so
 * an estimate that ignored them would run short of the wall clock. Woodcutting's grace
 * is zero: a tree does not stop you per log. The off clock counts every tick you are not
 * gathering regardless, so what the panel shows and what the summary reports stay honest.
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
	/**
	 * What the skill's own rate is worth when the item type has none yet. A redwood and a
	 * willow are both woodcutting and nothing alike, so the skill figure is a starting
	 * point rather than an anchor: enough that a first trip at a new tree is not wild, light
	 * enough that a few real rolls outweigh it.
	 */
	static final int SKILL_PRIOR_WEIGHT = 5;
	/** With no prior, wait for this many successful rolls before showing an estimate. */
	static final int MIN_ROLLS_FOR_ESTIMATE = 2;
	/**
	 * A run this long with no no-item outcome is taken as evidence there is none to see,
	 * whatever an earlier trip showed. Twenty clean rolls at the felling axe's one-in-five
	 * would happen about once in eighty trips, so it means the rations ran out.
	 */
	static final int MISSES_DOUBT_ROLLS = 20;
	/** Only trips with at least this many successful rolls teach the prior. */
	static final int MIN_ROLLS_TO_LEARN = 10;
	/** Cap on the band's stretch factor: high = mid * (1 + s), low = mid / (1 + s). */
	static final double MAX_RELATIVE_SPREAD = 0.8;
	/**
	 * Width of the range, as a multiple of the modelled standard deviation. The textbook
	 * 1.28 for an 80% interval turned out to be about 15% too narrow in practice: replaying
	 * 180 banked woodcutting trips, the band it produced held 72-76% of real outcomes rather
	 * than 80%, and by much the same margin whether 4 logs remained or 20. The waits are not
	 * quite the independent geometric draws the model treats them as, so the constant carries
	 * the correction rather than pretending the shape is exact.
	 */
	private static final double Z = 1.47;

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
	/**
	 * Items gathered since the current fill began, which is the trip's total until
	 * something empties the inventory mid-trip and it starts filling again.
	 */
	private int fillItems;
	private int rollsWithoutItem;
	/** Seconds of gathering per successful roll for the skill, carried over from earlier trips; 0 = none yet. */
	private double prior;
	/** The same, but for this exact item type, which is the one worth trusting; 0 = none yet. */
	private double typePrior;
	/** What this trip has been producing ("redwood_logs"), or null before the first named item. */
	private String currentType;
	/** Set once a second item type is named: a mixed load cannot teach either type its rate. */
	private boolean mixedTypes;
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
		fillItems = 0;
		rollsWithoutItem = 0;
		typePrior = 0;
		currentType = null;
		mixedTypes = false;
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
			if (offStreakTicks > 0 && offStreakTicks <= pauseGraceTicks())
			{
				// A pause short enough to be part of the work (a hop to the next rock, a
				// fishing spot moving) joins the rate clock now that it is over, so the
				// estimate covers the whole cycle. A longer one was time away, all of it:
				// no partial credit. The off clock keeps it either way, so "not swinging"
				// stays an honest measure of attention.
				gatherTicks += offStreakTicks;
			}
			gatherTicks++;
			offStreakTicks = 0;
		}
		else
		{
			offTicks++;
			offStreakTicks++;
		}
	}

	/** Longest pause the activity treats as part of the work rather than time away. */
	private int pauseGraceTicks()
	{
		return activity == null ? 0 : activity.pauseGraceTicks;
	}

	/**
	 * A pause this short is the gathering animation restarting, not the player stopping.
	 * The game drops the animation for a tick when an item lands and picks it straight back
	 * up, which is invisible in play but made the off-time line blink on every ore.
	 */
	static final int OFF_SETTLE_TICKS = 2;

	/**
	 * The current pause as the panel should show it: the whole streak once it outlasts the
	 * animation blip, 0 before that. Display only. The off clock counts every tick either
	 * way, and this is far shorter than the rate's pause grace, so a hop between rocks still
	 * shows up here even though the rate quietly treats it as part of the work.
	 */
	int visibleOffStreakTicks()
	{
		return offStreakTicks > OFF_SETTLE_TICKS ? offStreakTicks : 0;
	}

	/** An item arrived. Starts a trip if the animation never announced one (plugin enabled mid-trip). */
	void onItem(Activity activity, long nowMs)
	{
		if (!active)
		{
			start(activity, nowMs);
		}
		items++;
		fillItems++;
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
	 * Room opened up again without the trip ending at a bank: the Motherlode Mine hopper is
	 * the everyday case, where a full inventory of pay-dirt is deposited and the same trip
	 * carries on. The clocks restart and both notifications re-arm for the next fill.
	 */
	void resume()
	{
		full = false;
		fillItems = 0;
		leadNotified = false;
		fullNotified = false;
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
		if (rollsWithoutItem > 0)
		{
			return activity.itemChanceWithMisses;
		}
		if (missesSeenBefore && rolls() < MISSES_DOUBT_ROLLS)
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
		double carried = carriedRate();
		int weight = carriedWeight();
		if (carried > 0 && n > 0)
		{
			return (observed * n + carried * weight) / (n + weight);
		}
		if (carried > 0)
		{
			return carried;
		}
		return observed;
	}

	/**
	 * The rate carried into this trip: this item type's own if it has one, otherwise the
	 * skill's, otherwise nothing.
	 */
	private double carriedRate()
	{
		return typePrior > 0 ? typePrior : prior;
	}

	/** How many rolls the carried rate is worth against what this trip is measuring. */
	private int carriedWeight()
	{
		return typePrior > 0 ? PRIOR_WEIGHT : SKILL_PRIOR_WEIGHT;
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
		if (carriedRate() <= 0 && n < MIN_ROLLS_FOR_ESTIMATE)
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
		int effectiveSample = n + (carriedRate() > 0 ? carriedWeight() : 0);
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

	/** An item arrived and the line named what it was. */
	void noteItemType(String type)
	{
		if (type == null)
		{
			return;
		}
		if (currentType != null && !currentType.equals(type))
		{
			mixedTypes = true;
		}
		currentType = type;
	}

	String getCurrentType()
	{
		return currentType;
	}

	boolean isMixedTypes()
	{
		return mixedTypes;
	}

	double getTypePrior()
	{
		return typePrior;
	}

	void setTypePrior(double typePrior)
	{
		this.typePrior = typePrior > 0 ? typePrior : 0;
	}

	/**
	 * Fold this trip into the rate for the item type it produced, before {@link #finish}
	 * clears it. Returns the type's rate after the update, or 0 when the trip taught it
	 * nothing: too short, or a mixed load where the gathering clock cannot be divided up.
	 */
	double finishType()
	{
		if (!active || mixedTypes || currentType == null || rolls() < MIN_ROLLS_TO_LEARN || gatherTicks <= 0)
		{
			return 0;
		}
		double observed = gatherTicks * TICK_SECONDS / rolls();
		typePrior = typePrior > 0 ? 0.7 * typePrior + 0.3 * observed : observed;
		return typePrior;
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
		fillItems = 0;
		rollsWithoutItem = 0;
		typePrior = 0;
		currentType = null;
		mixedTypes = false;
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

	/** Items gathered toward the fill in progress; the panel counts these, not slots. */
	int getFillItems()
	{
		return fillItems;
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
