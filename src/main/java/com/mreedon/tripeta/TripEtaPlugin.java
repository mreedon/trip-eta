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

import com.google.inject.Provides;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Trip ETA",
	description = "Estimates when your inventory fills, counting only the time you're actually gathering",
	tags = {"inventory", "full", "trip", "afk", "eta", "timer", "woodcutting", "mining", "fishing", "dink"}
)
public class TripEtaPlugin extends Plugin
{
	private static final int INVENTORY_SIZE = 28;
	/** An inventory drop of at least this many slots is a deposit or a basket fill, not a manual drop. */
	private static final int DEPOSIT_DROP = 5;
	/** How long after a bank interface was open a big drop still counts as banking. */
	private static final int BANK_GRACE_TICKS = 3;
	private static final String INVENTORY_FULL_PREFIX = "Your inventory is too full to hold any more";
	private static final String CHECK_OPTION = "Check";
	/** How long after a Check click the item box that opens is taken to be the basket's. */
	private static final int CHECK_GRACE_TICKS = 3;
	private static final String PRIOR_KEY_PREFIX = "secondsPerRoll.";
	private static final String LEGACY_PRIOR_KEY_PREFIX = "secondsPerItem.";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TripEtaOverlay overlay;

	@Inject
	private TripEtaConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Notifier notifier;

	@Inject
	private DinkBridge dink;

	@Getter
	private final TripModel model = new TripModel();

	@Getter
	private final BasketTracker basket = new BasketTracker();

	/** Whether the gathering animation was playing on the last tick. */
	@Getter
	private boolean gathering;

	private final Map<Activity, Double> priors = new EnumMap<>(Activity.class);
	private int occupiedSlots;
	private int occupiedAtTickStart;
	private int itemMessagesThisTick;
	private boolean bankOpen;
	private int lastBankTick = -1000;
	private int checkClickedTick = -1000;
	private int tick;

