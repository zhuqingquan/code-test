package com.example.testadrscreencapture;

import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import com.example.testadrscreencapture.ui.ScreenCapPreviewer;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.testadrscreencapture.databinding.ActivityMainBinding;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;

import static android.content.Context.MEDIA_PROJECTION_SERVICE;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        ScreenCapPreviewer capPreviewer = (ScreenCapPreviewer)findViewById(R.id.surfaceView_screenCapPreview);
        SurfaceHolder surfaceHolder = capPreviewer.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                startCapture(holder.getSurface());
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                //stopCapture();
            }
        });
    }

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

    static final int RECORD_REQUEST_CODE = 201;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void startCapture(Surface surface)
    {
        mediaProjectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, RECORD_REQUEST_CODE);
        mSurface = surface;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RECORD_REQUEST_CODE && resultCode==RESULT_OK)
        {
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            {
                Log.i("ScreenCap:", "start ScreenCapService.");
                Intent svc = new Intent(this, ScreenCapService.class);
                svc.putExtra("data", data);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("surface", mSurface);
                startForegroundService(svc);
            }
            else
            {
                Log.i("ScreenCap:", "start getMediaProjection.");
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                int w = 1920;
                int h = 1080;
                Log.i("ScreenCap:", "createVirtualDisplay.w="+w+" h="+h);
                virDisplay = mediaProjection.createVirtualDisplay("RECORDER_VIR_DISPLAY_0", w, h, 1,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mSurface, virDisplayCallback, null);
            }
        }
    }

}