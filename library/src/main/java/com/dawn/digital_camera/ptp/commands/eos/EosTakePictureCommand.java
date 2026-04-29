/**
 * Copyright 2013 Nils Assbeck, Guersel Ayaz and Michael Zoech
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dawn.digital_camera.ptp.commands.eos;

import java.nio.ByteBuffer;
import android.util.Log;

import com.dawn.digital_camera.ptp.EosCamera;
import com.dawn.digital_camera.ptp.PtpCamera.IO;
import com.dawn.digital_camera.ptp.PtpConstants.Operation;
import com.dawn.digital_camera.ptp.PtpConstants.Response;

public class EosTakePictureCommand extends EosCommand {

    private static final String TAG = "EosTakePictureCommand";
    private int retryCount = 0;
    private static final int MAX_RETRIES = 10;

    public EosTakePictureCommand(EosCamera camera) {
        super(camera);
    }

    @Override
    public void exec(IO io) {
        io.handleCommand(this);
        if (responseCode == Response.DeviceBusy ||
            responseCode == Response.OutOfFocus ||
            responseCode == Response.InvalidStatus) {
            
            retryCount++;
            if (retryCount < MAX_RETRIES) {
                Log.w(TAG, "拍照指令返回 " + com.dawn.digital_camera.ptp.PtpConstants.responseToString(responseCode) + ", 重试 " + retryCount);
                camera.onDeviceBusy(this, true);
            } else {
                // 超过最大重试次数：通知失败但不销毁会话，允许用户重新拍照
                Log.e(TAG, "拍照指令重试 " + retryCount + " 次后失败: " + com.dawn.digital_camera.ptp.PtpConstants.responseToString(responseCode) + "，通知失败但保持会话");
                camera.onFocusEnded(false);
            }
        } else if (responseCode != Response.Ok) {
            Log.e(TAG, "拍照指令失败: " + com.dawn.digital_camera.ptp.PtpConstants.responseToString(responseCode));
            // 非预期错误也只通知失败，不销毁会话
            camera.onFocusEnded(false);
        }
    }

    @Override
    public void encodeCommand(ByteBuffer b) {
        encodeCommand(b, Operation.EosTakePicture);
    }
}
