/*
 * Copyright (C) 2012 The Android Open Source Project
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

package android.service.dreams;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.Slog;

/**
 * Internal helper for launching dreams to ensure consistency between the
 * <code>UiModeManagerService</code> system service and the <code>Somnambulator</code> activity.
 *
 * @hide
 */
public final class Sandman {
    private static final String TAG = "Sandman";

    private static final int DEFAULT_SCREENSAVER_ENABLED = 1;
    private static final int DEFAULT_SCREENSAVER_ACTIVATED_ON_DOCK = 1;

    // The component name of a special dock app that merely launches a dream.
    // We don't want to launch this app when docked because it causes an unnecessary
    // activity transition.  We just want to start the dream.
    private static final ComponentName SOMNAMBULATOR_COMPONENT =
            new ComponentName("com.android.systemui", "com.android.systemui.Somnambulator");


    // The sandman is eternal.  No one instantiates him.
    private Sandman() {
    }

    /**
     * Returns true if the specified dock app intent should be started.
     * False if we should dream instead, if appropriate.
     */
    public static boolean shouldStartDockApp(Context context, Intent intent) {
        ComponentName name = intent.resolveActivity(context.getPackageManager());
        return name != null && !name.equals(SOMNAMBULATOR_COMPONENT);
    }

    /**
     * Starts a dream manually.
     */
    public static void startDreamByUserRequest(Context context) {
        startDream(context, false);
    }

    /**
     * Starts a dream when docked if the system has been configured to do so,
     * otherwise does nothing.
     */
    public static void startDreamWhenDockedIfAppropriate(Context context) {
        if (!isScreenSaverEnabled(context)
                || !isScreenSaverActivatedOnDock(context)) {
            Slog.i(TAG, "Dreams currently disabled for docks.");
            return;
        }

        startDream(context, true);
    }

    private static void startDream(Context context, boolean docked) {
        try {
            IDreamManager dreamManagerService = IDreamManager.Stub.asInterface(
                    ServiceManager.getService(DreamService.DREAM_SERVICE));
            if (dreamManagerService != null && !dreamManagerService.isDreaming()) {
                if (docked) {
                    Slog.i(TAG, "Activating dream while docked.");

                    // Wake up.
                    // The power manager will wake up the system automatically when it starts
                    // receiving power from a dock but there is a race between that happening
                    // and the UI mode manager starting a dream.  We want the system to already
                    // be awake by the time this happens.  Otherwise the dream may not start.
                    PowerManager powerManager =
                            (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                    powerManager.wakeUp(SystemClock.uptimeMillis());
                } else {
                    Slog.i(TAG, "Activating dream by user request.");
                }

                // Dream.
                dreamManagerService.dream();
            }
        } catch (RemoteException ex) {
            Slog.e(TAG, "Could not start dream when docked.", ex);
        }
    }

    private static boolean isScreenSaverEnabled(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.SCREENSAVER_ENABLED, DEFAULT_SCREENSAVER_ENABLED,
                UserHandle.USER_CURRENT) != 0;
    }

    private static boolean isScreenSaverActivatedOnDock(Context context) {
        return Settings.Secure.getIntForUser(context.getContentResolver(),
                Settings.Secure.SCREENSAVER_ACTIVATE_ON_DOCK,
                DEFAULT_SCREENSAVER_ACTIVATED_ON_DOCK, UserHandle.USER_CURRENT) != 0;
    }
}
