package com.qsteam.reconf.mixin.config;

import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.discovery.ASMDataTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Loader.class)
public abstract class MixinLoader {

    @Redirect(
            method = "loadMods",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/config/ConfigManager;loadData(Lnet/minecraftforge/fml/common/discovery/ASMDataTable;)V"
            )
    )
    private void loadMods(ASMDataTable data) {}

}
