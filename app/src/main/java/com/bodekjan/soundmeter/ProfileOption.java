package com.bodekjan.soundmeter;

public class ProfileOption {
    private final int iconResId;
    private final String title;
    private final Action action;

    public enum Action {
        LOGIN,
        REGISTER,
        LOGOUT,
        HISTORY,
        SETTINGS,
        ABOUT
    }

    public ProfileOption(int iconResId, String title, Action action) {
        this.iconResId = iconResId;
        this.title = title;
        this.action = action;
    }

    public int getIconResId() {
        return iconResId;
    }

    public String getTitle() {
        return title;
    }

    public Action getAction() {
        return action;
    }
}
