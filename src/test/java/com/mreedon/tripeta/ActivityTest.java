package com.mreedon.tripeta;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;

public class ActivityTest
{
	@Test
	public void woodcuttingLines()
	{
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("You get some redwood logs."));
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("You get some logs."));
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("You get an oak log."));
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("You get some mushrooms."));
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("Your Kandarin headgear provides you with an additional log."));
		assertEquals(Activity.WOODCUTTING, Activity.forItemMessage("The nature offerings enabled you to chop an extra log."));
		assertTrue(Activity.WOODCUTTING.isRollWithoutItemMessage("You strike a clean cut without gathering any material."));
		assertFalse(Activity.WOODCUTTING.isItemMessage("You strike a clean cut without gathering any material."));
	}

	@Test
	public void miningLines()
	{
		assertEquals(Activity.MINING, Activity.forItemMessage("You manage to mine some iron ore."));
		assertEquals(Activity.MINING, Activity.forItemMessage("You manage to mine some coal."));
		assertEquals(Activity.MINING, Activity.forItemMessage("You manage to mine an uncut sapphire."));
		assertEquals(Activity.MINING, Activity.forItemMessage("The Varrock platebody enabled you to mine an additional ore."));
		assertEquals(Activity.MINING, Activity.forItemMessage("Your celestial ring allows you to mine an additional ore."));
		assertFalse(Activity.MINING.isItemMessage("You swing your pick at the rock."));
		assertFalse(Activity.MINING.isRollWithoutItemMessage("You swing your pick at the rock."));
	}

	@Test
	public void fishingLines()
	{
		assertEquals(Activity.FISHING, Activity.forItemMessage("You catch a shrimp."));
		assertEquals(Activity.FISHING, Activity.forItemMessage("You catch some shrimps."));
		assertEquals(Activity.FISHING, Activity.forItemMessage("You catch a leaping trout."));
		assertEquals(Activity.FISHING, Activity.forItemMessage("You catch an anglerfish!"));
		assertEquals(Activity.FISHING, Activity.forItemMessage("You catch a tuna. It hardens as you handle it with your ice gloves."));
		assertEquals(Activity.FISHING, Activity.forItemMessage("Your cormorant returns with its catch."));
		assertEquals(Activity.FISHING, Activity.forItemMessage("Rada's blessing enabled you to catch an extra fish."));
		assertFalse(Activity.FISHING.isItemMessage("You fail to catch anything."));
	}

	@Test
	public void unrelatedLinesMatchNothing()
	{
		assertNull(Activity.forItemMessage("Your inventory is too full to hold any more logs."));
		assertNull(Activity.forItemMessage("You empty your basket into the bank."));
		assertNull(Activity.forItemMessage("You get some rest."));
	}

	@Test
	public void animationsResolveToTheirActivity()
	{
		assertEquals(Activity.WOODCUTTING, Activity.forAnimation(AnimationID.FORESTRY_2H_AXE_CHOPPING_CRYSTAL));
		assertEquals(Activity.WOODCUTTING, Activity.forAnimation(AnimationID.HUMAN_WOODCUTTING_DRAGON_AXE));
		assertEquals(Activity.MINING, Activity.forAnimation(AnimationID.HUMAN_MINING_DRAGON_PICKAXE));
		assertEquals(Activity.MINING, Activity.forAnimation(AnimationID.HUMAN_MINING_CRYSTAL_PICKAXE_WALL));
		assertEquals(Activity.FISHING, Activity.forAnimation(AnimationID.HUMAN_HARPOON_DRAGON));
		assertEquals(Activity.FISHING, Activity.forAnimation(AnimationID.HUMAN_SMALLNET));
		assertNull(Activity.forAnimation(-1));
		assertNull(Activity.forAnimation(AnimationID.HUMAN_OPENHEAVYCHEST));
	}

	@Test
	public void onlyWoodcuttingHasAnItemCoin()
	{
		assertEquals(0.8, Activity.WOODCUTTING.itemChanceWithMisses, 1e-9);
		assertEquals(1.0, Activity.MINING.itemChanceWithMisses, 1e-9);
		assertEquals(1.0, Activity.FISHING.itemChanceWithMisses, 1e-9);
	}
}
