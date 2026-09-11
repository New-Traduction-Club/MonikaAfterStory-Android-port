package org.renpy.android;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.Vibrator;
import android.os.VibrationEffect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ProgressBar;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.FileReader;

import java.util.Collections;
import java.util.HashMap;

import java.util.Collections;
import java.util.HashMap;

import android.app.PictureInPictureParams;
import android.util.Rational;

import android.app.PictureInPictureParams;
import android.util.Rational;

import android.content.SharedPreferences;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.widget.RemoteViews;

public class PythonSDLActivity extends SDLActivity {

    /**
     * This exists so python code can access this activity.
     */
    public static PythonSDLActivity mActivity = null;

    public static void logLifecycle(String message) {
        // Log.v("python", "[Lifecycle] " + message);
        // if (mActivity != null) {
        // try {
        // java.io.File logFile = new java.io.File(mActivity.getFilesDir(),
        // "jnl_log.log");
        // java.io.FileWriter writer = new java.io.FileWriter(logFile, true);
        // String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
        // java.util.Locale.US).format(new java.util.Date());
        // writer.write("[" + timestamp + "] " + message + "\n");
        // writer.close();
        // } catch (Exception e) {
        // // Ignore
        // }
        // }
    }

    private WindowDecorator mWindowDecorator = null;

    public WindowDecorator getWindowDecorator() {
        return mWindowDecorator;
    }

