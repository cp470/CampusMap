package com.example.wlucampusmap;

import android.content.Context;
import android.content.SharedPreferences;

public class AdminManager {
    private static final String PREF_NAME = "admin_prefs";
    private static final String KEY_ADMIN_LOGGED_IN = "admin_logged_in";
    private SharedPreferences preferences;

    // These are your admin credentials
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    public AdminManager(Context context) {
        preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // Returns true if correct credentials
    public boolean login(String username, String password) {
        if (ADMIN_USERNAME.equals(username) && ADMIN_PASSWORD.equals(password)) {
            preferences.edit().putBoolean(KEY_ADMIN_LOGGED_IN, true).apply();
            return true;
        }
        return false;
    }

    public void logout() {
        preferences.edit().putBoolean(KEY_ADMIN_LOGGED_IN, false).apply();
    }

    public boolean isAdminLoggedIn() {
        return preferences.getBoolean(KEY_ADMIN_LOGGED_IN, false);
    }
}