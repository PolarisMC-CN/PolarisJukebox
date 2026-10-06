package cn.mcpolaris.plugin.polarisJukebox.paper.util;

import cn.mcpolaris.plugin.polarisJukebox.paper.PolarisJukebox;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.intellij.lang.annotations.Subst;

/**
 * 解析唱片物品在客户端对应哪个音频（sound event 的资源位置，如 "minecraft:music_disc.13"）。
 *
 * <p>原版只广播 world event 1010 + 物品数字 ID，超出世界事件范围的玩家收不到那个包，
 * 所以要把这个资源位置自己算出来，再用它手动补一份给远处的玩家。
 *
 * <p>只能走反射：1.20.1 服务端的类名是 Spigot 那套可读名，成员名却是混淆的
 * （ItemRecord#getSound() 实际叫 x()，SoundEffect#getLocation() 叫 a()），
 * 所以一律按返回类型找方法，不按名字找。混合端（Mohist/Arclight）则是 Mojang 全名，同一套逻辑也能命中。
 */
public final class RecordSoundResolver {

    private static final String[] RECORD_CLASS_NAMES = {
        "net.minecraft.world.item.RecordItem",  // Mojang 命名：混合端 / 1.20.5 之后的 Paper
        "net.minecraft.world.item.ItemRecord",  // Spigot 命名：1.20.4 及以前
    };

    private static final Map<Material, String> CACHE = new HashMap<>();
    private static final Map<String, String> OVERRIDES = new HashMap<>();   // 反射认不出的唱片在这里手动补
    private static final Map<Class<?>, Method> SOUND_GETTERS = new HashMap<>();  // 唱片类 -> 取 SoundEffect 的方法
    private static final Map<Class<?>, Method> KEY_GETTERS = new HashMap<>();    // SoundEffect 类 -> 取资源位置的方法
    private static final String NOT_A_RECORD = "";

    private static boolean broken;
    private static Method asNMSCopy;   // CraftItemStack.asNMSCopy(ItemStack)
    private static Method nmsGetItem;  // NMS ItemStack -> Item
    private static Class<?> recordClass;

    /**
     * 兜底入口：反射救不了的唱片（比如 ItemsAdder 那种原版 Material + CustomModelData 的假唱片）在这里指定
     */
    public static void putOverride(Material material, String soundKey) {
        OVERRIDES.put(material.name(), soundKey);
        CACHE.clear();
    }

    public static String resolve(Material material) {
        if (material == null || material.isAir()) return null;

        String cached = CACHE.get(material);
        if (cached != null) return cached.isEmpty() ? null : cached;

        String resolved = resolve(new ItemStack(material));
        CACHE.put(material, resolved == null ? NOT_A_RECORD : resolved);
        return resolved;
    }

    /**
     * @return 音频名；null 表示这不是唱片，或者反射已经失效
     */
    @Subst("")
    public static String resolve(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) return null;

        String override = OVERRIDES.get(stack.getType().name());
        if (override != null) return override;

        init();
        if (broken) return null;

        try {
            Object nmsStack = asNMSCopy.invoke(null, stack);
            Object nmsItem = nmsGetItem.invoke(nmsStack);
            if (!recordClass.isInstance(nmsItem)) return null;  // 不是唱片，最常见的一条路，先挡掉

            // 按真实类缓存：混合端上 Mod 唱片多是 RecordItem 的子类，继承下来的方法也得能命中
            Method getSound = SOUND_GETTERS.get(nmsItem.getClass());
            if (getSound == null) {
                getSound = findByReturnType(nmsItem.getClass(), null, "Sound");
                if (getSound == null) return null;
                SOUND_GETTERS.put(nmsItem.getClass(), getSound);
            }

            Class<?> soundClass = getSound.getReturnType();
            Method getKey = KEY_GETTERS.get(soundClass);
            if (getKey == null) {
                getKey = findByReturnType(soundClass, null, "Key");  // Spigot: MinecraftKey
                if (getKey == null) {
                    getKey = findByReturnType(soundClass, null, "Location");  // Mojang: ResourceLocation
                }
                if (getKey == null) return null;
                KEY_GETTERS.put(soundClass, getKey);
            }

            // MinecraftKey#toString 就是 "命名空间:路径"
            String sound = getKey.invoke(getSound.invoke(nmsItem)).toString();
            PolarisJukebox.getInstance().getLogger().info("唱片 " + stack.getType() + " 对应: " + sound);
            return sound;
        } catch (Throwable e) {
            broken = true;
            PolarisJukebox.getInstance().getLogger().warning("唱片音频名解析已停用：" + e);
            return null;
        }
    }

    /**
     * 比 Material#isRecord() 可靠：那个只认 16 个原版常量，Mod 唱片一律返回 false
     */
    public static boolean isRecord(ItemStack stack) {
        return resolve(stack) != null;
    }

    private static void init() {
        if (broken || asNMSCopy != null) return;
        synchronized (RecordSoundResolver.class) {
            if (broken || asNMSCopy != null) return;
            try {
                Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");
                Class<?> nmsItem = Class.forName("net.minecraft.world.item.Item");

                for (String name : RECORD_CLASS_NAMES) {
                    try {
                        recordClass = Class.forName(name);
                        break;
                    } catch (ClassNotFoundException ignored) {
                    }
                }
                if (recordClass == null) throw new ClassNotFoundException("找不到唱片物品类");

                // CraftBukkit 的包名带版本号（v1_20_R1），不能写死
                Class<?> craftItemStack = Class.forName(
                    Bukkit.getServer().getClass().getPackage().getName() + ".inventory.CraftItemStack"
                );
                asNMSCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);

                nmsGetItem = findByReturnType(nmsItemStack, nmsItem, null);
                if (nmsGetItem == null) throw new NoSuchMethodException("ItemStack 取 Item 的方法");
            } catch (Throwable e) {
                broken = true;
                PolarisJukebox.getInstance().getLogger().warning("唱片音频名解析初始化失败：" + e);
            }
        }
    }

    /**
     * 沿继承链找最先生效的那个声明，只认 public 无参方法。
     *
     * <p>不能直接用 getMethods()：它会把父类 Item 的 getDrinkingSound()/getEatingSound() 一起捞进来，
     * 那几个同样是无参、同样返回 SoundEffect，而 getMethods() 的顺序 JVM 并不保证，会随机取到错误的声音。
     *
     * @param exactType 非空按精确类型匹配，namePart 非空按返回类型简单名的包含关系匹配
     */
    private static Method findByReturnType(Class<?> owner, Class<?> exactType, String namePart) {
        for (Class<?> type = owner; type != null && type != Object.class; type = type.getSuperclass()) {
            for (Method method : type.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) continue;
                if (method.getParameterCount() != 0) continue;

                Class<?> returnType = method.getReturnType();
                if (returnType.isPrimitive() || returnType == void.class) continue;

                if (exactType != null && returnType == exactType) return method;
                if (namePart != null && returnType.getSimpleName().contains(namePart)) return method;
            }
        }
        return null;
    }
}
