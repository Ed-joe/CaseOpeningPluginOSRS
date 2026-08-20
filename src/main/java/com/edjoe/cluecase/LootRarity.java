package com.edjoe.cluecase;

import java.awt.Color;

enum LootRarity
{
	CONSUMER(new Color(176, 195, 217)),
	INDUSTRIAL(new Color(94, 152, 217)),
	MIL_SPEC(new Color(75, 105, 255)),
	RESTRICTED(new Color(136, 71, 255)),
	CLASSIFIED(new Color(211, 44, 230)),
	COVERT(new Color(235, 75, 75)),
	RARE_SPECIAL(new Color(255, 205, 54));

	private final Color color;

	LootRarity(Color color)
	{
		this.color = color;
	}

	Color getColor()
	{
		return color;
	}
}
