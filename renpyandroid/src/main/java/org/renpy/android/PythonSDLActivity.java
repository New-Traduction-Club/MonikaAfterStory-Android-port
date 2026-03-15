package org.renpy.android;

import org.libsdl.app.SDLActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
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
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ProgressBar;

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
     * This is set by the renpy.iap.Store when it's loaded. If it's not loadable, this
     * remains null;
     */
    public StoreInterface mStore = null;

    ResourceManager resourceManager;

    protected String[] getLibraries() {
        return new String[] {
            "renpython",
        };
    }

    private static final int REQUEST_CODE_OPEN_DOCUMENT_TREE = 1001;
    public static Uri safUri = null;

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
        mVbox.addView(view, index, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, (float) 0.0));
    }

    public void removeView(View view) {
        mVbox.removeView(view);
    }

    @Override
    public void setContentView(View view) {
        mFrameLayout = new FrameLayout(this);
        mFrameLayout.addView(view);

        mVbox = new LinearLayout(this);
        mVbox.setOrientation(LinearLayout.VERTICAL);
        mVbox.addView(mFrameLayout, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, (float) 1.0));

        super.setContentView(mVbox);

        mFrameLayout.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if (left != oldLeft || right != oldRight || top != oldTop || bottom != oldBottom) {
                    updatePictureInPictureParams();
                }
            }
        });
    }


    // Overriding this makes SDL respect the orientation given in the Android
    // manifest.
    @Override
    public void setOrientationBis(int w, int h, boolean resizable, String hint) {
        return;
    }

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
    public void unpackData(final String resource, File target) {

        boolean shouldUnpack = false;

        // The version of data in memory and on disk.
        String data_version = resourceManager.getString(resource + "_version");
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
             * Delete main.pyo unconditionally. This fixes a problem where we have
             * a main.py newer than main.pyo, but start.c won't run it.
             */
            new File(target, "main.pyo").delete();

            // Delete old libraries & renpy files.
            recursiveDelete(new File(target, "lib"));
            recursiveDelete(new File(target, "renpy"));

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

        runOnUiThread(new Runnable () {
            public void run() {
                Toast.makeText(thisActivity, msg, Toast.LENGTH_LONG).show();
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

        long unpackStart = System.currentTimeMillis();
        unpackData("private", getFilesDir());
        Log.v("python", "unpackData finished. Duration: " + (System.currentTimeMillis() - unpackStart) + "ms");

        nativeSetEnv("ANDROID_PRIVATE", getFilesDir().getAbsolutePath());
        nativeSetEnv("ANDROID_PUBLIC",  externalStorage.getAbsolutePath());
        nativeSetEnv("ANDROID_OLD_PUBLIC", oldExternalStorage.getAbsolutePath());

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

        Log.v("python", "Finished preparePython. Total Duration: " + (System.currentTimeMillis() - startTime) + "ms");

    };

    // App lifecycle.
    public ImageView mPresplash = null;

    Bitmap getBitmap(String assetName) {
        try {
            InputStream is = getAssets().open(assetName);
            Bitmap rv = BitmapFactory.decodeStream(is);
            is.close();

            return rv;
        } catch (IOException e) {
            return null;
        }
    }

    // The pack download progress bar.
    ProgressBar mProgressBar = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        long startTime = System.currentTimeMillis();
        Log.v("python", "onCreate() started at " + startTime);
        super.onCreate(savedInstanceState);

        if (mLayout == null) {
            return;
        }

        // Initalize the store support.
        createStore();

        // Show the presplash.
        Bitmap presplashBitmap = getBitmap("android-presplash.png");

        if (presplashBitmap == null) {
            presplashBitmap = getBitmap("android-presplash.jpg");
        }

        if (presplashBitmap != null) {

            mPresplash = new ImageView(this);
            mPresplash.setBackgroundColor(presplashBitmap.getPixel(0, 0));
            mPresplash.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mPresplash.setImageBitmap(presplashBitmap);

            mLayout.addView(mPresplash, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT));
        }

    }

    /**
     * Called by Ren'Py to hide the presplash after start.
     */
    public void hidePresplash() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mActivity.mPresplash != null) {
                    mActivity.mLayout.removeView(mActivity.mPresplash);
                    mActivity.mPresplash = null;
                }

                if (mActivity.mProgressBar != null) {
                    mActivity.mLayout.removeView(mActivity.mProgressBar);
                    mActivity.mProgressBar = null;
                }
            }
        });
    }

    @Override
    public void finish() {
        // Return to Launcher
        try {
            Intent intent = new Intent(this, LauncherActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("PythonSDLActivity", "Failed to return to launcher", e);
        }
        
        // Skip nativeQuit() in SDLActivity.onDestroy() to prevent killing the process
        mSkipNativeQuit = true;
        super.finish();
    }

    @Override
    protected void onDestroy() {
        Log.v("python", "onDestroy()");

        super.onDestroy();

        if (mStore != null) {
            mStore.destroy();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.v("python", "onNewIntent()");
        setIntent(intent);
    }

    public boolean mStopDone = true;

    @Override
    public void onStop() {
        Log.v("python", "onStop() start.");

        super.onStop();
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
                } catch (InterruptedException e) { /* pass */ }

            }
        }

        Log.v("python", "onStop() done.");
    }

    public void armOnStop () {
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
        openURL(url);
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
            final int takeFlags = resultData.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            getContentResolver().takePersistableUriPermission(uri, takeFlags);
        }

        super.onActivityResult(requestCode, resultCode, resultData);
    }

    // Llama esto desde Python usando JNI
    public static void openDocumentTree() {
        Activity activity = mSingleton;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    builder.setAutoEnterEnabled(true);
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

                enterPictureInPictureMode(builder.build());
            } catch (Exception e) {
                Log.e("PythonSDLActivity", "Enter PiP failed", e);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        
        // Cancel all scheduled notifications when the user returns to the game
        // WorkManager may not be auto-initialized in the :renpy process, so initialize it manually
        try {
            androidx.work.WorkManager.getInstance(this);
        } catch (IllegalStateException e) {
            // Not initialized yet in this process, do it manually
            androidx.work.WorkManager.initialize(this, 
                new androidx.work.Configuration.Builder().build());
        }
        NotificationWorker.cancelAllNotifications(this);

        long start = System.currentTimeMillis();
        getSharedPreferences("app_prefs", MODE_PRIVATE)
            .edit()
            .putLong("last_session_start", start)
            .apply();
    }

    @Override
    protected void onPause() {
        super.onPause();
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
}
