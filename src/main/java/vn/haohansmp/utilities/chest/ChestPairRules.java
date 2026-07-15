package vn.haohansmp.utilities.chest;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Chest;

public final class ChestPairRules {
    private ChestPairRules() {
    }

    public static boolean matchesOtherHalf(
            Material selectedMaterial,
            Chest.Type selectedType,
            BlockFace selectedFacing,
            Material candidateMaterial,
            Chest.Type candidateType,
            BlockFace candidateFacing
    ) {
        if (selectedMaterial != candidateMaterial || selectedType == Chest.Type.SINGLE) {
            return false;
        }
        Chest.Type expected = selectedType == Chest.Type.LEFT ? Chest.Type.RIGHT : Chest.Type.LEFT;
        return candidateType == expected && selectedFacing == candidateFacing;
    }
}
