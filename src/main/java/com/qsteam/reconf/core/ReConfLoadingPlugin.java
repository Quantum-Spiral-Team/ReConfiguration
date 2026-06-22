package com.qsteam.reconf.core;

import com.qsteam.reconf.core.transformer.InjectInitTransformer;
import com.qsteam.reconf.util.LogUtil;
import net.minecraftforge.common.ForgeVersion;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.slf4j.Logger;

@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
@IFMLLoadingPlugin.TransformerExclusions("com.qsteam.reconf.core")
@IFMLLoadingPlugin.MCVersion(ForgeVersion.mcVersion)
public class ReConfLoadingPlugin implements IFMLLoadingPlugin {

    private static final Logger LOGGER = LogUtil.getLogger(ReConfLoadingPlugin.class);

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{InjectInitTransformer.class.getName()};
    }

}
