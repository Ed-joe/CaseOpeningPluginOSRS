package com.edjoe.cluecase;

public enum InteractionControl
{
	BOTH("Space or left-click", true, true),
	SPACE_ONLY("Space only", true, false),
	LEFT_CLICK_ONLY("Left-click only", false, true);

	private final String label;
	private final boolean space;
	private final boolean leftClick;

	InteractionControl(String label, boolean space, boolean leftClick)
	{
		this.label = label;
		this.space = space;
		this.leftClick = leftClick;
	}

	public boolean allowsSpace() { return space; }

	public boolean allowsLeftClick() { return leftClick; }

	@Override
	public String toString() { return label; }
}
