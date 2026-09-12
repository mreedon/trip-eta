package com.mreedon.tripeta;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TripEtaPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TripEtaPlugin.class);
		RuneLite.main(args);
	}
}
