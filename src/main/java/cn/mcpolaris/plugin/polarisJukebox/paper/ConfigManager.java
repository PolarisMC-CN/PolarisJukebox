package cn.mcpolaris.plugin.polarisJukebox.paper;

import cn.mcpolaris.plugin.polarisJukebox.paper.data.BoxGroup;
import cn.mcpolaris.plugin.polarisJukebox.paper.util.GsonUtil;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.SneakyThrows;
import org.bukkit.Location;

public class ConfigManager {
    @Getter
    private static final Map<String, BoxGroup> groups = new HashMap<>();  // 文件名，以及对应文件内的唱片机坐标
    @Getter
    private static final Map<Location, String> jukeboxCache = new HashMap<>();  // 对坐标的缓存，直接找到对应组，只在内存记录
    private static final File boxesFolder = new File(PolarisJukebox.getInstance().getDataFolder(), "data");

    @SneakyThrows
    public static void reloadConfig() {
        groups.clear();
        jukeboxCache.clear();
        PolarisJukebox.getInstance().getLogger().info("载入配置文件和日志");
        if (boxesFolder.isFile()) {
            PolarisJukebox.getInstance().getLogger().warning(boxesFolder.getAbsolutePath() + " 此目录应当是文件夹，而非文件. 配置文件加载已终止");
            return;
        }
        boxesFolder.mkdirs();
        for (File file : boxesFolder.listFiles()) {
            String fileName = file.getName();
            if (fileName.toLowerCase().endsWith(".json")) {
                if (!file.isFile()) continue;
                try {
                    String fileContent = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                    groups.put(fileName.substring(0, fileName.length() - 5), GsonUtil.parseFromStr(fileContent, BoxGroup.class));
                } catch (Throwable e) {
                    PolarisJukebox.getInstance().getLogger().warning("读取文件 " + file.getAbsolutePath() + " 失败");
                    e.printStackTrace();
                }
            }
        }
        getGroups().forEach((name, boxes) ->
                                boxes.getBoxes().stream()
                                    .filter(loc -> loc != null && loc.getWorld() != null)  // 世界不存在的坐标直接丢弃
                                    .forEach(loc -> jukeboxCache.put(loc, name))
        );
    }

    public static void save() {
        groups.forEach((name, boxes) -> {
            File file = new File(boxesFolder, name + ".json");
            try {
                Files.writeString(file.toPath(), GsonUtil.parseFromObj(boxes));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
