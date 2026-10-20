package vn.haohan.utilities.feature.carry.render;

import org.bukkit.entity.Player;
import vn.haohan.utilities.feature.carry.CarrySession;

public interface CarryRenderer {
    void start();
    void stop();
    void spawn(Player player, CarrySession session);
    void remove(CarrySession session);
}
