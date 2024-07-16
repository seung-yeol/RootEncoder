/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pedro.library.multiple

import android.content.Context
import android.media.MediaCodec
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.common.AudioCodec
import com.pedro.common.ConnectChecker
import com.pedro.common.VideoCodec
import com.pedro.library.base.DisplayBase
import com.pedro.library.util.streamclient.RtmpStreamClient
import com.pedro.library.util.streamclient.RtspStreamClient
import com.pedro.library.util.streamclient.StreamBaseClient
import com.pedro.library.util.streamclient.StreamClientListener
import com.pedro.rtmp.rtmp.RtmpClient
import com.pedro.rtsp.rtsp.RtspClient
import java.nio.ByteBuffer

/**
 * Created by pedro, HirogaKatageri on 2021-05-24.
 *
 * Experimental Class.
 *
 * It supports multiple streams of rtmp and rtsp at same time.
 * You must set the same number of ConnectChecker that you want use.
 *
 * For example. 2 RTMP and 1 RTSP:
 * stream1, stream2, stream3 (stream1 and stream2 are ConnectChecker for RTMP. stream3 is ConnectChecker for RTSP)
 *
 * MultiDisplay multiDisplay = new MultiDisplay(context, true, new ConnectChecker[]{ stream1, stream2 },
 * new ConnectChecker[]{ stream3 });
 *
 * You can set an empty array or null if you don't want to use a specific protocol.
 * new MultiDisplay(context, true, new ConnectChecker[]{ stream1, stream2 }, null); // RTSP protocol is not used
 *
 * In order to use start, stop and other calls you must send type of stream and index to execute it.
 * Example (using previous example interfaces):
 *
 * multiDisplay.startStream(MultiType.RTMP, 1, endpoint); //stream2 is started
 * multiDisplay.stopStream(MultiType.RTSP, 0); //stream3 is stopped
 * multiDisplay.getStreamClient(MultiType.RTMP, 0).retry(delay, reason, backupUrl) //retry stream1
 *
 * NOTE:
 * If you call these methods nothing is executed:
 *
 * multiDisplay.startStream(endpoint);
 * multiDisplay.stopStream();
 *
 * The rest of methods without MultiType and index means that you will execute that command in all streams.
 * Read class code if you need info about any method.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class MultiDisplay(
    context: Context, useOpenGL: Boolean,
    connectCheckerRtmpList: Array<ConnectChecker>?,
    connectCheckerRtspList: Array<ConnectChecker>?
) : DisplayBase(context, useOpenGL) {

    private val streamClientListener = object: StreamClientListener {
        override fun onRequestKeyframe() {
            requestKeyFrame()
        }
    }
    private val rtmpClients = ArrayList<RtmpClient>()
    private val rtspClients = ArrayList<RtspClient>()
    private val rtmpStreamClients = ArrayList<RtmpStreamClient>()
    private val rtspStreamClients = ArrayList<RtspStreamClient>()

    init {
        connectCheckerRtmpList?.forEach {
            val client = RtmpClient(it)
            rtmpClients.add(client)
            rtmpStreamClients.add(RtmpStreamClient(client, streamClientListener))
        }
        connectCheckerRtspList?.forEach {
            val client = RtspClient(it)
            rtspClients.add(client)
            rtspStreamClients.add(RtspStreamClient(client, streamClientListener))
        }
    }

    fun getStreamClient(type: MultiType, index: Int): StreamBaseClient {
        return when (type) {
            MultiType.RTMP -> rtmpStreamClients[index]
            MultiType.RTSP -> rtspStreamClients[index]
        }
    }

    override fun getStreamClient(): StreamBaseClient {
        throw IllegalStateException("getStreamClient not allowed in Multi stream, use getStreamClient(type, index) instead")
    }

    override fun setVideoCodecImp(codec: VideoCodec) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.setVideoCodec(codec)
        }
        for (rtspClient in rtspClients) {
            rtspClient.setVideoCodec(codec)
        }
    }

    override fun setAudioCodecImp(codec: AudioCodec) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.setAudioCodec(codec)
        }
        for (rtspClient in rtspClients) {
            rtspClient.setAudioCodec(codec)
        }
    }

    fun startStream(type: MultiType, index: Int, url: String?) {
        var shouldStarEncoder = true
        for (rtmpClient in rtmpClients) {
            if (rtmpClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        for (rtspClient in rtspClients) {
            if (rtspClient.isStreaming) {
                shouldStarEncoder = false
                break
            }
        }
        if (shouldStarEncoder) super.startStream("")
        if (type == MultiType.RTMP) {
            if (videoEncoder.rotation == 90 || videoEncoder.rotation == 270) {
                rtmpClients[index].setVideoResolution(videoEncoder.height, videoEncoder.width)
            } else {
                rtmpClients[index].setVideoResolution(videoEncoder.width, videoEncoder.height)
            }
            rtmpClients[index].setFps(videoEncoder.fps)
            rtmpClients[index].connect(url)
        } else {
            rtspClients[index].connect(url)
        }
    }

    override fun startStreamRtp(url: String) {
    }

    fun stopStream(type: MultiType, index: Int) {
        var shouldStopEncoder = true
        for (rtmpClient in rtmpClients) {
            if (rtmpClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        for (rtspClient in rtspClients) {
            if (rtspClient.isStreaming) {
                shouldStopEncoder = false
                break
            }
        }
        if (type == MultiType.RTMP) {
            rtmpClients[index].disconnect()
        } else {
            rtspClients[index].disconnect()
        }
        if (shouldStopEncoder) super.stopStream()
    }

    override fun stopStreamRtp() {
    }

    override fun prepareAudioRtp(isStereo: Boolean, sampleRate: Int) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.setAudioInfo(sampleRate, isStereo)
        }
        for (rtspClient in rtspClients) {
            rtspClient.setAudioInfo(sampleRate, isStereo)
        }
    }

    override fun getAacDataRtp(aacBuffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.sendAudio(aacBuffer.duplicate(), info)
        }
        for (rtspClient in rtspClients) {
            rtspClient.sendAudio(aacBuffer.duplicate(), info)
        }
    }

    override fun onSpsPpsVpsRtp(sps: ByteBuffer, pps: ByteBuffer?, vps: ByteBuffer?) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.setVideoInfo(sps.duplicate(), pps?.duplicate(), vps?.duplicate())
        }
        for (rtspClient in rtspClients) {
            rtspClient.setVideoInfo(sps.duplicate(), pps?.duplicate(), vps?.duplicate())
        }
    }

    override fun getH264DataRtp(h264Buffer: ByteBuffer, info: MediaCodec.BufferInfo) {
        for (rtmpClient in rtmpClients) {
            rtmpClient.sendVideo(h264Buffer.duplicate(), info)
        }
        for (rtspClient in rtspClients) {
            rtspClient.sendVideo(h264Buffer.duplicate(), info)
        }
    }
}