    private final BroadcastReceiver mCommandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DesktopWindowManager.ACTION_WINDOW_COMMAND.equals(intent.getAction())) {
                String targetId = intent.getStringExtra(DesktopWindowManager.EXTRA_ACTIVITY_ID);
                String command = intent.getStringExtra(DesktopWindowManager.EXTRA_COMMAND);
                if (targetId != null && targetId.equals(PythonSDLActivity.this.getClass().getName())) {
                    if (mWindowDecorator != null) {
                        if ("MINIMIZE".equals(command)) {
                            mWindowDecorator.minimizeWindow();
                        } else if ("RESTORE".equals(command)) {
                            mWindowDecorator.restoreWindow();
                        }
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mNotificationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("org.renpy.android.ACTION_NEW_DESKTOP_NOTIFICATION".equals(intent.getAction())) {
                boolean isWindowed = false;
                boolean isMaximized = false;
                if (mWindowDecorator != null) {
                    isWindowed = mWindowDecorator.isWindowedMode();
                    if (isWindowed) {
                        android.view.Window w = getWindow();
                        if (w != null) {
                            android.view.WindowManager.LayoutParams params = w.getAttributes();
                            if (params != null) {
                                isMaximized = params.width == android.view.ViewGroup.LayoutParams.MATCH_PARENT;
                            }
                        }
                    }
                    if (mWindowDecorator.isWindowMinimizedState()) {
                        return;
                    }
                }

                boolean shouldShowOnGame = !isWindowed || isMaximized;
                if (!shouldShowOnGame) {
                    return;
                }

                String title = intent.getStringExtra("title");
                if (title == null)
                    title = context.getString(R.string.game_notification_default_title);
                String message = intent.getStringExtra("message");
                if (message == null)
                    message = "";
                String imagePath = intent.getStringExtra("image_path");

                DesktopNotificationUI.INSTANCE.showNotificationToast(
                        PythonSDLActivity.this,
                        title,
                        message,
                        imagePath);
            }
        }
    };

    /**
     * The layout that contains the SDL view. VideoPlayer uses this to add
     * its own view on on top of the SDL view.
     */
    public FrameLayout mFrameLayout;

    /**
     * A layout that contains mLayout. This is a 3x3 grid, with the layout
     * in the center. The idea is that if someone wants to show an ad, they
     * can stick it in one of the other cells..
     */
    public LinearLayout mVbox;

    /**
     * This is set by the renpy.iap.Store when it's loaded. If it's not loadable,
     * this
     * remains null;
     */
    public StoreInterface mStore = null;

    ResourceManager resourceManager;

    protected String[] getLibraries() {
        if (isRenpy8Engine) {
            return new String[] {
                "837renpython",
            };
        }
        if (isRenpy7411Engine) {
            return new String[] {
                "7411renpython",
            };
        }
        if (isRenpy7Engine) {
            return new String[] {
                "rencompat",
                "renpython",
            };
        }
        return new String[] {
                "png16",
                "SDL2",
                "SDL2_image",
                "SDL2_ttf",
                "SDL2_gfx",
                "SDL2_mixer",
                "python2.7",
                "pymodules",
                "main",
                "rencompat",
        };
    }

    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001;
    public static Uri safUri = null;
    private volatile boolean mPendingPictureInPictureEnter = false;

    // Creates the IAP store, when needed. /////////////////////////////////////////

    public void createStore() {
        if (Constants.store.equals("none")) {
            return;
        }

        try {
            Class cls = Class.forName("org.renpy.iap.Store");
            cls.getMethod("create", PythonSDLActivity.class).invoke(null, this);
        } catch (Exception e) {
            Log.e("PythonSDLActivity", "Failed to create store: " + e.toString());
        }
    }

    // GUI code. /////////////////////////////////////////////////////////////

    public void addView(View view, int index) {
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        Log.v("python", "setContentView() called with view: " + view);
        mFrameLayout = new FrameLayout(this);

        // Ensure the FrameLayout doesn't steal focus from its children
        mFrameLayout.setFocusable(false);
        mFrameLayout.setFocusableInTouchMode(false);

        mFrameLayout.addView(view);

        mVbox = new LinearLayout(this);
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout,
                new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        if (mWindowDecorator != null) {
            View decoratedView = mWindowDecorator.decorate(mVbox, "Monika After Story");
            super.setContentView(decoratedView);
        } else {
            super.setContentView(mVbox);
        }

        mFrameLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    updatePictureInPictureParams();
                }
            }
        });

        ToolboxManager.initialize(this);
    }

    // Overriding this makes SDL respect the orientation given in the Android
    // manifest.
    // @Override
    // public void setOrientationBis(int w, int h, boolean resizable, String hint) {
    // return;
    // }

    // Code to unpack python and get things running ///////////////////////////

    public void recursiveDelete(File f) {
        if (f.isDirectory()) {
            for (File r : f.listFiles()) {
                recursiveDelete(r);
            }
        }
        f.delete();
    }

    /**
     * This determines if unpacking one the zip files included in
     * the .apk is necessary. If it is, the zip file is unpacked.
     */
    public void unpackData(final String resource, File target, String data_version) {

        boolean shouldUnpack = false;

        // The version of data in memory and on disk.
        String disk_version = null;

        String filesDir = target.getAbsolutePath();
        String disk_version_fn = filesDir + "/" + resource + ".version";

        // If no version, no unpacking is necessary.
        if (data_version != null) {
            File versionFile = new File(disk_version_fn);
            if (versionFile.exists()) {
                try {
                    BufferedReader br = new BufferedReader(new FileReader(versionFile));
                    disk_version = br.readLine();
                    br.close();
                } catch (Exception e) {
                    disk_version = "";
                }
            } else {
                disk_version = "";
            }

            if (!data_version.equals(disk_version)) {
                shouldUnpack = true;
            }
        }

        // If the disk data is out of date, extract it and write the
        // version file.
        if (shouldUnpack) {
            Log.v("python", "Extracting " + resource + " assets.");

            /**
             * Delete main.pyo, main.pyc, main.py unconditionally. This fixes a problem where we have
             * an old main script from another runtime or start.c won't run it.
             */
            new File(target, "main.pyo").delete();
            new File(target, "main.pyc").delete();
            new File(target, "main.py").delete();

            // Delete old libraries & renpy files.
            recursiveDelete(new File(target, "lib"));
            recursiveDelete(new File(target, "renpy"));
            recursiveDelete(new File(target, "include"));

            target.mkdirs();

            AssetExtract ae = new AssetExtract(this);
            if (!ae.extractTar(resource + ".mp3", target.getAbsolutePath())) {
                toastError("Could not extract " + resource + " data.");
            }

            try {
                // Write .nomedia.
                new File(target, ".nomedia").createNewFile();

                // Write version file.
                FileOutputStream os = new FileOutputStream(disk_version_fn);
                os.write(data_version.getBytes());
                os.close();
            } catch (Exception e) {
                Log.w("python", e);
            }
        }

    }

    /**
     * Show an error using a toast. (Only makes sense from non-UI
     * threads.)
     */
    public void toastError(final String msg) {

        final Activity thisActivity = this;

        runOnUiThread(new Runnable() {
            public void run() {
                InAppNotifier.show(thisActivity, msg, true);
            }
        });

        // Wait to show the error.
        synchronized (this) {
            try {
                this.wait(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    public native void nativeSetEnv(String variable, String value);

    public void preparePython() {
        long startTime = System.currentTimeMillis();
        Log.v("python", "Starting preparePython. Time: " + startTime);

        mActivity = this;

        resourceManager = new ResourceManager(this);

        File oldExternalStorage = new File(Environment.getExternalStorageDirectory(), getPackageName());
        File externalStorage = getExternalFilesDir(null);
        File path;

        if (externalStorage == null) {
            externalStorage = oldExternalStorage;
        }

        String customBaseDir = getIntent().getStringExtra("base_dir");
        if (customBaseDir != null && !customBaseDir.isEmpty()) {
            File testPath = new File(customBaseDir);
            if (testPath.isAbsolute()) {
                path = testPath;
            } else {
                path = new File(getFilesDir(), customBaseDir);
            }
        } else {
            File externalGameDir = new File(externalStorage, "game");
            if (externalGameDir.exists() && externalGameDir.isDirectory()) {
                path = externalStorage;
            } else if (resourceManager.getString("public_version") != null) {
                path = externalStorage;
            } else {
                path = getFilesDir();
            }
        }

        long unpackStart = System.currentTimeMillis();
        if (!isRenpy7OrLater()) {
            String privateVersion = resourceManager.getString("private_version");
            if (privateVersion != null) {
                unpackData("private", path, privateVersion);
            }
            String publicVersion = resourceManager.getString("public_version");
            if (publicVersion != null) {
                unpackData("public", externalStorage, publicVersion);
            }
        }
        Log.v("python", "unpackData finished. Duration: " + (System.currentTimeMillis() - unpackStart) + "ms");

        nativeSetEnv("ANDROID_ARGUMENT", path.getAbsolutePath());
        nativeSetEnv("ANDROID_PRIVATE", path.getAbsolutePath());
        nativeSetEnv("ANDROID_MASBASE", path.getAbsolutePath());
        if (isRenpy7411Engine) {
            nativeSetEnv("ANDROID_PACK_FF1", path.getAbsolutePath());
        }
        if (!isRenpy7OrLater()) {
            nativeSetEnv("REQUESTS_CA_BUNDLE", path.getAbsolutePath() + "/game/python-packages/certifi/cacert.pem");
            nativeSetEnv("SSL_CERT_FILE", path.getAbsolutePath() + "/game/python-packages/certifi/cacert.pem");
        }
        if (customBaseDir != null && !customBaseDir.isEmpty()
                && !customBaseDir.equals("monikaafterstory-masl-edition")) {
            ExperimentsActivity.ensureDeandroidPatch(path);
            nativeSetEnv("ANDROID_PUBLIC", path.getAbsolutePath());
            nativeSetEnv("ANDROID_OLD_PUBLIC", path.getAbsolutePath());
        } else {
            nativeSetEnv("ANDROID_PUBLIC", externalStorage.getAbsolutePath());
            nativeSetEnv("ANDROID_OLD_PUBLIC", oldExternalStorage.getAbsolutePath());
        }

        // Figure out the APK path.
        String apkFilePath;
        ApplicationInfo appInfo;
        PackageManager packMgmr = getApplication().getPackageManager();

        try {
            appInfo = packMgmr.getApplicationInfo(getPackageName(), 0);
            apkFilePath = appInfo.sourceDir;
        } catch (NameNotFoundException e) {
            apkFilePath = "";
        }

        nativeSetEnv("ANDROID_APK", apkFilePath);

        String expansionFile = getIntent().getStringExtra("expansionFile");
        if (expansionFile != null) {
            nativeSetEnv("ANDROID_EXPANSION", expansionFile);
        }

        nativeSetEnv("PYTHONOPTIMIZE", "2");
        nativeSetEnv("PYTHONHOME", path.getAbsolutePath());
        nativeSetEnv("PYTHONPATH", path.getAbsolutePath() + ":" + path.getAbsolutePath() + "/lib");

        Log.v("python", "Finished preparePython. Total Duration: " + (System.currentTimeMillis() - startTime) + "ms");

        nativeSetEnv("RENPY_VARIANT", "android");

    }

    // App lifecycle.
    public ImageView mPresplash = null;

    // The pack download progress bar.
    ProgressBar mProgressBar = null;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences prefs = newBase.getSharedPreferences("app_prefs", Context.MODE_PRIVATE);

        String language = prefs.getString("language", "English");
        java.util.Locale locale;
        if ("Español".equals(language)) {
            locale = new java.util.Locale("es");
        } else if ("Português".equals(language)) {
            locale = new java.util.Locale("pt");
        } else {
            locale = java.util.Locale.ENGLISH;
        }
        java.util.Locale.setDefault(locale);

        boolean darkMode = prefs.getBoolean("dark_mode_enabled", false);
        Log.v("PythonSDLActivity", "attachBaseContext - language: " + language + ", darkMode: " + darkMode);

        Configuration config = new Configuration();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLocale(locale);
        } else {
            config.locale = locale;
        }

        config.uiMode = (config.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                (darkMode ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);

        try {
            applyOverrideConfiguration(config);
            Log.v("PythonSDLActivity", "attachBaseContext - applyOverrideConfiguration successful");
        } catch (IllegalStateException e) {
            Log.e("PythonSDLActivity", "attachBaseContext - Failed to apply override configuration", e);
        }
        super.attachBaseContext(newBase);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!(this instanceof PythonSDLActivity784) && !(this instanceof PythonSDLActivity7411) && !(this instanceof PythonSDLActivity837)) {
            isRenpy7Engine = false;
            isRenpy7411Engine = false;
            isRenpy8Engine = false;
        }
        mActivity = this;
        logLifecycle("onCreate()");
        Log.v("python", "onCreate() started");
        mWindowDecorator = new WindowDecorator(this);
        OrientationPolicy.applyRequestedOrientation(this, ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);

        String customBaseDir = getIntent().getStringExtra("base_dir");
        if (customBaseDir != null && !customBaseDir.isEmpty()
                && !customBaseDir.equals("monikaafterstory-masl-edition")) {
            String displayName = customBaseDir.replace("-", " ").replace("_", " ");
            StringBuilder sb = new StringBuilder();
            for (String s : displayName.split(" ")) {
                if (s.length() > 0) {
                    sb.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1)).append(" ");
                }
            }
            setTitle(sb.toString().trim());
        }

        applyImmersiveFullscreen();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            getWindow().getDecorView()
                    .setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            applyImmersiveFullscreen();
                        }
                    });
        }

        if (mLayout == null) {
            Log.e("python", "mLayout is null after super.onCreate()");
            return;
        }

        // Initalize the store support.
        createStore();

        IntentFilter filter = new IntentFilter(DesktopWindowManager.ACTION_WINDOW_COMMAND);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mCommandReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(mCommandReceiver, filter);
        }

        IntentFilter notifFilter = new IntentFilter("org.renpy.android.ACTION_NEW_DESKTOP_NOTIFICATION");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(mNotificationReceiver, notifFilter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(mNotificationReceiver, notifFilter);
        }

        DesktopWindowManager.registerReceiver(this);

        Log.v("python", "onCreate() finished, mLayout initialized");
        InAppNotifier.show(this, R.string.toolbox_swipe_tip, true);
    }

    /**
     * Called by Ren'Py to hide the presplash after start.
     */
    public void hidePresplash() {
        Log.v("python", "hidePresplash() called");
        final PythonSDLActivity activity = this;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activity.mPresplash != null) {
                    ViewGroup parent = (ViewGroup) activity.mPresplash.getParent();
                    if (parent != null) {
                        parent.removeView(activity.mPresplash);
                    }
                    activity.mPresplash = null;
                }

                if (activity.mProgressBar != null) {
                    ViewGroup parent = (ViewGroup) activity.mProgressBar.getParent();
                    if (parent != null) {
                        parent.removeView(activity.mProgressBar);
                    }
                    activity.mProgressBar = null;
                }

                activity.applyImmersiveFullscreen();
                ToolboxManager.initialize(activity);
            }
        });
    }

    @Override
    public void finish() {
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            mWindowDecorator.notifyState("DESTROYED");
        }
        super.finish();
    }

    @Override
    protected void onDestroy() {
        Log.v("python", "onDestroy()");

        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            mWindowDecorator.notifyState("DESTROYED");
        }
        try {
            unregisterReceiver(mCommandReceiver);
        } catch (Exception e) {
        }
        try {
            unregisterReceiver(mNotificationReceiver);
        } catch (Exception e) {
        }
        try {
            DesktopWindowManager.unregisterReceiver(this);
        } catch (Exception e) {
        }

        DiscordRpcManager.stop();
        super.onDestroy();

        if (mStore != null) {
            mStore.destroy();
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 200);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.v("python", "onNewIntent()");
        super.onNewIntent(intent);
        setIntent(intent);
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            if (mWindowDecorator.isWindowMinimizedState()) {
                mWindowDecorator.restoreWindow();
            }
        }
    }

    public boolean mStopDone = true;

    @Override
    public void onStop() {
        logLifecycle("onStop() start");
        Log.v("python", "onStop() start.");

        super.onStop();

        if (mIsInPictureInPictureMode) {
            Log.v("python", "onStop() skipping wait, in PiP mode");
            return;
        }

        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            Log.v("python", "onStop() skipping wait, in windowed mode");
            return;
        }

        if (mPendingPictureInPictureEnter) {
            boolean inPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
            if (!inPictureInPicture) {
                DiscordRpcManager.stop();
            }
            mPendingPictureInPictureEnter = false;
        }
        long startTime = System.currentTimeMillis();

        synchronized (this) {
            while (true) {
                if (mStopDone) {
                    break;
                }

                // Backstop.
                if (startTime + 8000 < System.currentTimeMillis()) {
                    break;
                }

                try {
                    this.wait(100);
                } catch (InterruptedException e) {
                    /* pass */ }

            }
        }

        Log.v("python", "onStop() done.");
    }

    public void armOnStop() {
        Log.v("python", "armOnStop()");
        mStopDone = false;
    }

    public void finishOnStop() {
        Log.v("python", "finishOnStop()");

        synchronized (this) {
            mStopDone = true;
            this.notifyAll();
        }
    }

    // Support public APIs. ////////////////////////////////////////////////////

    public void openUrl(String url) {
        Log.i("python", "Opening URL: " + url);
        try {
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setData(Uri.parse(url));
            startActivity(i);
        } catch (Exception e) {
            Log.e("python", "Failed to open URL: " + url, e);
        }
    }

    public void vibrate(double s) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot((int) (1000 * s), VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate((int) (1000 * s));
            }
        }
    }

    public int getDPI() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        return metrics.densityDpi;
    }

    public PowerManager.WakeLock wakeLock = null;

    public void setWakeLock(boolean active) {
        if (wakeLock == null) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.SCREEN_BRIGHT_WAKE_LOCK, "Screen On");
            wakeLock.setReferenceCounted(false);
        }

        if (active) {
            wakeLock.acquire();
        } else {
            wakeLock.release();
        }
    }

    // Activity Requests ///////////////////////////////////////////////////////

    // The thought behind this is that this will make it possible to call
    // mActivity.startActivity(Intent, requestCode), then poll the fields on
    // this object until the response comes back.

    public int mActivityResultRequestCode = -1;
    public int mActivityResultResultCode = -1;
    public Intent mActivityResultResultData = null;

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (mStore != null && mStore.onActivityResult(requestCode, resultCode, resultData)) {
            return;
        }

        Log.v("python", "onActivityResult(" + requestCode + ", " + resultCode + ", " + resultData + ")");

        mActivityResultRequestCode = requestCode;
        mActivityResultResultCode = resultCode;
        mActivityResultResultData = resultData;

        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == RESULT_OK && resultData != null) {
            Uri uri = resultData.getData();
            safUri = uri;
            final int takeFlags = resultData.getFlags()
                    & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }

        super.onActivityResult(requestCode, resultCode, resultData);
    }

    // Llama esto desde Python usando JNI
    public static void openDocumentTree() {
        Activity activity = mSingleton;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE);
    }

    public void updatePictureInPictureParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

                Rational aspectRatio = new Rational(16, 9);
                if (mFrameLayout != null && mFrameLayout.getWidth() > 0 && mFrameLayout.getHeight() > 0) {
                    int w = mFrameLayout.getWidth();
                    int h = mFrameLayout.getHeight();
                    float ratio = (float) w / h;
                    if (ratio > 0.4184f && ratio < 2.39f) {
                        aspectRatio = new Rational(w, h);
                    }

                    if (!isInPictureInPictureMode()) {
                        android.graphics.Rect sourceRectHint = new android.graphics.Rect();
                        mFrameLayout.getGlobalVisibleRect(sourceRectHint);
                        builder.setSourceRectHint(sourceRectHint);
                    }
                }

                builder.setAspectRatio(aspectRatio);

                boolean isWindowed = mWindowDecorator != null && mWindowDecorator.isWindowedMode();
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(!isWindowed);
                    builder.setSeamlessResizeEnabled(true);
                }

                setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                Log.e("PythonSDLActivity", "Failed to update PiP params", e);
            }
        }
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                mPendingPictureInPictureEnter = true;
                mIsInPictureInPictureMode = true;
                PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder();

                Rational aspectRatio = new Rational(16, 9);
                if (mFrameLayout != null && mFrameLayout.getWidth() > 0 && mFrameLayout.getHeight() > 0) {
                    int w = mFrameLayout.getWidth();
                    int h = mFrameLayout.getHeight();
                    float ratio = (float) w / h;
                    if (ratio > 0.4184f && ratio < 2.39f) {
                        aspectRatio = new Rational(w, h);
                    }

                    if (!isInPictureInPictureMode()) {
                        android.graphics.Rect sourceRectHint = new android.graphics.Rect();
                        mFrameLayout.getGlobalVisibleRect(sourceRectHint);
                        builder.setSourceRectHint(sourceRectHint);
                    }
                }
                builder.setAspectRatio(aspectRatio);

                enterPictureInPictureMode(builder.build());
            } catch (Exception e) {
                mPendingPictureInPictureEnter = false;
                mIsInPictureInPictureMode = false;
                Log.e("PythonSDLActivity", "Enter PiP failed", e);
            }
        }
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig) {
        Log.v("PythonSDLActivity", "onPictureInPictureModeChanged: " + isInPictureInPictureMode);

        mIsInPictureInPictureMode = isInPictureInPictureMode;

        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        mPendingPictureInPictureEnter = false;
        if (isInPictureInPictureMode) {
            DiscordRpcManager.startIfEnabled(this);
        }

        handleNativeState();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        Log.v("PythonSDLActivity", "onConfigurationChanged - incoming uiMode: " + newConfig.uiMode);
        SharedPreferences prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE);
        String language = prefs.getString("language", "English");
        java.util.Locale locale;
        if ("Español".equals(language)) {
            locale = new java.util.Locale("es");
        } else if ("Português".equals(language)) {
            locale = new java.util.Locale("pt");
        } else {
            locale = java.util.Locale.ENGLISH;
        }

        boolean darkMode = prefs.getBoolean("dark_mode_enabled", false);
        Log.v("PythonSDLActivity", "onConfigurationChanged - user dark mode: " + darkMode);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            newConfig.setLocale(locale);
        } else {
            newConfig.locale = locale;
        }
        newConfig.uiMode = (newConfig.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) |
                (darkMode ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);

        Log.v("PythonSDLActivity", "onConfigurationChanged - outgoing uiMode: " + newConfig.uiMode);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        logLifecycle("onWindowFocusChanged: " + hasFocus);
        Log.v("python", "onWindowFocusChanged: " + hasFocus);
        if (hasFocus) {
            applyImmersiveFullscreen();
            if (mTextEdit != null && mTextEdit.getVisibility() == View.VISIBLE) {
                mTextEdit.requestFocus();
            } else if (mSurface != null) {
                View surfaceView = (View) mSurface;
                surfaceView.requestFocus();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        logLifecycle("onResume() start - isPaused=" + org.libsdl.app.SDLActivity.mIsPaused + ", isSurfaceReady="
                + org.libsdl.app.SDLActivity.mIsSurfaceReady + ", hasFocus=" + org.libsdl.app.SDLActivity.mHasFocus);
        if (mWindowDecorator != null) {
            mWindowDecorator.applyWindowDimensions();
        }
        applyImmersiveFullscreen();
        mPendingPictureInPictureEnter = false;
        DiscordRpcManager.startIfEnabled(this);

        // Cancel all scheduled notifications when the user returns to the game
        // Routing is handled by NotificationSchedulerReceiver in the main process.
        NotificationWorker.cancelAllNotifications(this);

        // Force mHasFocus to true in windowed mode for focus bugs on pause/resume
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            org.libsdl.app.SDLActivity.mHasFocus = true;
            getWindow().getDecorView().requestLayout();
            getWindow().getDecorView().invalidate();
            if (mFrameLayout != null) {
                mFrameLayout.requestLayout();
                mFrameLayout.invalidate();
            }
            if (org.libsdl.app.SDLActivity.mSurface != null) {
                final View surfaceView = (View) org.libsdl.app.SDLActivity.mSurface;
                surfaceView.requestLayout();
                surfaceView.invalidate();
            }
            org.libsdl.app.SDLActivity.handleResume();
        }

        long start = System.currentTimeMillis();
        getSharedPreferences("app_prefs", MODE_PRIVATE)
                .edit()
                .putLong("last_session_start", start)
                .apply();

        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            if (!mWindowDecorator.isWindowMinimizedState()) {
                mWindowDecorator.notifyState("RUNNING");
            }
        }
    }

    @Override
    protected void onPause() {
        logLifecycle("onPause() start");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInPictureInPictureMode() || mPendingPictureInPictureEnter) {
                mIsInPictureInPictureMode = true;
            }
        }

        boolean wasPiP = mIsInPictureInPictureMode;
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            mIsInPictureInPictureMode = true;
        }

        super.onPause();

        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            mIsInPictureInPictureMode = wasPiP;
        }

        boolean inPictureInPicture = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode();
        if (!inPictureInPicture && !mPendingPictureInPictureEnter) {
            DiscordRpcManager.stop();
        }
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        long start = prefs.getLong("last_session_start", 0);
        if (start > 0) {
            long now = System.currentTimeMillis();
            long played = prefs.getLong("played_today", 0);
            String today = new java.text.SimpleDateFormat("yyyyMMdd").format(new java.util.Date());
            String lastDay = prefs.getString("last_played_day", "");
            if (!today.equals(lastDay)) {
                played = 0; // reset if new day
            }
            played += (now - start);
            prefs.edit()
                    .putLong("played_today", played)
                    .putString("last_played_day", today)
                    .remove("last_session_start")
                    .apply();
        }
    }

    public void applyImmersiveFullscreen() {
        final View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(
                        android.view.WindowInsets.Type.statusBars() | android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            int options = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decorView.setSystemUiVisibility(options);
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (mWindowDecorator != null && mWindowDecorator.isWindowedMode()) {
            if (ev.getAction() == MotionEvent.ACTION_DOWN && !hasWindowFocus()) {
                mWindowDecorator.bringToFrontSelf();
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void setTitle(CharSequence title) {
        super.setTitle(title);
        if (mWindowDecorator != null && title != null) {
            mWindowDecorator.setWindowTitle(title.toString());
        }
    }

    @Override
    public void setTitle(int titleId) {
        super.setTitle(titleId);
        String title = getString(titleId);
        if (mWindowDecorator != null && title != null) {
            mWindowDecorator.setWindowTitle(title);
        }
    }
}
