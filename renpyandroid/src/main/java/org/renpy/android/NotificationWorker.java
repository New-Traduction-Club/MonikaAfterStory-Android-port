package org.renpy.android;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.util.concurrent.TimeUnit;
import java.util.Random;

public class NotificationWorker extends Worker {

    public NotificationWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String[] messages = {
            "Noti 1",
            "Noti 2",
            "Noti 3",
            "Noti 4"
        };
        String message = messages[new Random().nextInt(messages.length)];

        NotificationManager manager = (NotificationManager) getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        String channelId = "game_channel";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(channelId, "Game Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            manager.createNotificationChannel(channel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), channelId)
                .setContentTitle("Debug")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setAutoCancel(true);

        manager.notify((int) System.currentTimeMillis(), builder.build());

        long delay = 1 + new Random().nextInt(6); // hours
        OneTimeWorkRequest nextWork =
            new OneTimeWorkRequest.Builder(NotificationWorker.class)
                .setInitialDelay(delay, TimeUnit.HOURS)
                .build();
        // WorkManager.getInstance(getApplicationContext()).enqueue(nextWork);
        return Result.success();
    }
}