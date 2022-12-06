package com.example.testadrscreencapture;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;

public class ScreenCapService extends Service {
    MediaProjectionManager mediaProjectionManager;
    MediaProjection mediaProjection;
    VirtualDisplay virDisplay;
    Surface mSurface;
    VirtualDisplay.Callback virDisplayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
        }

        @Override
        public void onResumed() {
            super.onResumed();
        }

        @Override
        public void onStopped() {
            super.onStopped();
        }
    };

    MediaProjection.Callback projectionCallback = new MediaProjection.Callback() {
        @Override
        public void onStop() {
            super.onStop();
            Log.i("ScreenCap:", "MediaProjection onStop.");
        }
    };

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i("ScreenCap:", "ScreenCapService onStartCommand");
        startForeground(startId, createNotificationChannel());

        mediaProjectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Log.i("ScreenCap:", "getMediaProjection when onStartCommand ScreenCapService.");
        int resultCode = intent.getIntExtra("resultCode", 0);
        Intent data = intent.getParcelableExtra("data");
        Surface surface = intent.getParcelableExtra("surface");
        int surfaceWidth = intent.getIntExtra("width", 720);
        int surfaceHeight = intent.getIntExtra("height", 1280);
        if(mSurface==null)
            mSurface = surface;
        try
        {
            mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
        }
        catch (Exception ex)
        {
            Log.e("ScreenCap:", "getMediaProjection failed."+ex.toString());
        }

        int w = surfaceWidth;
        int h = surfaceHeight;

        virDisplay = mediaProjection.createVirtualDisplay("RECORDER_VIR_DISPLAY_0", w, h, 1,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, virDisplayCallback, null);
        Log.i("ScreenCap:", "createVirtualDisplay.w="+w+" h="+h+ " VirtualDisplay="+virDisplay);

        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.i("ScreenCap:", "ScreenCapService onDestroy");
        if(virDisplay!=null)
        {
            virDisplay.setSurface(null);
            virDisplay.release();
            virDisplay = null;
        }
        if(mediaProjection!=null){
            mediaProjection.stop();
            mediaProjection = null;
        }

        super.onDestroy();
    }

    Notification createNotificationChannel()
    {
        Notification.Builder builder =  new Notification.Builder(this.getApplicationContext()); // 获取Notification的构造器
        Intent nfIntent = new Intent(this, MainActivity.class); // 创建跳转到主页面的通知

        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            builder.setChannelId("ScreenCapService_notification");
        }
        //适配前台服务通知
        if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
        {
            NotificationManager ntyMgr = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
            NotificationChannel ntfCh = new NotificationChannel("ScreenCapService_notification", "ScreenCapService_notification", NotificationManager.IMPORTANCE_LOW);
            ntyMgr.createNotificationChannel(ntfCh);
        }

        Notification notification = builder.build();
        notification.defaults = Notification.DEFAULT_SOUND;
        Log.i("ScreenCap:", "ScreenCapService create Notification success");
        return notification;
    }
}
