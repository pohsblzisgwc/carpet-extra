import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Class-file checks only: this never loads or initializes Minecraft classes. */
class ValidateMixins {
    private static final String MIXIN = "Lorg/spongepowered/asm/mixin/";
    private static final String INJECTION = MIXIN + "injection/";
    private final Map<String, ClassNode> classes = new HashMap<>();
    private final List<String> errors = new ArrayList<>();
    private int mixins;
    private int injections;

    public static void main(String[] args) throws IOException {
        var audit = new ValidateMixins();
        for (String config : args) {
            var json = JsonParser.parseString(Files.readString(Path.of(config))).getAsJsonObject();
            String prefix = json.get("package").getAsString().replace('.', '/') + "/";
            for (String side : List.of("mixins", "client", "server")) {
                if (json.has(side)) {
                    for (var name : json.getAsJsonArray(side)) audit.checkMixin(prefix + name.getAsString());
                }
            }
        }
        for (String error : audit.errors) System.err.println(error);
        if (!audit.errors.isEmpty()) throw new IllegalStateException(audit.errors.size() + " static mixin errors");
        System.out.println("Static mixin checks passed: " + audit.mixins + " mixins, " + audit.injections + " injectors.");
        System.out.println("Runtime application, local capture and interactions with other mods still require game testing.");
    }

