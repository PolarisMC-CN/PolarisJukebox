package cn.mcpolaris.plugin.polarisJukebox.paper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.bukkit.Location;

public class GsonUtil {
    private static final Gson gson = new GsonBuilder()
                                         .setPrettyPrinting()
                                         .disableHtmlEscaping()
                                         .registerTypeAdapter(Location.class, new SimpleLocAdapter())
                                         .create();

    public static <T> T parseFromStr(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }

    public static String parseFromObj(Object obj) {
        return gson.toJson(obj);
    }
}
