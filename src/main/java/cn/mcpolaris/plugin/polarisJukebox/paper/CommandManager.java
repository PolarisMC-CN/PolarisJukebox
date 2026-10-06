package cn.mcpolaris.plugin.polarisJukebox.paper;

import cn.mcpolaris.plugin.polarisJukebox.paper.data.BoxGroup;
import cn.mcpolaris.plugin.polarisJukebox.paper.util.GsonUtil;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.SneakyThrows;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CommandManager implements CommandExecutor {
    @SneakyThrows
    private static void createIfNotExist(String name) {
        if (ConfigManager.getGroups().containsKey(name)) return; // 已经存在且被读取过了
        File file = new File(PolarisJukebox.getInstance().getDataFolder(), "data" + File.separator + name + ".json");
        if (!file.exists()) {  // 文件不存在
            file.getParentFile().mkdirs();
            file.createNewFile();
            Files.writeString(file.toPath(), GsonUtil.parseFromObj(BoxGroup.of()));
        }
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        ConfigManager.getGroups().put(name, GsonUtil.parseFromStr(content, BoxGroup.class));  // 写入缓存
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String... args) {
        PolarisJukebox.getInstance().getServer().getScheduler().runTaskAsynchronously(PolarisJukebox.getInstance(), () -> {
            if (!sender.hasPermission("Polaris.admin")) {
                sender.sendMessage("Permission denied");
                return;
            }
            if (args.length == 0) {
                sender.sendMessage("/" + label + " <add|del> <text>  -  把看向的方块添加到对应组中");
                sender.sendMessage("/" + label + " reload  -  重载");
                return;
            }
            if ("reload".equalsIgnoreCase(args[0])) {
                ConfigManager.reloadConfig();
                sender.sendMessage("重载完成");
                return;
            }

            if (args.length < 2) {
                sender.sendMessage("参数不足");
                return;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Player Only");
                return;
            }

            String groupName = args[1];
            createIfNotExist(groupName);
            BoxGroup gotBoxes = ConfigManager.getGroups().get(groupName);
            if (gotBoxes == null) return;

            Block targetBlockExact = player.getTargetBlockExact(5);
            if (targetBlockExact != null && targetBlockExact.getType() == Material.JUKEBOX) {
                Location blockLoc = targetBlockExact.getLocation();
                if ("add".equalsIgnoreCase(args[0])) {
                    gotBoxes.getBoxes().add(blockLoc);
                    ConfigManager.getJukeboxCache().put(blockLoc, groupName);
                    ConfigManager.save();
                    player.sendMessage("已添加 " + blockLoc + " 的方块到组 " + groupName);
                } else if ("del".equalsIgnoreCase(args[0])) {
                    gotBoxes.getBoxes().remove(blockLoc);
                    ConfigManager.getJukeboxCache().remove(blockLoc);
                    ConfigManager.save();
                    player.sendMessage("已从 " + groupName + " 组移除 " + blockLoc + " 的方块");
                } else {
                    player.sendMessage("参数错误");
                }
            } else {
                player.sendMessage("你好像对不准？准星对准的方块貌似不是唱片机");
            }
        });
        return true;
    }
}
