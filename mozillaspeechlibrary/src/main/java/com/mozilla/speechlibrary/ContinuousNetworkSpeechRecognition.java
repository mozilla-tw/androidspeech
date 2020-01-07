package com.mozilla.speechlibrary;

import android.content.Context;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Process;

import com.github.axet.audiolibrary.encoders.Encoder;
import com.github.axet.audiolibrary.encoders.EncoderInfo;
import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.audiolibrary.encoders.FormatOPUS;
import com.github.axet.audiolibrary.encoders.Sound;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

class ContinuousNetworkSpeechRecognition implements Runnable {

    static final int AUDIO_OUTPUT = 1;

    Vad mVad;
    short[] mBuftemp;
    ByteArrayOutputStream baos ;
    int mMinimumVoice = 250;
    int mMaximumSilence = 1000;
    int mUpperLimit = 20;
    static final int FRAME_SIZE = 160;
    boolean cancelled;
    Context mContext;
    int mSampleRate;
    int mChannels;
    MozillaSpeechService mService;
    Networking network;
    NetworkSettings mNetworkSettings;
    Handler mAudioHandler;

    protected ContinuousNetworkSpeechRecognition(Looper looper, int aSampleRate, int aChannels, Vad aVad, Context aContext,
                                                 MozillaSpeechService aService, NetworkSettings mNetworkSettings) {
        this.mVad = aVad;
        this.mContext = aContext;
        this.mSampleRate = aSampleRate;
        this.mChannels = aChannels;
        this.mService = aService;
        this.mNetworkSettings = mNetworkSettings;
        network = new Networking(mService);
        this.mAudioHandler = new AudioHandler(looper, network, mNetworkSettings);
    }

    public void run() {
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);

            baos = new ByteArrayOutputStream();
            boolean batchProcess = false;
            long samplesvoice = 0 ;
            long samplessilence = 0 ;
            boolean touchedvoice = false;
            boolean touchedsilence = false;
            int vad = 0;
            long dtantes = System.currentTimeMillis();
            long dtantesmili = 	System.currentTimeMillis();
            boolean raisenovoice = false;
            network.mContext = this.mContext;
            AudioRecord recorder = Sound.getAudioRecord(mChannels, mSampleRate);
            EncoderInfo ef = new EncoderInfo(1, mSampleRate, 16);
            Encoder e = Factory.getEncoder(mContext, FormatOPUS.EXT, ef, baos);

            recorder.startRecording();
            mService.notifyListeners(MozillaSpeechService.SpeechState.START_LISTEN, null);

            while (!this.cancelled) {
                int nshorts = 0 ;

                try {
                    mBuftemp = new short[FRAME_SIZE * mChannels * 2];
                    nshorts = recorder.read(mBuftemp, 0, mBuftemp.length);
                    vad = mVad.feed(mBuftemp, nshorts);
                    e.encode(mBuftemp, 0, nshorts);
                    double[] fft =  Sound.fft(mBuftemp, 0, nshorts);
                    double fftsum = Arrays.stream(fft).sum()/fft.length;
                    mService.notifyListeners(MozillaSpeechService.SpeechState.MIC_ACTIVITY, fftsum);
                }
                catch (Exception exc) {
                    exc.printStackTrace();
                }

                long dtdepois = System.currentTimeMillis();

                if (vad == 0) {
                    if (touchedvoice) {
                        samplessilence += dtdepois - dtantesmili;
                        if (samplessilence >  mMaximumSilence) touchedsilence = true;
                    }
                }
                else {
                    samplesvoice  += dtdepois - dtantesmili;
                    if (samplesvoice >  mMinimumVoice) touchedvoice = true;
                }
                dtantesmili = dtdepois;

                if (touchedvoice && touchedsilence)
                    batchProcess = true;

                if (batchProcess || touchedvoice && ((dtdepois - dtantes) / 1000 > mUpperLimit)) {
                    notifyAudioOutput(baos);

                    baos = new ByteArrayOutputStream();
                    e = Factory.getEncoder(mContext, FormatOPUS.EXT, ef, baos);
                    batchProcess = false;
                    samplesvoice = 0 ;
                    samplessilence = 0 ;
                    touchedvoice = false;
                    touchedsilence = false;
                    vad = 0;
                    dtantes = System.currentTimeMillis();
                    dtantesmili = System.currentTimeMillis();
                }

                if (nshorts <= 0)
                    break;
            }

            e.close();
            mVad.stop();
            recorder.stop();
            recorder.release();

            if (raisenovoice) mService.notifyListeners(MozillaSpeechService.SpeechState.NO_VOICE, null);

            if (cancelled) {
                cancelled = false;
                mService.notifyListeners(MozillaSpeechService.SpeechState.CANCELED, null);
                return;
            }
        }
        catch (Exception exc)
        {
            String error = String.format("General audio error %s", exc.getMessage());
            mService.notifyListeners(MozillaSpeechService.SpeechState.ERROR, error);
            exc.printStackTrace();
        }
    }

    private void notifyAudioOutput(ByteArrayOutputStream baos) {
        Message message = new Message();
        message.what = AUDIO_OUTPUT;
        message.obj = baos;
        mAudioHandler.sendMessage(message);
    }

    public void cancel(){
        cancelled = true;
        mVad.stop();
        network.cancel();
    }

    private static class AudioHandler extends Handler {
        private Networking mNetwork;
        private NetworkSettings mNetworkSettings;

        AudioHandler(Looper looper, Networking mNetwork, NetworkSettings mNetworkSettings) {
            super(looper);
            this.mNetwork = mNetwork;
            this.mNetworkSettings = mNetworkSettings;
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what == ContinuousNetworkSpeechRecognition.AUDIO_OUTPUT) {
                startProcessing((ByteArrayOutputStream) msg.obj);
            }
        }

        private void startProcessing(final ByteArrayOutputStream baos) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    mNetwork.doSTT(baos, mNetworkSettings);
                }
            }).start();
        }
    }
}
