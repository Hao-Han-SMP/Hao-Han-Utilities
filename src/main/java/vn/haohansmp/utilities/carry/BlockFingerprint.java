package vn.haohansmp.utilities.carry;

import org.bukkit.Material;

public record BlockFingerprint(Material type, String blockData, int inventoryHash, int pdcHash) {
}
