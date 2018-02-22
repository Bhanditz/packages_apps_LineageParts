/*
 * Copyright (C) 2018 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.lineageos.lineageparts.style;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ActivityManager;
import android.app.WallpaperManager;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.preference.Preference;
import android.support.v7.graphics.Palette;
import android.support.v14.preference.SwitchPreference;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.content.Intent;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.RemoteException;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.Context;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.style.models.Accent;
import org.lineageos.lineageparts.style.models.StyleStatus;
import org.lineageos.lineageparts.style.util.AccentAdapter;
import org.lineageos.lineageparts.style.util.AccentUtils;
import org.lineageos.lineageparts.style.util.OverlayManager;
import org.lineageos.lineageparts.style.util.UIUtils;

import java.util.Arrays;
import java.util.List;

import lineageos.providers.LineageSettings;
import lineageos.style.StyleInterface;
import lineageos.style.Suggestion;

import com.android.settingslib.drawer.SettingsDrawerActivity;

public class StylePreferences extends SettingsPreferenceFragment {
    private static final String TAG = "StylePreferences";
    private static final String CHANGE_STYLE_PERMISSION =
            lineageos.platform.Manifest.permission.CHANGE_STYLE;
    private static final int REQUEST_CHANGE_STYLE = 68;

    private Preference mStylePref;
    private Preference mAccentPref;
    private SwitchPreference mForceBlackPref;

    private List<Accent> mAccents;

    private StyleInterface mInterface;
    private StyleStatus mStyleStatus;

    private IOverlayManager mOverlayManager;

    @Override
    public void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);

        addPreferencesFromResource(R.xml.style_preferences);

        mOverlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));

        mStylePref = findPreference("berry_global_style");
        mStylePref.setOnPreferenceChangeListener(this::onStyleChange);
        setupStylePref();

        mForceBlackPref = (SwitchPreference) findPreference("style_force_black");
        mForceBlackPref.setOnPreferenceChangeListener(this::onForceBlackPrefChange);
        setupForceBlackPref();

        mAccents = AccentUtils.getAccents(getContext(), mStyleStatus);
        mAccentPref = findPreference("style_accent");
        mAccentPref.setOnPreferenceClickListener(this::onAccentClick);
        setupAccentPref();

        Preference automagic = findPreference("style_automagic");
        automagic.setOnPreferenceClickListener(p -> onAutomagicClick());

        mInterface = StyleInterface.getInstance(getContext());
    }

    private boolean hasChangeStylePermission() {
        return getActivity().checkSelfPermission(CHANGE_STYLE_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    private void requestChangeStylePermission() {
        getActivity().requestPermissions(new String[] { CHANGE_STYLE_PERMISSION },
                REQUEST_CHANGE_STYLE);
    }

    private boolean onAccentClick(Preference preference) {
        if (!hasChangeStylePermission()) {
            requestChangeStylePermission();
            return false;
        }

        mAccents = AccentUtils.getAccents(getContext(), mStyleStatus);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_accent_title)
                .setAdapter(new AccentAdapter(mAccents, getContext()),
                        (dialog, i) -> onAccentSelected(mAccents.get(i)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        return true;
    }

    private void setupAccentPref() {
        String currentAccent = LineageSettings.System.getString(getContext().getContentResolver(),
                LineageSettings.System.BERRY_CURRENT_ACCENT);
        try {
            updateAccentPref(AccentUtils.getAccent(getContext(), currentAccent));
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, currentAccent + ": package not found.");
        }
    }

    private void onAccentSelected(Accent accent) {
        String previousAccent = LineageSettings.System.getString(getContext().getContentResolver(),
                LineageSettings.System.BERRY_CURRENT_ACCENT);

        OverlayManager om = new OverlayManager(getContext());
        if (!TextUtils.isEmpty(previousAccent)) {
            // Disable previous theme
            om.setEnabled(previousAccent, false);
        }

        mInterface.setAccent(accent.getPackageName());
        updateAccentPref(accent);
    }

    private boolean isUsingDarkOrBlackTheme() {
        if (mOverlayManager == null){
            mOverlayManager = IOverlayManager.Stub.asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE));
        }
        OverlayInfo systemuiThemeInfoDark = null;
        OverlayInfo systemuiThemeInfoBlack = null;
        try {
            systemuiThemeInfoDark = mOverlayManager.getOverlayInfo("org.lineageos.overlay.dark",
                    ActivityManager.getCurrentUser());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        try {
            systemuiThemeInfoBlack = mOverlayManager.getOverlayInfo("org.lineageos.overlay.black",
                    ActivityManager.getCurrentUser());
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return (systemuiThemeInfoDark != null && systemuiThemeInfoDark.isEnabled()) || 
               (systemuiThemeInfoBlack != null && systemuiThemeInfoBlack.isEnabled());
    }

    private void updateAccentPref(Accent accent) {
        int size = getResources().getDimensionPixelSize(R.dimen.style_accent_icon);

        mAccentPref.setSummary(accent.getName());
        mAccentPref.setIcon(UIUtils.getAccentBitmap(getResources(), size, accent.getColor()));
    }

    private boolean onAutomagicClick() {
        if (!hasStoragePermission()) {
            Toast.makeText(getContext(), getString(R.string.style_permission_error),
                    Toast.LENGTH_LONG).show();
            return false;
        }

        if (!hasChangeStylePermission()) {
            requestChangeStylePermission();
            return false;
        }

        Bitmap bitmap = getWallpaperBitmap();
        if (bitmap == null) {
            return false;
        }

        Integer[] colorsArray = new Integer[mAccents.size()];
        for (int i = 0; i < mAccents.size(); i++) {
            colorsArray[i] = mAccents.get(i).getColor();
        }

        new AutomagicTask(mInterface, bitmap, this::onAutomagicCompleted).execute(colorsArray);
        return true;
    }

    private void onAutomagicCompleted(Suggestion suggestion) {
        String styleType = getString(suggestion.globalStyle == StyleInterface.STYLE_GLOBAL_LIGHT ?
                R.string.style_global_entry_light : R.string.style_global_entry_dark_black).toLowerCase();

        String accentName = mAccents.get(suggestion.selectedAccent).getName().toLowerCase();
        String message = getString(R.string.style_automagic_dialog_content, styleType, accentName);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_automagic_title)
                .setMessage(message)
                .setPositiveButton(R.string.style_automagic_dialog_positive,
                        (dialog, i) -> applyStyle(suggestion))
                .setNegativeButton(android.R.string.cancel,
                        (dialog, i) -> increaseOkStatus())
                .show();
    }

    private void setupStylePref() {
        int preference = LineageSettings.System.getInt(getContext().getContentResolver(),
                LineageSettings.System.BERRY_GLOBAL_STYLE,
                StyleInterface.STYLE_GLOBAL_AUTO_WALLPAPER);

        setStyleIcon(preference);
        switch (preference) {
            case StyleInterface.STYLE_GLOBAL_LIGHT:
                mStyleStatus = StyleStatus.LIGHT_ONLY;
                break;
            case StyleInterface.STYLE_GLOBAL_DARK:
                mStyleStatus = StyleStatus.DARK_ONLY;
                break;
            default:
                mStyleStatus = StyleStatus.DYNAMIC;
                break;
        }
    }

    private void setupForceBlackPref() {
        int preference = LineageSettings.System.getInt(getContext().getContentResolver(),
                LineageSettings.System.BERRY_FORCE_BLACK, 0);
        mForceBlackPref.setChecked(preference == 1);
        mForceBlackPref.setEnabled(mStyleStatus != StyleStatus.LIGHT_ONLY);
    }

    private boolean onForceBlackPrefChange(Preference preference, Object newValue) {
        boolean value = (Boolean) newValue;
        if (isUsingDarkOrBlackTheme()){
            reload();
        }
        LineageSettings.System.putInt(getContext().getContentResolver(),
                LineageSettings.System.BERRY_FORCE_BLACK, value ? 1 : 0);
        return true;
    }

    private void applyStyle(Suggestion suggestion) {
        onStyleChange(mStylePref, suggestion.globalStyle);
        onAccentSelected(mAccents.get(suggestion.selectedAccent));
    }

    private boolean onStyleChange(Preference preference, Object newValue) {
        Integer value;
        if (newValue instanceof String) {
            value = Integer.valueOf((String) newValue);
        } else if (newValue instanceof Integer) {
            value = (Integer) newValue;
        } else {
            return false;
        }

        boolean accentCompatibility = checkAccentCompatibility(value);
        if (!accentCompatibility) {
            new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_global_title)
                .setMessage(R.string.style_accent_configuration_not_supported)
                .setPositiveButton(R.string.style_accent_configuration_positive,
                        (dialog, i) -> onAccentConflict(value))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
            return false;
        }

        int oldValue = LineageSettings.System.getInt(getContext().getContentResolver(),
                LineageSettings.System.BERRY_GLOBAL_STYLE, INDEX_WALLPAPER);

        if (oldValue != value){
            try {
                reload();
            }catch (Exception ignored){
            }
        }

        mInterface.setGlobalStyle(value);
        setStyleIcon(value);
        setupForceBlackPref();
        return true;
    }

    private void reload(){
        Intent intent2 = new Intent(Intent.ACTION_MAIN);
        intent2.addCategory(Intent.CATEGORY_HOME);
        intent2.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent2);
        Toast.makeText(getContext(), R.string.applying_theme_toast, Toast.LENGTH_SHORT).show();
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
              @Override
              public void run() {
                  Intent intent = new Intent(Intent.ACTION_MAIN);
                  intent.setClassName("org.lineageos.lineageparts",
                        "org.lineageos.lineageparts.style.StylePreferences");
                  intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                  intent.putExtra(SettingsDrawerActivity.EXTRA_SHOW_MENU, true);
                  getContext().startActivity(intent);
                  Toast.makeText(getContext(), R.string.theme_applied_toast, Toast.LENGTH_SHORT).show();
              }
        }, 2000);
    }

    private void setStyleIcon(int value) {
        int icon;
        switch (value) {
            case StyleInterface.STYLE_GLOBAL_AUTO_DAYTIME:
                icon = R.drawable.ic_style_time;
                break;
            case StyleInterface.STYLE_GLOBAL_LIGHT:
                icon = R.drawable.ic_style_light;
                break;
            case StyleInterface.STYLE_GLOBAL_DARK:
                icon = R.drawable.ic_style_dark;
                break;
            default:
                icon = R.drawable.ic_style_auto;
                break;
        }

        mStylePref.setIcon(icon);
    }

    private boolean checkAccentCompatibility(int value) {
        String currentAccentPkg = LineageSettings.System.getString(
                getContext().getContentResolver(), LineageSettings.System.BERRY_CURRENT_ACCENT);
        StyleStatus supportedStatus;
        try {
            supportedStatus = AccentUtils.getAccent(getContext(), currentAccentPkg)
                .getSupportedStatus();
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e.getMessage());
            supportedStatus = StyleStatus.DYNAMIC;
        }

        switch (supportedStatus) {
            case LIGHT_ONLY:
                return value == StyleInterface.STYLE_GLOBAL_LIGHT;
            case DARK_ONLY:
                return value == StyleInterface.STYLE_GLOBAL_DARK;
            case DYNAMIC:
            default: // Never happens, but compilation fails without this
                return true;
        }
    }

    private void onAccentConflict(int value) {
        StyleStatus proposedStatus;
        switch (value) {
            case StyleInterface.STYLE_GLOBAL_LIGHT:
                proposedStatus = StyleStatus.LIGHT_ONLY;
                break;
            case StyleInterface.STYLE_GLOBAL_DARK:
                proposedStatus = StyleStatus.DARK_ONLY;
                break;
            default:
                proposedStatus = StyleStatus.DYNAMIC;
                break;
        }

        // Let the user pick the new accent
        List<Accent> accents = AccentUtils.getAccents(getContext(), proposedStatus);

        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.style_accent_title)
                .setAdapter(new AccentAdapter(accents, getContext()),
                        (dialog, i) -> {
                            onAccentSelected(accents.get(i));
                            onStyleChange(mStylePref, value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Nullable
    private Bitmap getWallpaperBitmap() {
        WallpaperManager manager = WallpaperManager.getInstance(getContext());
        Drawable wallpaper = manager.getDrawable();

        if (wallpaper == null) {
            return null;
        }

        if (wallpaper instanceof BitmapDrawable) {
            return ((BitmapDrawable) wallpaper).getBitmap();
        }

        Bitmap bitmap = Bitmap.createBitmap(wallpaper.getIntrinsicWidth(),
                wallpaper.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        wallpaper.setBounds(0, 0 , canvas.getWidth(), canvas.getHeight());
        wallpaper.draw(canvas);
        return bitmap;
    }

    private boolean hasStoragePermission() {
        Activity activity = getActivity();
        return activity != null &&
                activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                        PackageManager.PERMISSION_GRANTED;
    }

    private static final class AutomagicTask extends AsyncTask<Integer, Void, Suggestion> {
        private final StyleInterface mInterface;
        private final Bitmap mWallpaper;
        private final Callback mCallback;
        private final Palette mPalette;

        AutomagicTask(StyleInterface styleInterface, Bitmap wallpaper, Callback callback) {
            mInterface = styleInterface;
            mWallpaper = wallpaper;
            mCallback = callback;
        }

        @Override
        public Suggestion doInBackground(Integer... colors) {
            int[] array = Arrays.stream(colors).mapToInt(Integer::intValue).toArray();
            return mInterface.getSuggestion(mWallpaper, array);
        }

        @Override
        public void onPostExecute(Suggestion suggestion) {
            mCallback.onDone(suggestion);
        }
    }

    private interface Callback {
        void onDone(Suggestion suggestion);
    }
}
