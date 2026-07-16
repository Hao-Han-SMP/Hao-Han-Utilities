package vn.haohansmp.utilities.carry;

public enum CarryKind {
    BLOCK,
    ENTITY,
    SOUL_ANCHOR;

    public boolean isBlockLike() {
        return this != ENTITY;
    }
}
