package com.qsteam.reconf.core.transformer;

import com.qsteam.reconf.api.ReConfig;
import com.qsteam.reconf.core.ReConfLoadingPlugin;
import com.qsteam.reconf.util.LogUtil;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;

import static org.objectweb.asm.Opcodes.*;

public class InjectInitTransformer implements IClassTransformer {

    private static final Logger LOGGER = LogUtil.getLogger(ReConfLoadingPlugin.class, InjectInitTransformer.class);

    private static final String ANNOTATION_DESC = Type.getDescriptor(ReConfig.class);
    private static final String TYPE_ENUM_DESC = Type.getDescriptor(ReConfig.Type.class);

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;

        try {
            ClassReader reader = new ClassReader(basicClass);
            ClassNode node = new ClassNode();
            reader.accept(node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

            if (node.visibleAnnotations != null) {
                for (AnnotationNode annotation : node.visibleAnnotations) {
                    if (annotation.desc.equals(ANNOTATION_DESC) && isLazyType(annotation)) {
                        ClassReader fullReader = new ClassReader(basicClass);
                        ClassNode fullNode = new ClassNode();
                        fullReader.accept(fullNode, 0);

                        return injectInit(fullNode, fullReader, basicClass);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("An unexpected error occurred while reading the bytecode for class {}", name, e);
            return basicClass;
        }

        return basicClass;
    }

    /**
     * Reads the {@link ReConfig#type()} member off a raw (not-yet-loaded) {@code @ReConfig}
     * annotation node and reports whether it is explicitly set to {@link ReConfig.Type#LAZY}.
     *
     * <p>Annotation defaults are not present in the classfile's annotation
     * attribute, so an absent {@link ReConfig#type()} key correctly means "default
     * ({@link ReConfig.Type#INSTANCE}), not {@link ReConfig.Type#LAZY}" rather than requiring a separate default lookup.
     */
    private boolean isLazyType(AnnotationNode annotation) {
        if (annotation.values == null) return false;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            if ("type".equals(annotation.values.get(i))) {
                Object value = annotation.values.get(i + 1);
                if (value instanceof String[] enumValue
                        && enumValue.length == 2
                        && TYPE_ENUM_DESC.equals(enumValue[0])) {
                    return ReConfig.Type.LAZY.name().equals(enumValue[1]);
                }
                return false;
            }
        }

        return false;
    }

    private byte[] injectInit(ClassNode node, ClassReader reader, byte[] basicClass) {
        MethodNode clinit = node.methods.stream()
                .filter(m -> m.name.equals("<clinit>"))
                .findFirst()
                .orElse(null);

        if (clinit == null) {
            clinit = new MethodNode(ASM9, ACC_STATIC, "<clinit>", "()V", null, null);
            clinit.instructions.add(new InsnNode(RETURN));
            node.methods.add(clinit);
        }

        InsnList inject = new InsnList();
        inject.add(new LdcInsnNode(Type.getObjectType(node.name)));
        inject.add(new MethodInsnNode(INVOKESTATIC,
                "com/qsteam/reconf/config/ConfigManager", "sync",
                "(Ljava/lang/Class;)V", false
        ));

        for (AbstractInsnNode insn : clinit.instructions.toArray()) {
            if (insn.getOpcode() == RETURN) {
                clinit.instructions.insertBefore(insn, cloneList(inject));
            }
        }

        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        node.accept(writer);

        return writer.toByteArray();
    }

    private static InsnList cloneList(InsnList list) {
        InsnList newList = new InsnList();
        for (AbstractInsnNode node : list.toArray()) {
            newList.add(node.clone(null));
        }
        return newList;
    }

}
