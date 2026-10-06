package cn.mcpolaris.plugin.polarisJukebox.paper.util;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import org.bukkit.Bukkit;
import org.bukkit.Location;

/**
 * Location 的序列化适配器。
 * <p>
 * 只保存 world/x/y/z/yaw/pitch，避免 Gson 反射遍历整个 World 对象图。
 * World 内部含有 {@link java.lang.ref.WeakReference}，Java 9+ 模块系统下
 * 无法反射访问其私有字段 referent，会抛 JsonIOException。
 */
public class SimpleLocAdapter extends TypeAdapter<Location> {

    @Override
    public void write(JsonWriter out, Location loc) throws IOException {
        if (loc == null || loc.getWorld() == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("world").value(loc.getWorld().getName());
        out.name("x").value(loc.getX());
        out.name("y").value(loc.getY());
        out.name("z").value(loc.getZ());
        out.endObject();
    }

    @Override
    public Location read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String world = null;
        double x = 0, y = 0, z = 0;
        in.beginObject();
        while (in.hasNext()) {
            switch (in.nextName()) {
                case "world" -> world = in.nextString();
                case "x" -> x = in.nextDouble();
                case "y" -> y = in.nextDouble();
                case "z" -> z = in.nextDouble();
                default -> in.skipValue();
            }
        }
        in.endObject();
        if (world == null) {
            return null;
        }
        return new Location(Bukkit.getWorld(world), x, y, z);
    }
}
