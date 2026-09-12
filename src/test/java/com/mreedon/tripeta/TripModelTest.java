package com.mreedon.tripeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class TripModelTest
{
	private static final double EPS = 1e-9;

	private static TripModel started()
	{
		TripModel m = new TripModel();
		m.start(Activity.WOODCUTTING, 0);
		return m;
	}

	private static void gather(TripModel m, int ticks)
	{
		for (int i = 0; i < ticks; i++)
		{
			m.tick(true);
		}
	}

	@Test
	public void noEstimateBeforeTripStarts()
	{
		TripModel m = new TripModel();
		assertNull(m.estimate(10));
	}

	@Test
	public void noEstimateUntilTwoItemsWithoutPrior()
	{
		TripModel m = started();
		gather(m, 10);
		m.onItem(Activity.WOODCUTTING, 0);
		assertNull(m.estimate(10));
		m.onItem(Activity.WOODCUTTING, 0);
		assertNotNull(m.estimate(10));
	}

	@Test
	public void estimateUsesOnlyGatheringTicks()
	{
		TripModel m = started();
		gather(m, 10);          // 6.0 s of chopping
		for (int i = 0; i < 100; i++)
		{
			m.tick(false);      // 60 s off the tree must not count
		}
		m.onItem(Activity.WOODCUTTING, 0);
		m.onItem(Activity.WOODCUTTING, 0);
		// 6.0 s / 2 items = 3.0 s per item; 4 more items = 12 s
		TripModel.Estimate est = m.estimate(4);
		assertEquals(12.0, est.midSeconds, EPS);
		assertTrue(est.lowSeconds < est.midSeconds);
		assertTrue(est.highSeconds > est.midSeconds);
		assertEquals(100, m.getOffTicks());
		assertEquals(10, m.getGatherTicks());
	}

	@Test
	public void priorAloneGivesAnEstimate()
	{
		TripModel m = new TripModel();
		m.setPrior(10.0);
		m.start(Activity.WOODCUTTING, 0);
		TripModel.Estimate est = m.estimate(3);
		assertNotNull(est);
		assertEquals(30.0, est.midSeconds, EPS);
	}

	@Test
	public void priorBlendsWithObservation()
	{
		TripModel m = new TripModel();
		m.setPrior(10.0);
		m.start(Activity.WOODCUTTING, 0);
		gather(m, 10); // 6 s
		m.onItem(Activity.WOODCUTTING, 0);
		m.onItem(Activity.WOODCUTTING, 0); // observed 3 s/item
		// (3*2 + 10*PRIOR_WEIGHT) / (2+PRIOR_WEIGHT)
		double expected = (3.0 * 2 + 10.0 * TripModel.PRIOR_WEIGHT) / (2 + TripModel.PRIOR_WEIGHT);
		assertEquals(expected, m.secondsPerItem(), EPS);
	}

	@Test
	public void rangeTightensWithMoreItems()
	{
		TripModel m = started();
		gather(m, 20);
		m.onItem(Activity.WOODCUTTING, 0);
		m.onItem(Activity.WOODCUTTING, 0);
		TripModel.Estimate early = m.estimate(10);
		double earlySpread = (early.highSeconds - early.lowSeconds) / early.midSeconds;
		for (int i = 0; i < 18; i++)
		{
			gather(m, 10);
			m.onItem(Activity.WOODCUTTING, 0);
		}
		TripModel.Estimate late = m.estimate(10);
		double lateSpread = (late.highSeconds - late.lowSeconds) / late.midSeconds;
		assertTrue(lateSpread < earlySpread);
	}

	@Test
	public void finishLearnsOnlyFromLongTrips()
	{
		TripModel m = started();
		gather(m, 10);
		for (int i = 0; i < 5; i++)
		{
			m.onItem(Activity.WOODCUTTING, 0);
		}
		assertEquals(0.0, m.finish(), EPS);

		m.start(Activity.WOODCUTTING, 0);
		gather(m, 100); // 60 s
		for (int i = 0; i < 10; i++)
		{
			m.onItem(Activity.WOODCUTTING, 0);
		}
		assertEquals(6.0, m.finish(), EPS);
		assertEquals(6.0, m.getPrior(), EPS);

		// Second long trip moves the prior 30% of the way to the new observation.
		m.start(Activity.WOODCUTTING, 0);
		gather(m, 200); // 120 s / 10 = 12 s/item
		for (int i = 0; i < 10; i++)
		{
			m.onItem(Activity.WOODCUTTING, 0);
		}
		assertEquals(0.7 * 6.0 + 0.3 * 12.0, m.finish(), EPS);
	}

	@Test
	public void rangeWidensAsFewerItemsRemain()
	{
		// Rate well known (many rolls), so the process term is what is left: the wait
		// for 5 more items is far less certain, relatively, than the wait for 30.
		TripModel m = started();
		for (int i = 0; i < 40; i++)
		{
			gather(m, 30); // 18 s per roll -> q = 2.4/18
			m.onItem(Activity.WOODCUTTING, 0);
		}
		TripModel.Estimate far = m.estimate(30);
		TripModel.Estimate near = m.estimate(5);
		double farRel = (far.highSeconds - far.lowSeconds) / far.midSeconds;
		double nearRel = (near.highSeconds - near.lowSeconds) / near.midSeconds;
		assertTrue(nearRel > farRel);
		// sqrt(1-q)/sqrt(5) with q = 0.133 is ~0.42 relative sd, times z 1.28 -> s ~ 0.54,
		// plus a small rate term; the band must reflect that scale, not the old ~16%.
		assertTrue(nearRel > 0.8);
	}

	@Test
	public void rangeIsMultiplicativeAndNeverReachesZero()
	{
		TripModel m = started();
		gather(m, 10);
		m.onItem(Activity.WOODCUTTING, 0);
		m.onItem(Activity.WOODCUTTING, 0);
		TripModel.Estimate est = m.estimate(1);
		assertTrue(est.lowSeconds > 0);
		assertTrue(est.highSeconds - est.midSeconds > est.midSeconds - est.lowSeconds);
		assertEquals(est.midSeconds * est.midSeconds, est.lowSeconds * est.highSeconds, 1e-6);
	}

	@Test
	public void perRollSuccessChanceComesFromTheMeasuredRate()
	{
		TripModel m = started();
		gather(m, 40); // 24 s for 2 rolls = 12 s per roll -> q = 2.4/12 = 0.2
		m.onItem(Activity.WOODCUTTING, 0);
		m.onItem(Activity.WOODCUTTING, 0);
		assertEquals(0.2, m.perRollSuccessChance(), EPS);
		TripModel fresh = new TripModel();
		assertEquals(0.0, fresh.perRollSuccessChance(), EPS);
	}

	@Test
	public void cleanCutsCountAsRollsAndApplyTheKnownItemChance()
	{
		TripModel m = started();
		gather(m, 20); // 12 s
		for (int i = 0; i < 4; i++)
		{
			m.onItem(Activity.WOODCUTTING, 0);
		}
		for (int i = 0; i < 3; i++)
		{
			m.onRollWithoutItem();
		}
		assertEquals(7, m.rolls());
		assertEquals(0.8, m.itemChance(), EPS);
		assertEquals(12.0 / 7, m.secondsPerRoll(), EPS);
		// per item = per roll / 0.8, not the naive 12 s / 4 items = 3.0
		assertEquals(12.0 / 7 / 0.8, m.secondsPerItem(), EPS);
	}

	@Test
	public void aRunOfCleanCutsDoesNotInflateTheEstimateLikeSilenceWould()
	{
		// Both models know misses happen, chop for the same time, get the same four logs.
		TripModel withCuts = new TripModel();
		withCuts.setMissesSeenBefore(true);
		withCuts.start(Activity.WOODCUTTING, 0);
		TripModel silent = new TripModel();
		silent.setMissesSeenBefore(true);
		silent.start(Activity.WOODCUTTING, 0);
		for (TripModel m : new TripModel[]{withCuts, silent})
		{
			gather(m, 20);
			for (int i = 0; i < 4; i++)
			{
				m.onItem(Activity.WOODCUTTING, 0);
			}
			gather(m, 9);
		}
		// Only one of them saw three clean cuts in those last nine ticks.
		for (int i = 0; i < 3; i++)
		{
			withCuts.onRollWithoutItem();
		}
		// The clean cuts are evidence the tree is giving successes; silence is not.
		assertTrue(withCuts.secondsPerItem() < silent.secondsPerItem());
		assertEquals((29 * 0.6) / 7 / 0.8, withCuts.secondsPerItem(), EPS);
		assertEquals((29 * 0.6) / 4 / 0.8, silent.secondsPerItem(), EPS);
	}

	@Test
	public void itemChanceIsOneUntilAMissIsSeen()
	{
		TripModel m = started();
		assertEquals(1.0, m.itemChance(), EPS);
		m.onRollWithoutItem();
		assertEquals(0.8, m.itemChance(), EPS);
	}

	@Test
	public void finishRemembersWhetherMissesHappened()
	{
		TripModel m = started();
		gather(m, 100);
		for (int i = 0; i < 9; i++)
		{
			m.onItem(Activity.WOODCUTTING, 0);
		}
		m.onRollWithoutItem(); // 10 rolls, enough to learn
		m.finish();
		assertTrue(m.isMissesSeenBefore());
		m.start(Activity.WOODCUTTING, 0);
		assertEquals(0.8, m.itemChance(), EPS);
	}

	@Test
	public void itemArrivalStartsTripWhenAnimationMissed()
	{
		TripModel m = new TripModel();
		m.onItem(Activity.WOODCUTTING, 42);
		assertTrue(m.isActive());
		assertEquals(1, m.getItems());
		assertEquals(42, m.getStartedAtMs());
	}

	@Test
	public void fullStopsTheClock()
	{
		TripModel m = started();
		gather(m, 5);
		m.markFull();
		gather(m, 5);
		m.tick(false);
		assertEquals(5, m.getGatherTicks());
		assertEquals(0, m.getOffTicks());
	}

	@Test
	public void formatSeconds()
	{
		assertEquals("0:00", TripModel.formatSeconds(0));
		assertEquals("1:05", TripModel.formatSeconds(65));
		assertEquals("12:00", TripModel.formatSeconds(719.6));
	}
}
