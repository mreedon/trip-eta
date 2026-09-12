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
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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
	/** An inventory drop of at least this many slots is a deposit, not a manual drop. */
	private static final int DEPOSIT_DROP = 5;
	/** How long after a bank interface was open a big drop still counts as banking. */
	private static final int BANK_GRACE_TICKS = 3;
	private static final String BASKET_EMPTIED = "You empty your basket into the bank.";
	private static final String INVENTORY_FULL_PREFIX = "Your inventory is too full to hold any more";
	private static final String PRIOR_KEY_PREFIX = "secondsPerItem.";

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

	/** Whether the gathering animation was playing on the last tick. */
	@Getter
	private boolean gathering;

	private final Map<Activity, Double> priors = new EnumMap<>(Activity.class);
	private int occupiedSlots;
	private int occupiedAtTickStart;
	private int itemMessagesThisTick;
	private int basketUsed;
	private boolean bankOpen;
	private int lastBankTick = -1000;
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
		gathering = activity != null;
		if (gathering && !model.isActive())
		{
			startTrip(activity);
		}
		model.tick(gathering);

		// Items that arrived this tick but never showed up in the inventory went into the basket.
		int inventoryGain = Math.max(0, occupiedSlots - occupiedAtTickStart);
		int toBasket = Math.max(0, itemMessagesThisTick - inventoryGain);
		if (toBasket > 0 && config.basketCapacity() > 0)
		{
			basketUsed = Math.min(config.basketCapacity(), basketUsed + toBasket);
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
				onFull();
			}
			return;
		}
		checkLead(remaining);
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() != InventoryID.INV)
		{
			return;
		}
		int now = countOccupied(event.getItemContainer());
		int drop = occupiedSlots - now;
		occupiedSlots = now;
		if (drop < DEPOSIT_DROP)
		{
			return;
		}
		boolean banking = bankOpen || (tick - lastBankTick) <= BANK_GRACE_TICKS;
		if (banking)
		{
			finishTrip();
		}
		else if (config.basketCapacity() > 0)
		{
			// A big drop away from a bank is the player filling the basket by hand.
			basketUsed = Math.min(config.basketCapacity(), basketUsed + drop);
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankOpen = true;
			lastBankTick = tick;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (isBankInterface(event.getGroupId()))
		{
			bankOpen = false;
			lastBankTick = tick;
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

		if (BASKET_EMPTIED.equals(message))
		{
			basketUsed = 0;
			lastBankTick = tick;
			finishTrip();
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
			return;
		}
		if (model.isActive() && model.getActivity().isRollWithoutItemMessage(message))
		{
			model.onRollWithoutItem();
		}
	}

	/** Free inventory slots plus whatever the configured basket can still take. */
	int remainingCapacity()
	{
		int free = Math.max(0, INVENTORY_SIZE - occupiedSlots);
		int basket = config.basketCapacity() > 0 ? Math.max(0, config.basketCapacity() - basketUsed) : 0;
		return free + basket;
	}

	private void startTrip(Activity activity)
	{
		model.setPrior(priors.getOrDefault(activity, 0.0));
		model.start(activity, System.currentTimeMillis());
		log.debug("trip started: {} free={} basketRemaining={} prior={}s/item",
			activity, INVENTORY_SIZE - occupiedSlots, remainingCapacity() - (INVENTORY_SIZE - occupiedSlots), model.getPrior());
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
		double prior = model.finish();
		if (prior > 0 && items >= TripModel.MIN_ITEMS_TO_LEARN)
		{
			priors.put(activity, prior);
			savePrior(activity, prior);
		}
		log.debug("trip finished: {} items={} prior now {}s/item", activity, items, prior);
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
		if (config.notifyRuneLite())
		{
			notifier.notify(text);
		}
		if (config.dinkNotify())
		{
			double offSeconds = gathering ? 0 : model.getOffStreakTicks() * TripModel.TICK_SECONDS;
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
			if (v != null && v > 0)
			{
				priors.put(a, v);
			}
		}
	}

	private void savePrior(Activity activity, double secondsPerItem)
	{
		configManager.setRSProfileConfiguration(TripEtaConfig.GROUP, PRIOR_KEY_PREFIX + activity.name(), secondsPerItem);
	}

	private void resetAll()
	{
		model.reset();
		gathering = false;
		occupiedSlots = 0;
		occupiedAtTickStart = 0;
		itemMessagesThisTick = 0;
		basketUsed = 0;
		bankOpen = false;
	}
}
