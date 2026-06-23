package com.qsteam.reconf.core.transformer;

import com.qsteam.reconf.api.ReConfig;
import com.qsteam.reconf.config.ConfigManager;
import com.qsteam.reconf.core.ReConfLoadingPlugin;
import com.qsteam.reconf.util.LogUtil;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;

public class InjectInitTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogUtil.getLogger(ReConfLoadingPlugin.class, InjectInitTransformer.class);

    private static final String ANNOTATION_DESC = Type.getDescriptor(ReConfig.class);

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE);

            if (node.visibleAnnotations != null) {
                for (AnnotationNode annotation : node.visibleAnnotations) {
                    if (annotation.desc.equals(ANNOTATION_DESC) &&
                            ConfigManager.CONFIG_NAMES.add(node.name.replace('/', '.')))
                        return injectInit(node, reader, basicClass);
                }
            }
        } catch (Exception e) {
            ReConfLoadingPlugin.LOGGER.error("[{}]", InjectInitTransformer.class.getSimpleName(), e);
            return basicClass;
        }

        return basicClass;
    }

    private byte[] injectInit(ClassNode node, ClassReader reader, byte[] fallbackBytes) {
        //TODO add inject
        return fallbackBytes;
    }

}
