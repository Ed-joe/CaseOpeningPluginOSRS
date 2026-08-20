package com.edjoe.cluecase;

final class Loot
{
	private final int itemId;
	private final int quantity;

	Loot(int itemId, int quantity)
	{
		this.itemId = itemId;
		this.quantity = quantity;
	}

	int getItemId()
	{
		return itemId;
	}

	int getQuantity()
	{
		return quantity;
	}
}
