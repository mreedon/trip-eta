package com.mreedon.tripeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class BasketTrackerTest
{
	private static BasketTracker present()
	{
		BasketTracker b = new BasketTracker();
		b.setPresent(true, true);
		return b;
	}

	@Test
	public void recognisesAllFourBasketItems()
	{
		assertTrue(BasketTracker.isBasket(ItemID.LOG_BASKET_CLOSED));
		assertTrue(BasketTracker.isBasket(ItemID.LOG_BASKET_OPEN));
		assertTrue(BasketTracker.isBasket(ItemID.FORESTRY_BASKET_CLOSED));
		assertTrue(BasketTracker.isBasket(ItemID.FORESTRY_BASKET_OPEN));
		assertFalse(BasketTracker.isBasket(ItemID.FORESTRY_KIT));
		assertTrue(BasketTracker.isOpenBasket(ItemID.LOG_BASKET_OPEN));
		assertFalse(BasketTracker.isOpenBasket(ItemID.LOG_BASKET_CLOSED));
	}

	@Test
	public void noBasketMeansNoCapacity()
	{
		BasketTracker b = new BasketTracker();
		assertEquals(0, b.remaining());
		b.onItemsWithoutInventoryGain(3);
		assertEquals(0, b.getUsed());
	}

	@Test
	public void arrivalsWithoutInventoryGainFillIt()
	{
		BasketTracker b = present();
		b.onItemsWithoutInventoryGain(5);
		assertEquals(5, b.getUsed());
		assertEquals(23, b.remaining());
		b.onItemsWithoutInventoryGain(40);
		assertEquals(28, b.getUsed());
		assertEquals(0, b.remaining());
	}

	@Test
	public void manualFillAndPartialEmpty()
	{
		BasketTracker b = present();
		b.onManualFill(20);
		assertEquals(20, b.getUsed());
		b.onInventoryGainWithoutItems(7);
		assertEquals(13, b.getUsed());
		b.onInventoryGainWithoutItems(50);
		assertEquals(0, b.getUsed());
	}

	@Test
	public void gameMessagesSetTheCount()
	{
		BasketTracker b = present();
		assertEquals(BasketTracker.Outcome.UPDATED, b.onMessage("The basket is full."));
		assertEquals(28, b.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, b.onMessage("Your basket is empty."));
		assertEquals(0, b.getUsed());
		b.onManualFill(10);
		assertEquals(BasketTracker.Outcome.UPDATED, b.onMessage("You empty your basket."));
		assertEquals(0, b.getUsed());
		b.onManualFill(10);
		assertEquals(BasketTracker.Outcome.EMPTIED_TO_BANK, b.onMessage("You empty your basket into the bank."));
		assertEquals(0, b.getUsed());
		b.onManualFill(10);
		assertEquals(BasketTracker.Outcome.EMPTIED_TO_BANK, b.onMessage("You empty all of your containers into the bank."));
		assertEquals(0, b.getUsed());
		assertEquals(BasketTracker.Outcome.NONE, b.onMessage("You get some redwood logs."));
	}

	@Test
	public void checkSummaryIsSummed()
	{
		BasketTracker b = present();
		assertEquals(BasketTracker.Outcome.UPDATED, b.onMessage("The basket contains: 12 x Redwood logs, 3 x Yew logs"));
		assertEquals(15, b.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, b.onMessage("5 × Magic logs"));
		assertEquals(5, b.getUsed());
	}

	@Test
	public void itemReachingInventoryWhileOpenMeansFull()
	{
		BasketTracker b = present(); // open
		assertEquals(0, b.getUsed());
		b.onItemsIntoInventoryWhileOpen(1);
		assertEquals(28, b.getUsed());
		assertEquals(0, b.remaining());
	}

	@Test
	public void itemReachingInventoryWhileClosedSaysNothing()
	{
		BasketTracker b = new BasketTracker();
		b.setPresent(true, false);
		b.onManualFill(10);
		b.onItemsIntoInventoryWhileOpen(1);
		assertEquals(10, b.getUsed());
	}

	@Test
	public void resetClearsEverything()
	{
		BasketTracker b = present();
		b.onManualFill(9);
		b.reset();
		assertFalse(b.isPresent());
		assertEquals(0, b.getUsed());
		assertEquals(0, b.remaining());
	}
}
