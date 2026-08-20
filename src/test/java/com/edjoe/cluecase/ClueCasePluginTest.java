package com.edjoe.cluecase;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development-only entry point that registers this plugin before RuneLite starts. */
public class ClueCasePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ClueCasePlugin.class);
		RuneLite.main(args);
	}
}
