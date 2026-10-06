package cn.mcpolaris.plugin.polarisJukebox.paper.data;

import java.util.HashSet;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import org.bukkit.Location;

@Data
@Accessors(chain = true)
@NoArgsConstructor(staticName = "of")
public class BoxGroup {
    private String currentPlaying = "";
    private Set<Location> boxes = new HashSet<>();
}
