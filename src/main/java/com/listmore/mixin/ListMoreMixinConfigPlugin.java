package com.listmore.mixin;

import java.util.List;
import java.util.Set;

import me.fallenbreath.conditionalmixin.api.mixin.RestrictiveMixinConfigPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.listmore.ListMore;

// 使 @Restriction(require = @Condition("litematica")) 生效
// 未注册本插件时该注解不会参与过滤
public class ListMoreMixinConfigPlugin extends RestrictiveMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger(ListMore.MOD_ID);

    @Override
    protected void onRestrictionCheckFailed(String mixinClassName, String reason) {
        // LOGGER.warn("Disabled mixin '{}' due to: '{}'", mixinClassName, reason);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }
}
