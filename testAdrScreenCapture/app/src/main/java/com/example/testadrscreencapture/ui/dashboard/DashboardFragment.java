package com.example.testadrscreencapture.ui.dashboard;

import android.os.Bundle;
import android.util.Log;
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
import com.example.testadrscreencapture.databinding.FragmentDashboardBinding;

public class DashboardFragment extends Fragment {

    private DashboardViewModel dashboardViewModel;
    private FragmentDashboardBinding binding;
    static final String TAG = "EGLFragment";
    private EGLSurfaceView mEGLSurfaceView = null;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        dashboardViewModel =
                new ViewModelProvider(this).get(DashboardViewModel.class);

        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textDashboard;
        dashboardViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                //textView.setText(s);
            }
        });
        return root;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mEGLSurfaceView = (EGLSurfaceView)view.findViewById(R.id.surfaceViewEGL);
        //mEGLSurfaceView.getHolder().setFormat(PixelFormat.TRANSPARENT);
        Log.i(TAG, "onViewCreated");
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        if(mEGLSurfaceView!=null)
            mEGLSurfaceView.stopRender();
        Log.i(TAG, "onDestroyView");
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mEGLSurfaceView!=null)
            mEGLSurfaceView.pauseRender();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mEGLSurfaceView!=null)
            mEGLSurfaceView.resumeRender();
        Log.i(TAG, "onResume");
    }
}