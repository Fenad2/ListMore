package com.listmore.config;

import java.util.List;

import com.listmore.ListMore;

import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

public class ListMoreConfigGui extends GuiConfigsBase {
	private static ConfigTab tab = ConfigTab.GENERIC;

	public ListMoreConfigGui() {
		super(10, 50, ListMore.MOD_ID, null, "listmore.gui.title");
	}

	@Override
	public void initGui() {
		super.initGui();
		this.clearOptions();

		int x = 10;
		for (ConfigTab currentTab : ConfigTab.values()) {
			x += this.createButton(x, 26, -1, currentTab) + 2;
		}
	}

	private int createButton(int x, int y, int width, ConfigTab configTab) {
		ButtonGeneric button = new ButtonGeneric(x, y, width, 20, configTab.getDisplayName());
		button.setEnabled(tab != configTab);
		this.addButton(button, new ButtonListener(configTab, this));
		return button.getWidth();
	}

	@Override
	protected int getConfigWidth() {
		if (tab == ConfigTab.GENERIC) {
			return 200;
		}

		return 200;
	}

	@Override
	protected int getBrowserHeight() {
		return this.height - 70;
	}

	@Override
	protected boolean useKeybindSearch() {
		return tab == ConfigTab.ALL;
	}

	@Override
	public List<ConfigOptionWrapper> getConfigs() {
		return ConfigOptionWrapper.createFor(ListMoreConfigs.Generic.OPTIONS);
	}

	private record ButtonListener(ConfigTab tab, ListMoreConfigGui parent) implements IButtonActionListener {
		@Override
		public void actionPerformedWithButton(ButtonBase button, int mouseButton) {
			ListMoreConfigGui.tab = this.tab;
			this.parent.reCreateListWidget();
			if (this.parent.getListWidget() != null) {
				this.parent.getListWidget().resetScrollbarPosition();
			}
			this.parent.initGui();
		}
	}

	private enum ConfigTab {
		ALL("malilib.gui.title.all"),
		GENERIC("listmore.gui.tab.generic");

		private final String translationKey;

		ConfigTab(String translationKey) {
			this.translationKey = translationKey;
		}

		public String getDisplayName() {
			return StringUtils.translate(this.translationKey);
		}
	}
}
