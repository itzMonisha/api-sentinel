package com.sentinel.modules.project.entity;

public enum ProjectRole {
    OWNER,
    EDITOR,
    VIEWER;

    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    public boolean canManageMembers() {
        return this == OWNER;
    }

    public boolean canDeleteProject() {
        return this == OWNER;
    }
}
