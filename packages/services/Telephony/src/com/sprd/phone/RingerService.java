package com.sprd.phone;

import java.io.IOException;
import com.android.internal.telephony.IRingerService;

import com.sprd.phone.AsyncRingtonePlayer;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.provider.Settings;

public class RingerService extends Service {

    private static final int MSG_START_PLAY = 1;
    private static final int MSG_STOP_PLAY = 2;
    // SPRD: Fade down ringtone to vibrate.
    private static final int MSG_FADEDOWN_PLAY = 3;
    // SPRD: MaxRingingVolume and Vibrate
    private static final int MSG_MAX_RINGING_VOLUME = 4;
    private AsyncRingtonePlayer player;
    /**
     * SPRD: Local Player that to make sure ringtone uri can be played.
     */
    private MediaPlayer mLocalPlayer;

    public static final String SERVICE_INTERFACE = "android.telephony.RingerService";

    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_START_PLAY:
                    Uri ringtone = (Uri)msg.obj;
                    // SPRD: try opening uri locally before delegating to remote player
                    destroyLocalPlayer();
                    mLocalPlayer = new MediaPlayer();
                    try {
                        mLocalPlayer.setDataSource(getApplicationContext(), ringtone);
                    } catch (SecurityException | IOException e) {
                        String uriString = Settings.System.getString(getApplicationContext().getContentResolver(),
                                Settings.System.DEFAULT_RINGTONE);
                        ringtone = (uriString != null) ? Uri.parse(uriString) : null;
                        destroyLocalPlayer();
                    }
                    player.play(ringtone);
                    break;
                case MSG_STOP_PLAY:
                    player.stop();
                    break;
                /* SPRD: Fade down ringtone to vibrate. @{ */
                case MSG_FADEDOWN_PLAY:
                    player.fadeDownRingtone();
                    break;
                /* @} */
                /* SPRD: MaxRingingVolume and Vibrate @{ */
                case MSG_MAX_RINGING_VOLUME:
                    player.handleMaxRingingVolume();
                    break;
                default:
                    break;
            }
        }
    };

    private final class RingerServiceBinder extends IRingerService.Stub {
        public void play(Uri ringtone) {
            mHandler.obtainMessage(MSG_START_PLAY, ringtone).sendToTarget();
        }
        public void stop() {
            mHandler.obtainMessage(MSG_STOP_PLAY).sendToTarget();
        }
        /* SPRD: Fade down ringtone to vibrate. @{ */
        public void fadeDownRingtone() {
            mHandler.obtainMessage(MSG_FADEDOWN_PLAY).sendToTarget();
        }
        /* SPRD: MaxRingingVolume and Vibrate @{ */
        public void maxRingingVolume() {
            mHandler.obtainMessage(MSG_MAX_RINGING_VOLUME).sendToTarget();
        }
        /* @} */
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (player == null) {
            player = new AsyncRingtonePlayer(this);
        }
        return new RingerServiceBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (player != null) {
            player.stop();
            player = null;
        }
        return false;
    }
    private void destroyLocalPlayer() {
        if (mLocalPlayer != null) {
            mLocalPlayer.reset();
            mLocalPlayer.release();
            mLocalPlayer = null;
        }
    }
}

