/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dawn.digital_camera.ptp.commands.eos;

import android.util.Log;

import com.dawn.digital_camera.AppConfig;
import com.dawn.digital_camera.ptp.EosCamera;
import com.dawn.digital_camera.ptp.PtpCamera;
import com.dawn.digital_camera.ptp.PtpConstants;
import com.dawn.digital_camera.ptp.PtpConstants.Response;
import com.dawn.digital_camera.ptp.commands.Command;

public class EosAfDriveDeviceReadyCommand extends Command {

    private static final int MAX_RETRIES = 50;
    private static final int RETRY_DELAY = 200;

    private int retryCount = 0;
    private static String TAG = "EosAfDriveDeviceReadyCommand";

    private final int operation;
    private final int p0;
    private final int p1;

    public EosAfDriveDeviceReadyCommand(PtpCamera camera, int operation, int p0, int p1) {
        super(camera);
        this.operation = operation;
        this.p0 = p0;
        this.p1 = p1;
    }

    @Override
    public void exec(PtpCamera.IO io) {
        reset();
        io.handleCommand(this);

        if (responseCode == Response.DeviceBusy) {
            handleDeviceBusy();
        } else if (responseCode == Response.Ok) {
            handleFocusSuccess();
        } else if (responseCode == Response.OutOfFocus || responseCode == Response.InvalidStatus) {
            retryCount++;
            if (retryCount < MAX_RETRIES) {
                Log.i(TAG, "AF 未合焦，重试 " + retryCount);
                camera.enqueue(this, RETRY_DELAY);
            } else {
                handleFocusFailure();
            }
        } else {
            handleFocusFailure();
        }
    }

    private void handleDeviceBusy() {
        retryCount++;

        if (retryCount < MAX_RETRIES) {
            camera.enqueue(this, RETRY_DELAY);
            if (AppConfig.LOG) {
                Log.i(TAG, "AF still busy, retry " + retryCount);
            }
        } else {
            handleFocusFailure();
        }
    }

    private void handleFocusSuccess() {
        if (AppConfig.LOG) {
            Log.i(TAG, "AF succeeded after " + retryCount + " retries");
        }
        EosCamera eosCamera = (EosCamera) camera;
        eosCamera.onFocusEnded(true);
    }

    private void handleFocusFailure() {
        if (AppConfig.LOG) {
            Log.w(TAG, "AF failed after " + retryCount + " retries, response: " + PtpConstants.responseToString(responseCode));
        }
        EosCamera eosCamera = (EosCamera) camera;
        eosCamera.onFocusEnded(false);
    }

    @Override
    public void encodeCommand(java.nio.ByteBuffer b) {
        encodeCommand(b, operation, p0, p1);
    }

    @Override
    public void reset() {
        responseCode = 0;
        hasResponseReceived = false;
    }
}

