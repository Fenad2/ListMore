package com.listmore;

import com.listmore.config.ListMoreConfigGui;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public class ListMoreModMenuApiImpl implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return screen -> {
			ListMoreConfigGui gui = new ListMoreConfigGui();
			gui.setParent(screen);
			return gui;
		};
	}
}
