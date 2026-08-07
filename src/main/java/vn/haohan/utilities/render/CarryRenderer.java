package vn.haohan.utilities.render;

import org.bukkit.entity.Player;
import vn.haohan.utilities.carry.CarrySession;

public interface CarryRenderer {
    void start();
    void stop();
    void spawn(Player player, CarrySession session);
    void remove(CarrySession session);
}
