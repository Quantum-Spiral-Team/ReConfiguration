package com.qsteam.reconf.core;

import com.qsteam.reconf.core.transformer.AnnotationReplacerTransformer;
import com.qsteam.reconf.core.transformer.InjectInitTransformer;
import com.qsteam.reconf.util.LogUtil;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.slf4j.Logger;

import java.util.Map;

@IFMLLoadingPlugin.SortingIndex(Integer.MIN_VALUE + 1)
@IFMLLoadingPlugin.TransformerExclusions("com.qsteam.reconf.core")
public class ReConfLoadingPlugin implements IFMLLoadingPlugin {

    private static final Logger LOGGER = LogUtil.getLogger(ReConfLoadingPlugin.class);

    @Override
    public String[] getASMTransformerClass() {
        return new String[]{InjectInitTransformer.class.getName()};
    }

    @Override
    public void injectData(Map<String, Object> data) {

    }

}
