package cn.mcpolaris.plugin.polarisJukebox.paper;

import cn.mcpolaris.plugin.polarisJukebox.paper.data.BoxGroup;
import cn.mcpolaris.plugin.polarisJukebox.paper.util.RecordSoundResolver;
import org.bukkit.Bukkit;
import org.bukkit.GameEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.Jukebox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.GenericGameEvent;
import org.bukkit.inventory.ItemStack;

public class EventListener implements Listener {
    private static final float RECORD_VOLUME = 4f;  // 跟原版一致，音量给到 4 才能传得远
    private static final float RECORD_PITCH = 1f;

    private static void syncToAllBoxes(Jukebox currentClicked, BoxGroup boxes, ItemStack item) {
        boxes.getBoxes().forEach(loc -> {
            Bukkit.getOnlinePlayers().forEach(p -> {
                p.sendBlockChange(loc, loc.getBlock().getBlockData());  // 同步一下，告诉客户端这里是有方块的，不是虚空
                PolarisJukebox.getInstance().getLogger().info("向玩家 " + p.getName() + " 发送位于 " + loc + " 的方块数据");
            });
            if (!loc.getChunk().isLoaded()) {
                loc.getChunk().load();  // 加载一下免得下面判空
            }
        });

        String sound = RecordSoundResolver.resolve(item);
        boxes.getBoxes().stream()
            .filter(loc -> !isSimpleEqualsLoc(loc, currentClicked.getLocation()))
            .map(Location::getBlock) // 找对应方块
            .forEach(b -> {
                if (b.getState() instanceof Jukebox state) {
                    if (item.isSimilar(state.getRecord())) return;  // 唱片一样就不处理，下一个
                    state.setRecord(item);
                    try {
                        state.update(false);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
                Bukkit.getOnlinePlayers().forEach(p -> {
                    p.playSound(b.getLocation(), sound, SoundCategory.RECORDS, 4f, 1f);
                    PolarisJukebox.getInstance().getLogger().info("向玩家 " + p.getName() + " 在 " + b.getLocation() + " 位置模拟音乐: " + sound);

                });

            }); // 同步唱片
        boxes.setCurrentPlaying(sound);  // 存一下现在在放什么
    }

    private static void clearAllBoxes(BoxGroup group) {
        group.getBoxes().stream()
            .map(Location::getBlock) // 找对应方块
            .filter(s -> s.getState() instanceof Jukebox) // 筛选是Jukebox的
            .forEach(b -> {
                Jukebox state = (Jukebox) b.getState();
                state.setRecord(null);
                state.stopPlaying();
                state.update(false);

            }); // 停止播放
        group.setCurrentPlaying("");
    }

    /**
     * 手动补一份唱片音效。
     *
     * <p>原版那套（world event 1010）只发给看得见这个方块的玩家，跨世界、跑远了的听不到，
     * 所以要自己把音频名解析出来重新发一遍。用字符串而不是 Sound 枚举，是为了能覆盖 Mod 注册的唱片。
     *
     * <p>注意这里不能 import Sound，会和 org.bukkit.Sound 撞名，只能写全限定名。
     */
    private static void playRecord(Player player, Location loc, ItemStack record) {
        String soundKey = RecordSoundResolver.resolve(record);
        if (soundKey != null) {
            try {
                player.playSound(loc, soundKey, SoundCategory.RECORDS, 4f, 1f);
                return;
            } catch (RuntimeException e) {  // 理论上不会发生，Key 是从游戏注册表里拿的
                PolarisJukebox.getInstance().getLogger().warning("音频名 " + soundKey + " 不合法，改用原版枚举：" + e);
            }
        }

        try {  // 解析失效时退回枚举，至少原版那 16 张唱片还能响
            player.playSound(loc, Sound.valueOf(record.getType().toString()), SoundCategory.RECORDS, RECORD_VOLUME, RECORD_PITCH);
        } catch (IllegalArgumentException ignored) {  // 不是原版唱片，枚举里没有这个名字
        }
    }

    private static boolean isSimpleEqualsLoc(Location loc1, Location loc2) {
        if (loc1 == null || loc2 == null) return loc1 == loc2;  // 同null

        return loc1.getWorld() == loc2.getWorld()
                   && loc1.getX() == loc2.getX()
                   && loc1.getY() == loc2.getY()
                   && loc1.getZ() == loc2.getZ();
    }

    @EventHandler
    public void onGameEvent(GenericGameEvent event) {
        if (event.getEvent() == GameEvent.JUKEBOX_PLAY) {
            Block blockAt = event.getLocation().getBlock();
            if (blockAt.getState() instanceof Jukebox jukebox) {
                ItemStack item = jukebox.getRecord().clone();
                String groupName = ConfigManager.getJukeboxCache().get(jukebox.getLocation());
                if (groupName == null) return;
                // 不包含该坐标，直接不要管了，浪费性能

                BoxGroup boxes = ConfigManager.getGroups().get(groupName);
                if (boxes == null) return;

                if (!boxes.getCurrentPlaying().isEmpty()) return;

                syncToAllBoxes(jukebox, boxes, item);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.PHYSICAL) return;  // 脚踩不用处理
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        ItemStack item = event.getItem() == null ? null : event.getItem().clone();
        if (block == null) return;
        if (block.getState() instanceof Jukebox clickedJukebox) {
            String groupName = ConfigManager.getJukeboxCache().get(clickedJukebox.getLocation());
            if (groupName == null) return;
            // 不包含该坐标，直接不要管了，浪费性能

            BoxGroup boxes = ConfigManager.getGroups().get(groupName);
            if (boxes == null) return;
            // 这个坐标反应的组没了

            if (event.getAction().toString().contains("LEFT")) { // 管控中的唱片机不允许直接破坏，应当先移除
                event.setCancelled(true);
                if (player.hasPermission("Polaris.admin")) {
                    player.sendMessage("请先将该唱片机移除出组，再拆除");
                }
                return;
            }

            if (!player.hasPermission("Polaris.admin")) {  // 玩家点击的唱片机属于管控的方块，此时应当拒绝玩家进行操作，而不能直接return任由玩家弹出
                event.setCancelled(true);
                return;
            }

            if (clickedJukebox.hasRecord()) {  // 有唱片，点一下应该是弹出已有唱片
                ItemStack record = clickedJukebox.getRecord().clone();
                String soundKey = RecordSoundResolver.resolve(record);
                clearAllBoxes(boxes);
                Bukkit.getScheduler().runTaskLater(PolarisJukebox.getInstance(), () -> {
                    Bukkit.getOnlinePlayers().forEach(p -> {
                        p.stopSound(soundKey, SoundCategory.RECORDS);
                    });
                }, 2L);  // 停止播放
            } else if (item != null && RecordSoundResolver.isRecord(item)) {  // 如果唱片机空的，玩家手上是唱片，那应该是插入唱片了
                syncToAllBoxes(clickedJukebox, boxes, item);
            }
        }
    }
}
