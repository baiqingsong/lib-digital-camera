package com.dawn.libdigitalcamera;

import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;

import com.dawn.digital_camera.R;
import com.dawn.digital_camera.view.GalleryFragment;
import com.dawn.digital_camera.view.SessionActivity;
import com.dawn.digital_camera.view.TabletSessionFragment;
import com.google.android.material.tabs.TabLayout;

/**
 * Demo Activity for lib-digital-camera.
 *
 * <p>Extends {@link SessionActivity} to automatically manage USB camera lifecycle.
 * Provides two tabs:
 * <ul>
 *   <li>Session — live view, camera settings, shoot controls via {@link TabletSessionFragment}</li>
 *   <li>Gallery — browse captured images via {@link GalleryFragment}</li>
 * </ul>
 * </p>
 */
public class MainActivity extends SessionActivity {

    private TabletSessionFragment sessionFragment;
    private GalleryFragment galleryFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(com.dawn.libdigitalcamera.R.layout.activity_main);

        // Keep the screen on while the camera session is active
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sessionFragment = new TabletSessionFragment();
        galleryFragment = new GalleryFragment();

        // Show session fragment by default
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, sessionFragment)
                .commit();

        TabLayout tabLayout = findViewById(com.dawn.libdigitalcamera.R.id.tab_layout);
        tabLayout.addTab(tabLayout.newTab().setText("Session"));
        tabLayout.addTab(tabLayout.newTab().setText("Gallery"));

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                Fragment fragment = tab.getPosition() == 0 ? sessionFragment : galleryFragment;
                getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        getCameraManager().onNewIntent(intent);
    }
}
