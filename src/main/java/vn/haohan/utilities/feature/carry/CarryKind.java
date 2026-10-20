package vn.haohan.utilities.feature.carry;

public enum CarryKind {
    BLOCK,
    ENTITY,
    SOUL_ANCHOR;

    public boolean isBlockLike() {
        return this != ENTITY;
    }
}
