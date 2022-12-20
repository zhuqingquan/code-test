package com.example.testadrscreencapture.ui.notifications;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.testadrscreencapture.R;
import com.example.testadrscreencapture.databinding.FragmentNotificationsBinding;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_DEPTH_BUFFER_BIT;

public class NotificationsFragment extends Fragment {

    private NotificationsViewModel notificationsViewModel;
    private FragmentNotificationsBinding binding;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
//        notificationsViewModel =
//                new ViewModelProvider(this).get(NotificationsViewModel.class);
//
//        binding = FragmentNotificationsBinding.inflate(inflater, container, false);
//        View root = binding.getRoot();
//
//        //final TextView textView = binding.textNotifications;
//        notificationsViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
//            @Override
//            public void onChanged(@Nullable String s) {
//
//                //textView.setText(s);
//            }
//        });

        GLSurfaceView sview = new GLSurfaceView(getActivity());

        sview.setEGLContextClientVersion(2);
        //sview.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        sview.setRenderer(new Render());
        return sview;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    class Render implements GLSurfaceView.Renderer {

        float green = 0.0f;

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {

        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {

        }

        @Override
        public void onDrawFrame(GL10 gl) {
            GLES20.glClearColor(0.5f, green, 0.0f, 1.0f);
            GLES20.glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            green += 0.1f;
            if(green>=1.0) green = 0.0f;
        }
    }
}