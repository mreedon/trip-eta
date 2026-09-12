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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class TripEtaOverlay extends OverlayPanel
{
	private static final Color BAR_FOREGROUND = new Color(88, 160, 88);
	private static final Color BAR_BACKGROUND = new Color(40, 40, 40, 180);
	private static final Color OFF_COLOR = new Color(255, 170, 60);
	private static final Color FULL_COLOR = new Color(255, 90, 90);
	private static final Color DIM = new Color(170, 170, 170);

	private final TripEtaPlugin plugin;
	private final TripEtaConfig config;

	@Inject
	TripEtaOverlay(TripEtaPlugin plugin, TripEtaConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TripModel model = plugin.getModel();
		if (!model.isActive())
		{
			if (config.hideWhenIdle())
			{
				return null;
			}
			panelComponent.getChildren().add(TitleComponent.builder().text("Trip ETA").build());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Waiting for a trip")
				.leftColor(DIM)
				.build());
			return super.render(graphics);
		}

		Activity activity = model.getActivity();
		int remaining = plugin.remainingCapacity();
		int total = model.getItems() + remaining;

		panelComponent.getChildren().add(TitleComponent.builder().text("Trip ETA").build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(capitalize(activity.itemNoun))
			.right(model.getItems() + " / " + total)
			.build());

		String etaText;
		Color etaColor = Color.WHITE;
		if (model.isFull())
		{
			etaText = "Full";
			etaColor = FULL_COLOR;
		}
		else
		{
			TripModel.Estimate est = model.estimate(remaining);
			if (est == null)
			{
				etaText = "measuring";
				etaColor = DIM;
			}
			else if (config.showRange())
			{
				etaText = TripModel.formatSeconds(est.lowSeconds) + " to " + TripModel.formatSeconds(est.highSeconds);
			}
			else
			{
				etaText = TripModel.formatSeconds(est.midSeconds);
			}
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left(activity.verb + " left")
			.right(etaText)
			.rightColor(etaColor)
			.build());

		if (!plugin.isGathering() && !model.isFull())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(activity.offLabel)
				.right(TripModel.formatSeconds(model.getOffStreakTicks() * TripModel.TICK_SECONDS))
				.rightColor(OFF_COLOR)
				.build());
		}

		ProgressBarComponent bar = new ProgressBarComponent();
		bar.setMinimum(0);
		bar.setMaximum(Math.max(1, total));
		bar.setValue(model.getItems());
		bar.setForegroundColor(model.isFull() ? FULL_COLOR : BAR_FOREGROUND);
		bar.setBackgroundColor(BAR_BACKGROUND);
		panelComponent.getChildren().add(bar);

		return super.render(graphics);
	}

	private static String capitalize(String s)
	{
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
