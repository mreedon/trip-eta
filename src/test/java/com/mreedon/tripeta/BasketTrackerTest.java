package com.mreedon.tripeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class BasketTrackerTest
{
	private static BasketTracker present()
	{
		BasketTracker b = new BasketTracker();
		b.setPresent(BasketTracker.Kind.LOG_BASKET, true, false);
		return b;
	}

	private static BasketTracker barrel()
	{
		BasketTracker b = new BasketTracker();
		b.setPresent(BasketTracker.Kind.FISH_BARREL, true, false);
		return b;
	}

	@Test
	public void recognisesEveryContainerItem()
	{
		assertEquals(BasketTracker.Kind.LOG_BASKET, BasketTracker.kindOf(ItemID.LOG_BASKET_CLOSED));
		assertEquals(BasketTracker.Kind.LOG_BASKET, BasketTracker.kindOf(ItemID.LOG_BASKET_OPEN));
		assertEquals(BasketTracker.Kind.LOG_BASKET, BasketTracker.kindOf(ItemID.FORESTRY_BASKET_CLOSED));
		assertEquals(BasketTracker.Kind.LOG_BASKET, BasketTracker.kindOf(ItemID.FORESTRY_BASKET_OPEN));
		assertEquals(BasketTracker.Kind.FISH_BARREL, BasketTracker.kindOf(ItemID.FISH_BARREL_CLOSED));
		assertEquals(BasketTracker.Kind.FISH_BARREL, BasketTracker.kindOf(ItemID.FISH_BARREL_OPEN));
		assertEquals(BasketTracker.Kind.FISH_BARREL, BasketTracker.kindOf(ItemID.FISH_SACK_BARREL_CLOSED));
		assertEquals(BasketTracker.Kind.FISH_BARREL, BasketTracker.kindOf(ItemID.FISH_SACK_BARREL_OPEN));
		assertNull(BasketTracker.kindOf(ItemID.FORESTRY_KIT));
		assertNull(BasketTracker.kindOf(ItemID.FISH_SACK));
		assertTrue(BasketTracker.isOpen(ItemID.LOG_BASKET_OPEN));
		assertFalse(BasketTracker.isOpen(ItemID.LOG_BASKET_CLOSED));
		assertTrue(BasketTracker.isOpen(ItemID.FISH_BARREL_OPEN));
		assertFalse(BasketTracker.isOpen(ItemID.FISH_SACK_BARREL_CLOSED));
	}

	@Test
	public void eachContainerTakesOnlyItsOwnActivity()
	{
		BasketTracker b = present();
		assertTrue(b.takes(Activity.WOODCUTTING));
		assertFalse(b.takes(Activity.MINING));
		assertFalse(b.takes(Activity.FISHING));
		assertEquals("basket", b.noun());
		BasketTracker f = barrel();
		assertTrue(f.takes(Activity.FISHING));
		assertFalse(f.takes(Activity.WOODCUTTING));
		assertEquals("barrel", f.noun());
		assertFalse(new BasketTracker().takes(Activity.WOODCUTTING));
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
	public void barrelMessagesSetTheCountToo()
	{
		BasketTracker f = barrel();
		assertEquals(BasketTracker.Outcome.UPDATED, f.onMessage("The barrel is full. It may be emptied at a bank."));
		assertEquals(28, f.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, f.onMessage("The barrel is full."));
		assertEquals(BasketTracker.Outcome.UPDATED, f.onMessage("Your barrel is empty."));
		assertEquals(0, f.getUsed());
		f.onManualFill(6);
		assertEquals(BasketTracker.Outcome.EMPTIED_TO_BANK, f.onMessage("You empty your barrel into the bank."));
		assertEquals(0, f.getUsed());
		assertEquals(BasketTracker.Outcome.NONE, f.onMessage("You catch a shark."));
	}

	@Test
	public void containerMentionsAreSpottedForLogging()
	{
		assertTrue(BasketTracker.mentionsContainer("You fill the barrel with fish."));
		assertTrue(BasketTracker.mentionsContainer("Your Basket is now closed."));
		assertFalse(BasketTracker.mentionsContainer("You catch a barrelfish."));
		assertFalse(BasketTracker.mentionsContainer("You get some logs."));
	}

	@Test
	public void checkBoxTextIsSummed()
	{
		BasketTracker b = present();
		assertEquals(BasketTracker.Outcome.UPDATED, b.onCheckText("The basket contains:<br>12 x Redwood logs<br>3 x Yew logs"));
		assertEquals(15, b.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, b.onCheckText("<col=ff0000>The basket contains:</col><br>5 × Magic logs"));
		assertEquals(5, b.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, b.onCheckText("The basket is empty."));
		assertEquals(0, b.getUsed());
		b.onManualFill(4);
		assertEquals(BasketTracker.Outcome.NONE, b.onCheckText("The coal bag contains 27 pieces of coal."));
		assertEquals(4, b.getUsed());
		assertEquals(BasketTracker.Outcome.NONE, b.onCheckText(null));

		BasketTracker f = barrel();
		assertEquals(BasketTracker.Outcome.UPDATED, f.onCheckText("The barrel contains:<br>10 x Raw shark<br>2 x Raw swordfish"));
		assertEquals(12, f.getUsed());
		assertEquals(BasketTracker.Outcome.UPDATED, f.onCheckText("The barrel is empty."));
		assertEquals(0, f.getUsed());
	}

	@Test
	public void chatNeverCarriesTheCheckSummary()
	{
		BasketTracker b = present();
		assertEquals(BasketTracker.Outcome.NONE, b.onMessage("The basket contains: 12 x Redwood logs"));
		assertEquals(0, b.getUsed());
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
		b.setPresent(BasketTracker.Kind.LOG_BASKET, false, false);
		b.onManualFill(10);
		b.onItemsIntoInventoryWhileOpen(1);
		assertEquals(10, b.getUsed());
	}

	@Test
	public void wornContainerCountsAndIsFlagged()
	{
		BasketTracker b = new BasketTracker();
		b.setPresent(BasketTracker.Kind.FISH_BARREL, true, true);
		assertTrue(b.isWorn());
		assertEquals(28, b.remaining());
		b.onItemsWithoutInventoryGain(3);
		assertEquals(3, b.getUsed());
		b.reset();
		assertFalse(b.isWorn());
	}

	@Test
	public void swappingContainerDropsTheOldCount()
	{
		BasketTracker b = present();
		b.onManualFill(12);
		assertEquals(12, b.getUsed());
		// A barrel picked up on the next trip has its own contents, unknown and assumed empty.
		b.setPresent(BasketTracker.Kind.FISH_BARREL, true, false);
		assertEquals(0, b.getUsed());
		b.onManualFill(5);
		// Re-scanning the same kind must not keep clearing it.
		b.setPresent(BasketTracker.Kind.FISH_BARREL, false, false);
		assertEquals(5, b.getUsed());
		assertFalse(b.isOpen());
	}

	@Test
	public void resetClearsEverything()
	{
		BasketTracker b = present();
		b.onManualFill(9);
		b.reset();
		assertFalse(b.isPresent());
		assertNull(b.getKind());
		assertEquals(0, b.getUsed());
		assertEquals(0, b.remaining());
	}
}