    private ClassNode read(String name) {
        if (classes.containsKey(name)) return classes.get(name);
        try (var stream = ClassLoader.getSystemResourceAsStream(name + ".class")) {
            if (stream == null) return null;
            var node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            classes.put(name, node);
            return node;
        } catch (IOException e) {
            throw new IllegalStateException(name, e);
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        var result = new ArrayList<AnnotationNode>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }

    private static Object value(AnnotationNode annotation, String key, Object fallback) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (key.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
            }
        }
        return fallback;
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of(value);
    }

    private boolean hasField(ClassNode target, String name, String descriptor) {
        if (target == null) return false;
        return target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(descriptor))
                || (target.superName != null && hasField(read(target.superName), name, descriptor));
    }

    private boolean hasMethod(ClassNode target, String name, String descriptor) {
        if (target == null) return false;
        return target.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))
                || (target.superName != null && hasMethod(read(target.superName), name, descriptor))
                || target.interfaces.stream().anyMatch(i -> hasMethod(read(i), name, descriptor));
    }

    private void checkMixin(String name) {
        ClassNode mixin = read(name);
        if (mixin == null) {
            errors.add(name + ": missing mixin class");
            return;
        }
        mixins++;
        for (var annotation : annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations)) {
            if (!annotation.desc.equals(MIXIN + "Mixin;")) continue;
            var targets = new ArrayList<String>();
            for (Object type : list(value(annotation, "value", List.of()))) targets.add(((Type) type).getInternalName());
            for (Object type : list(value(annotation, "targets", List.of()))) targets.add(type.toString().replace('.', '/'));
            for (String targetName : targets) {
                ClassNode target = read(targetName);
                if (target == null) {
                    errors.add(name + ": missing target " + targetName);
                    continue;
                }
                for (var field : mixin.fields) {
                    for (var a : annotations(field.visibleAnnotations, field.invisibleAnnotations)) {
                        if (a.desc.equals(MIXIN + "Shadow;") && !hasField(target, field.name, field.desc))
                            errors.add(name + ": missing shadow field " + field.name + field.desc);
                    }
                }
                for (var method : mixin.methods) checkMethod(mixin, target, method);
            }
        }
    }

    private void checkMethod(ClassNode mixin, ClassNode target, MethodNode handler) {
        String context = mixin.name + "#" + handler.name;
        for (var a : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
            if (a.desc.equals(MIXIN + "Shadow;") || a.desc.equals(MIXIN + "gen/Invoker;")) {
                String name = value(a, "value", handler.name).toString();
                if (!hasMethod(target, name, handler.desc)) errors.add(context + ": missing method " + name + handler.desc);
            } else if (a.desc.equals(MIXIN + "gen/Accessor;")) {
                String name = value(a, "value", "").toString();
                if (name.isEmpty()) name = java.beans.Introspector.decapitalize(handler.name.replaceFirst("^(get|set|is)", ""));
                Type result = Type.getReturnType(handler.desc);
                String descriptor = result.equals(Type.VOID_TYPE) ? Type.getArgumentTypes(handler.desc)[0].getDescriptor() : result.getDescriptor();
                if (!hasField(target, name, descriptor)) errors.add(context + ": missing accessor field " + name + descriptor);
            } else if (a.desc.startsWith(INJECTION) && !a.desc.equals(INJECTION + "At;")) {
                injections++;
                int total = 0;
                for (Object selectorValue : list(value(a, "method", List.of()))) {
                    String selector = selectorValue.toString();
                    if (selector.startsWith("L") && selector.contains(";")) selector = selector.substring(selector.indexOf(';') + 1);
                    int descriptorStart = selector.indexOf('(');
                    String methodName = descriptorStart < 0 ? selector : selector.substring(0, descriptorStart);
                    String descriptor = descriptorStart < 0 ? null : selector.substring(descriptorStart);
                    var methods = target.methods.stream().filter(m -> m.name.equals(methodName)
                            && (descriptor == null || descriptor.equals(m.desc))).toList();
                    if (methods.isEmpty()) errors.add(context + ": missing injection method " + selector);
                    for (var method : methods) {
                        if (a.desc.equals(INJECTION + "Inject;")) checkCallback(context, handler, method);
                        if (a.desc.equals(INJECTION + "ModifyConstant;")) {
                            for (Object constant : list(value(a, "constant", List.of())))
                                total += countConstants(method, (AnnotationNode) constant);
                        } else {
                            for (Object at : list(value(a, "at", List.of())))
                                total += checkAt(context, a, handler, method, (AnnotationNode) at);
                        }
                    }
                }
                int required = (int) value(a, "require", 1);
                if (total < Math.max(1, required)) errors.add(context + ": injection matches " + total + " instructions; require=" + required);
            }
        }
    }

    private void checkCallback(String context, MethodNode handler, MethodNode target) {
        Type[] params = Type.getArgumentTypes(handler.desc);
        int callback = -1;
        for (int i = 0; i < params.length; i++) {
            if (params[i].getDescriptor().startsWith("Lorg/spongepowered/asm/mixin/injection/callback/CallbackInfo")) callback = i;
        }
        if (callback < 0) {
            errors.add(context + ": missing callback parameter");
            return;
        }
        String expected = Type.getReturnType(target.desc).equals(Type.VOID_TYPE) ? "CallbackInfo" : "CallbackInfoReturnable";
        if (!params[callback].getClassName().endsWith("." + expected)) errors.add(context + ": wrong callback type for " + target.name + target.desc);
        if (callback != 0 && !Arrays.equals(Arrays.copyOf(params, callback), Type.getArgumentTypes(target.desc)))
            errors.add(context + ": callback arguments disagree with " + target.name + target.desc);
        if ((target.access & Opcodes.ACC_STATIC) != 0 && (handler.access & Opcodes.ACC_STATIC) == 0)
            errors.add(context + ": instance handler for static target");
    }

    private int checkAt(String context, AnnotationNode injector, MethodNode handler, MethodNode method, AnnotationNode at) {
        String kind = value(at, "value", "").toString();
        String target = value(at, "target", "").toString();
        var matches = new ArrayList<AbstractInsnNode>();
        for (var instruction : method.instructions) {
            boolean match = switch (kind) {
                case "HEAD" -> instruction == method.instructions.getFirst();
                case "RETURN", "TAIL" -> instruction.getOpcode() >= Opcodes.IRETURN && instruction.getOpcode() <= Opcodes.RETURN;
                case "INVOKE" -> instruction instanceof MethodInsnNode call
                        && target.equals("L" + call.owner + ";" + call.name + call.desc);
                case "FIELD" -> instruction instanceof FieldInsnNode field
                        && target.equals("L" + field.owner + ";" + field.name + ":" + field.desc)
                        && ((int) value(at, "opcode", -1) < 0 || instruction.getOpcode() == (int) value(at, "opcode", -1));
                default -> throw new IllegalArgumentException(context + ": unsupported @At " + kind);
            };
            if (match) matches.add(instruction);
        }
        if (kind.equals("TAIL") && !matches.isEmpty()) matches = new ArrayList<>(List.of(matches.getLast()));
        int ordinal = (int) value(at, "ordinal", -1);
        if (ordinal >= 0) matches = ordinal < matches.size() ? new ArrayList<>(List.of(matches.get(ordinal))) : new ArrayList<>();
        if (injector.desc.equals(INJECTION + "Redirect;")) {
            for (var instruction : matches) {
                if (instruction instanceof MethodInsnNode call) {
                    var arguments = new ArrayList<Type>();
                    if (call.getOpcode() != Opcodes.INVOKESTATIC) arguments.add(Type.getObjectType(call.owner));
                    arguments.addAll(Arrays.asList(Type.getArgumentTypes(call.desc)));
                    Type[] actual = Type.getArgumentTypes(handler.desc);
                    boolean direct = Arrays.equals(actual, arguments.toArray(Type[]::new));
                    arguments.addAll(Arrays.asList(Type.getArgumentTypes(method.desc)));
                    if ((!direct && !Arrays.equals(actual, arguments.toArray(Type[]::new)))
                            || !Type.getReturnType(call.desc).equals(Type.getReturnType(handler.desc)))
                        errors.add(context + ": redirect signature disagrees with " + target);
                }
            }
        }
        if (injector.desc.equals(INJECTION + "ModifyArg;")) {
            for (var instruction : matches) {
                var call = (MethodInsnNode) instruction;
                int index = (int) value(injector, "index", -1);
                Type result = Type.getReturnType(handler.desc);
                Type[] arguments = Type.getArgumentTypes(call.desc);
                if ((index >= 0 && (index >= arguments.length || !result.equals(arguments[index])))
                        || (index < 0 && Arrays.stream(arguments).filter(result::equals).count() != 1))
                    errors.add(context + ": invalid ModifyArg index/type at " + target);
            }
        }
        for (var instruction : matches) checkLocals(context, handler, method, instruction);
        return matches.size();
    }

    private void checkLocals(String context, MethodNode handler, MethodNode target, AbstractInsnNode instruction) {
        Type[] arguments = Type.getArgumentTypes(handler.desc);
        for (int parameter = 0; parameter < arguments.length; parameter++) {
            var visible = handler.visibleParameterAnnotations;
            var invisible = handler.invisibleParameterAnnotations;
            for (var annotation : annotations(visible == null ? null : visible[parameter], invisible == null ? null : invisible[parameter])) {
                if (!annotation.desc.equals("Lcom/llamalad7/mixinextras/sugar/Local;")) continue;
                String descriptor = arguments[parameter].getDescriptor();
                int position = target.instructions.indexOf(instruction);
                var candidates = target.localVariables.stream()
                        .filter(local -> local.desc.equals(descriptor)
                                && target.instructions.indexOf(local.start) <= position
                                && target.instructions.indexOf(local.end) > position)
                        .sorted(Comparator.comparingInt(local -> local.index)).toList();
                List<?> names = list(value(annotation, "name", List.of()));
                if (!names.isEmpty()) candidates = candidates.stream().filter(local -> names.contains(local.name)).toList();
                int ordinal = (int) value(annotation, "ordinal", -1);
                int index = (int) value(annotation, "index", -1);
                if (index >= 0) candidates = candidates.stream().filter(local -> local.index == index).toList();
                boolean valid = ordinal >= 0 ? ordinal < candidates.size() : candidates.size() == 1;
                if (!valid) errors.add(context + ": unresolved @Local parameter " + parameter + " (" + descriptor + ", matches=" + candidates.size() + ")");
            }
        }
    }

    private int countConstants(MethodNode method, AnnotationNode constant) {
        Object expected = value(constant, "intValue", value(constant, "doubleValue", null));
        if (expected == null) throw new IllegalArgumentException("Unsupported constant selector");
        int count = 0;
        for (var instruction : method.instructions) {
            Object found = switch (instruction) {
                case LdcInsnNode ldc -> ldc.cst;
                case IntInsnNode integer when integer.getOpcode() == Opcodes.BIPUSH || integer.getOpcode() == Opcodes.SIPUSH -> integer.operand;
                default -> instruction.getOpcode() >= Opcodes.ICONST_M1 && instruction.getOpcode() <= Opcodes.ICONST_5
                        ? instruction.getOpcode() - Opcodes.ICONST_0 : null;
            };
            if (expected.equals(found)) count++;
        }
        int ordinal = (int) value(constant, "ordinal", -1);
        return ordinal < 0 ? count : (ordinal < count ? 1 : 0);
    }
}
