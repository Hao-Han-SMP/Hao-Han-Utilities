package vn.haohan.utilities.carry;

public enum CarryKind {
    BLOCK,
    ENTITY,
    SOUL_ANCHOR,
    CUSTOM_BLOCK;

    public boolean isBlockLike() {
        return this != ENTITY;
    }
}