	@Provides
	TripEtaConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TripEtaConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		resetAll();
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				loadPriors();
				readInventory();
			}
		});
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		resetAll();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				loadPriors();
				readInventory();
				break;
			case LOGIN_SCREEN:
				// A hop passes through HOPPING, not LOGIN_SCREEN, so a mid-trip hop keeps the trip.
				resetAll();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		tick++;
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}

		Activity activity = Activity.forAnimation(local.getAnimation());
		boolean wasGathering = gathering;
		gathering = activity != null;
		if (gathering && !model.isActive())
		{
			startTrip(activity);
		}
		if (gathering != wasGathering && model.isActive())
		{
			if (gathering)
			{
				log.debug("gathering resumed after {} off ({} anim {})",
					TripModel.formatSeconds(model.getOffStreakTicks() * TripModel.TICK_SECONDS), activity, local.getAnimation());
			}
			else
			{
				log.debug("gathering stopped: items={} rolls={} gatherTicks={} offTicks={} occupied={} basket={}/{}",
					model.getItems(), model.rolls(), model.getGatherTicks(), model.getOffTicks(), occupiedSlots,
					basket.getUsed(), basket.isPresent() ? BasketTracker.CAPACITY : 0);
			}
		}
		model.tick(gathering);

		// Reconcile this tick's chat arrivals against inventory movement. An item that
		// arrived without taking a slot went into an open basket; a slot gained with no
		// item arriving came out of the basket.
		int inventoryGain = Math.max(0, occupiedSlots - occupiedAtTickStart);
		int toBasket = Math.max(0, itemMessagesThisTick - inventoryGain);
		int fromBasket = Math.max(0, inventoryGain - itemMessagesThisTick);
		int intoInventory = Math.min(itemMessagesThisTick, inventoryGain);
		if (intoInventory > 0 && basket.isPresent() && basket.isOpen() && basket.getUsed() < BasketTracker.CAPACITY)
		{
			// An open basket only lets an item reach the inventory once it is full.
			basket.onItemsIntoInventoryWhileOpen(intoInventory);
			log.debug("open basket must be full: {} item(s) went to the inventory -> {}/{}",
				intoInventory, basket.getUsed(), BasketTracker.CAPACITY);
		}
		if (toBasket > 0)
		{
			if (basket.isPresent())
			{
				basket.onItemsWithoutInventoryGain(toBasket);
				log.debug("basket took {} item(s): {}/{} (messages={} inventoryGain={})",
					toBasket, basket.getUsed(), BasketTracker.CAPACITY, itemMessagesThisTick, inventoryGain);
			}
			else
			{
				log.debug("{} item message(s) with no inventory gain and no basket in inventory", toBasket);
			}
		}
		else if (fromBasket > 0 && basket.isPresent() && basket.getUsed() > 0)
		{
			basket.onInventoryGainWithoutItems(fromBasket);
			log.debug("basket gave up {} item(s): {}/{}", fromBasket, basket.getUsed(), BasketTracker.CAPACITY);
		}
		itemMessagesThisTick = 0;
		occupiedAtTickStart = occupiedSlots;

		if (!model.isActive())
		{
			return;
		}
		int remaining = remainingCapacity();
		if (remaining <= 0)
		{
			if (!model.isFull())
			{
				model.markFull();
				log.debug("full: items={} rolls={} gatherTicks={} offTicks={} occupied={} basket={}/{}",
					model.getItems(), model.rolls(), model.getGatherTicks(), model.getOffTicks(), occupiedSlots,
					basket.getUsed(), basket.isPresent() ? BasketTracker.CAPACITY : 0);
				onFull();
			}
			return;
		}
		checkLead(remaining);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.WORN)
		{
			// Baskets can be worn in the cape slot and still collect logs.
			rescanBasket();
			return;
		}
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		int now = countOccupied(event.getItemContainer());
		int drop = occupiedSlots - now;
		occupiedSlots = now;
		rescanBasket();

		if (drop < DEPOSIT_DROP)
		{
			return;
		}
		boolean banking = bankOpen || (tick - lastBankTick) <= BANK_GRACE_TICKS;
		log.debug("inventory dropped by {} to {} occupied; bankOpen={} ticksSinceBank={} -> {}",
			drop, now, bankOpen, tick - lastBankTick, banking ? "deposit" : (basket.isPresent() ? "manual basket fill" : "ignored"));
		if (banking)
		{
			finishTrip();
		}
		else
		{
			basket.onManualFill(drop);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		// "Check" on a basket opens an item box, not a chat line; remember the click so the
		// box that follows can be attributed to the basket and not to some other item.
		// A worn basket's Check comes through the equipment tab without an inventory item id.
		if (CHECK_OPTION.equals(event.getMenuOption()) && (BasketTracker.isBasket(event.getItemId()) || basket.isWorn()))
		{
			checkClickedTick = tick;
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankOpen = true;
			lastBankTick = tick;
			log.debug("bank interface {} opened", event.getGroupId());
			return;
		}
		if (event.getGroupId() == InterfaceID.OBJECTBOX && (tick - checkClickedTick) <= CHECK_GRACE_TICKS)
		{
			// The text is filled in after the load event, so read it on the next pass.
			clientThread.invokeLater(() ->
			{
				Widget text = client.getWidget(InterfaceID.Objectbox.TEXT);
				if (text == null)
				{
					return;
				}
				BasketTracker.Outcome outcome = basket.onCheckText(text.getText());
				log.debug("basket check box -> {} ({}/{})", outcome, basket.getUsed(), BasketTracker.CAPACITY);
			});
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankOpen = false;
			lastBankTick = tick;
			log.debug("bank interface {} closed", event.getGroupId());
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}
		String message = Text.removeTags(event.getMessage());

		BasketTracker.Outcome basketOutcome = basket.onMessage(message);
		if (basketOutcome != BasketTracker.Outcome.NONE)
		{
			log.debug("basket message \"{}\" -> {}/{}", message, basket.getUsed(), BasketTracker.CAPACITY);
			if (basketOutcome == BasketTracker.Outcome.EMPTIED_TO_BANK)
			{
				lastBankTick = tick;
				finishTrip();
			}
			return;
		}
		if (message.startsWith(INVENTORY_FULL_PREFIX))
		{
			if (model.isActive() && !model.isFull())
			{
				model.markFull();
				onFull();
			}
			return;
		}

		Activity activity = Activity.forItemMessage(message);
		if (activity != null)
		{
			itemMessagesThisTick++;
			if (!model.isActive())
			{
				startTrip(activity);
			}
			model.onItem(activity, System.currentTimeMillis());
			if (log.isDebugEnabled())
			{
				int remaining = remainingCapacity();
				TripModel.Estimate est = model.estimate(remaining);
				log.debug("item #{} (roll #{}): remaining={} gatherTicks={} offTicks={} s/roll={} s/item={} p={} eta={}",
					model.getItems(), model.rolls(), remaining, model.getGatherTicks(), model.getOffTicks(),
					String.format("%.1f", model.secondsPerRoll()), String.format("%.1f", model.secondsPerItem()), model.itemChance(),
					est == null ? "none" : TripModel.formatSeconds(est.lowSeconds) + "/" + TripModel.formatSeconds(est.midSeconds) + "/" + TripModel.formatSeconds(est.highSeconds));
			}
			return;
		}
		if (model.isActive() && model.getActivity().isRollWithoutItemMessage(message))
		{
			model.onRollWithoutItem();
			log.debug("roll without item #{}", model.getRollsWithoutItem());
		}
	}

	/** Free inventory slots plus whatever a basket in the inventory can still take. */
	int remainingCapacity()
	{
		return Math.max(0, INVENTORY_SIZE - occupiedSlots) + basket.remaining();
	}

	/** Every slot a log could go in: the inventory plus the basket when there is one. */
	int totalCapacity()
	{
		return INVENTORY_SIZE + (basket.isPresent() ? BasketTracker.CAPACITY : 0);
	}

	private void startTrip(Activity activity)
	{
		model.setPrior(priors.getOrDefault(activity, 0.0));
		model.start(activity, System.currentTimeMillis());
		log.debug("trip started: {} free={} basket={} prior={}s/roll itemChance={}",
			activity, INVENTORY_SIZE - occupiedSlots,
			basket.isPresent() ? basket.getUsed() + "/" + BasketTracker.CAPACITY + (basket.isOpen() ? " open" : " closed") : "none",
			String.format("%.2f", model.getPrior()), model.itemChance());
	}

	private void finishTrip()
	{
		if (!model.isActive())
		{
			return;
		}
		Activity activity = model.getActivity();
		if (config.dinkTripSummary() && model.getItems() > 0)
		{
			dink.notifyTripSummary(model);
		}
		int items = model.getItems();
		int rolls = model.rolls();
		double prior = model.finish();
		if (prior > 0 && rolls >= TripModel.MIN_ROLLS_TO_LEARN)
		{
			priors.put(activity, prior);
			savePrior(activity, prior);
		}
		log.debug("trip finished: {} items={} rolls={} prior now {}s/roll, misses seen={}",
			activity, items, rolls, String.format("%.2f", prior), model.isMissesSeenBefore());
	}

	private void checkLead(int remaining)
	{
		if (model.isLeadNotified())
		{
			return;
		}
		int leadSeconds = config.leadMinutes() * 60;
		if (leadSeconds <= 0)
		{
			return;
		}
		TripModel.Estimate est = model.estimate(remaining);
		if (est == null || est.midSeconds > leadSeconds)
		{
			return;
		}
		model.setLeadNotified(true);
		Activity a = model.getActivity();
		String text = "Trip ETA: inventory fills in about " + TripModel.formatSeconds(est.midSeconds) + " of " + a.verb.toLowerCase();
		log.debug("lead warning: remaining={} eta={}/{}/{} runelite={} dink={}", remaining,
			TripModel.formatSeconds(est.lowSeconds), TripModel.formatSeconds(est.midSeconds), TripModel.formatSeconds(est.highSeconds),
			config.notifyRuneLite(), config.dinkNotify());
		if (config.notifyRuneLite())
		{
			notifier.notify(text);
		}
		if (config.dinkNotify())
		{
			double offSeconds = model.awayStreakTicks() * TripModel.TICK_SECONDS;
			dink.notifyLead(model, est, remaining, offSeconds);
		}
	}

	private void onFull()
	{
		if (model.isFullNotified() || !config.notifyOnFull())
		{
			return;
		}
		model.setFullNotified(true);
		if (config.notifyRuneLite())
		{
			notifier.notify("Trip ETA: inventory full, " + model.getItems() + " " + model.getActivity().itemNoun + " this trip");
		}
		if (config.dinkNotify())
		{
			dink.notifyFull(model);
		}
	}

	private void readInventory()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		occupiedSlots = inventory == null ? 0 : countOccupied(inventory);
		occupiedAtTickStart = occupiedSlots;
		rescanBasket();
	}

	/** Look for a basket in the inventory, then the worn equipment (cape slot). Client thread only. */
	private void rescanBasket()
	{
		boolean hadBasket = basket.isPresent();
		int found = findBasket(client.getItemContainer(InventoryID.INV));
		boolean worn = false;
		if (found < 0)
		{
			found = findBasket(client.getItemContainer(InventoryID.WORN));
			worn = found >= 0;
		}
		basket.setPresent(found >= 0, found >= 0 && BasketTracker.isOpenBasket(found), worn);
		if (basket.isPresent() != hadBasket)
		{
			log.debug("basket {} ({}{})", basket.isPresent() ? "found" : "gone",
				basket.isOpen() ? "open" : "closed", basket.isWorn() ? ", worn" : "");
		}
	}

	/** The basket item ID in the container, or -1. */
	private static int findBasket(ItemContainer container)
	{
		if (container == null)
		{
			return -1;
		}
		for (Item item : container.getItems())
		{
			if (BasketTracker.isBasket(item.getId()))
			{
				return item.getId();
			}
		}
		return -1;
	}

	private static int countOccupied(ItemContainer container)
	{
		int n = 0;
		for (Item item : container.getItems())
		{
			if (item.getId() > 0 && item.getQuantity() > 0)
			{
				n++;
			}
		}
		return n;
	}

	private static boolean isBankInterface(int groupId)
	{
		return groupId == InterfaceID.BANKMAIN || groupId == InterfaceID.BANK_DEPOSITBOX;
	}

	private void loadPriors()
	{
		priors.clear();
		for (Activity a : Activity.values())
		{
			Double v = configManager.getRSProfileConfiguration(TripEtaConfig.GROUP, PRIOR_KEY_PREFIX + a.name(), Double.class);
			if (v == null)
			{
				// Pre-release builds stored seconds per item; convert once and drop the old key.
				Double legacy = configManager.getRSProfileConfiguration(TripEtaConfig.GROUP, LEGACY_PRIOR_KEY_PREFIX + a.name(), Double.class);
				if (legacy != null && legacy > 0)
				{
					v = legacy * a.itemChanceWithMisses;
					savePrior(a, v);
					configManager.unsetRSProfileConfiguration(TripEtaConfig.GROUP, LEGACY_PRIOR_KEY_PREFIX + a.name());
					log.debug("converted legacy prior for {}: {} s/item -> {} s/roll", a, legacy, v);
				}
			}
			if (v != null && v > 0)
			{
				priors.put(a, v);
			}
		}
		log.debug("priors loaded: {}", priors);
	}

	private void savePrior(Activity activity, double secondsPerRoll)
	{
		configManager.setRSProfileConfiguration(TripEtaConfig.GROUP, PRIOR_KEY_PREFIX + activity.name(), secondsPerRoll);
	}

	private void resetAll()
	{
		model.reset();
		basket.reset();
		gathering = false;
		occupiedSlots = 0;
		occupiedAtTickStart = 0;
		itemMessagesThisTick = 0;
		bankOpen = false;
	}
}
