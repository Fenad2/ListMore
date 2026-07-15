package com.ohmylist;

import com.ohmylist.config.OhMyListConfigGui;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class OhMyListModMenuApiImpl implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return screen -> {
			OhMyListConfigGui gui = new OhMyListConfigGui();
			gui.setParent(screen);
			return gui;
		};
	}
}
