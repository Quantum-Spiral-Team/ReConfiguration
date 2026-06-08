package com.qsteam.reconf.core;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

public class ReConfASMTransformer implements IClassTransformer {

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.EXPAND_FRAMES);

            if (node.visibleAnnotations != null) {
                for (AnnotationNode annotation : node.visibleAnnotations) {
                    if ("L".equals(annotation.desc)) { //TODO add annotation desc
                        return injectInit(node, reader, basicClass);
                    }
                }
            }
        } catch (Exception e) {
            return basicClass;
        }

        return basicClass;
    }

    private byte[] injectInit(ClassNode node, ClassReader reader, byte[] fallbackBytes) {
        //TODO add inject
        return fallbackBytes;
    }

}
