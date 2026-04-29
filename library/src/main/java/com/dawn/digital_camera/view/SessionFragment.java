package com.dawn.digital_camera.view;

import com.dawn.digital_camera.AppSettings;
import com.dawn.digital_camera.SessionView;
import com.dawn.digital_camera.ptp.Camera;

public abstract class SessionFragment extends BaseFragment implements SessionView {

    protected boolean inStart;

    protected Camera camera() {
        if (getActivity() == null) {
            return null;
        }
        return ((SessionActivity) getActivity()).getCamera();
    }

    protected AppSettings getSettings() {
        return ((SessionActivity) getActivity()).getSettings();
    }

    @Override
    public void onStart() {
        super.onStart();
        inStart = true;
    }

    @Override
    public void onStop() {
        super.onStop();
        inStart = false;
    }
}
