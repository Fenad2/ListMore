package com.listmore.config;

import java.util.List;

import com.listmore.ListMore;

import fi.dy.masa.malilib.gui.GuiConfigsBase;

public class ListMoreConfigGui extends GuiConfigsBase {
	public ListMoreConfigGui() {
		super(10, 50, ListMore.MOD_ID, null, "listmore.gui.title");
	}

	@Override
	protected int getConfigWidth() {
		return 200;
	}

	@Override
	protected int getBrowserHeight() {
		return this.height - 70;
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return ConfigOptionWrapper.createFor(ListMoreConfigs.Generic.OPTIONS);
	}
}
