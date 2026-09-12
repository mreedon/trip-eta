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
		// (3*2 + 10*6) / (2+6) = 66/8 = 8.25
		assertEquals(8.25, m.secondsPerItem(), EPS);
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
