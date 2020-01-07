package com.mozilla.speechlibrary;

import android.content.Context;
import android.os.Looper;
import android.util.Log;

import java.util.ArrayList;

public class MozillaSpeechService {

    protected static final String TAG = "MozillaSpeech";
    private final int SAMPLERATE = 16000;
    private final int CHANNELS = 1;
    private ArrayList<ISpeechRecognitionListener> mListeners;
    private Context mContext;
    private boolean isIdle = true;
    NetworkSettings mNetworkSettings;
    private boolean useDeepSpeech = false;
    private boolean continuousMode = false;
    private String mModelPath;

    public enum SpeechState
    {
        DECODING, MIC_ACTIVITY, STT_RESULT, START_LISTEN,
        NO_VOICE, CANCELED, ERROR
    }

    private static final MozillaSpeechService ourInstance = new MozillaSpeechService();
    private NetworkSpeechRecognition mNetworkSpeechRecognition;
    private LocalSpeechRecognition mLocalSpeechRecognition;
    private ContinuousNetworkSpeechRecognition mContinuousNetworkSpeechRecognition;
    private SpeechState mState;
    private Vad mVad;

    public static MozillaSpeechService getInstance() {
        return ourInstance;
    }

    private MozillaSpeechService() {
        mVad = new Vad();
        mNetworkSettings = new NetworkSettings();
    }

    public void start(Context aContext) {

        try {
            if (!isIdle) {
                notifyListeners(SpeechState.ERROR, "Recognition already In progress");
            } else {
                int retVal = mVad.start();
                this.mContext = aContext;
                if (retVal < 0) {
                    notifyListeners(SpeechState.ERROR, "Error Initializing VAD " + String.valueOf(retVal));
                } else {
                    Thread audio_thread;

                    if (this.continuousMode) {
                        this.mContinuousNetworkSpeechRecognition = new ContinuousNetworkSpeechRecognition(Looper.myLooper(), SAMPLERATE, CHANNELS, mVad, aContext, this, mNetworkSettings);
                        audio_thread = new Thread(this.mContinuousNetworkSpeechRecognition);
                    } else if (this.useDeepSpeech) {
                        this.mLocalSpeechRecognition = new LocalSpeechRecognition(SAMPLERATE, CHANNELS, mVad, this);
                        audio_thread = new Thread(this.mLocalSpeechRecognition);
                    } else {
                        this.mNetworkSpeechRecognition = new NetworkSpeechRecognition(SAMPLERATE, CHANNELS, mVad, aContext, this, mNetworkSettings);
                        audio_thread = new Thread(this.mNetworkSpeechRecognition);
                    }

                    audio_thread.start();
                    isIdle = false;
                }
            }
        } catch (Exception exc) {
            Log.e("MozillaSpeechService", "General error loading the module: " + exc);
            notifyListeners(SpeechState.ERROR, "General error loading the module: " + exc);
        }
    }

    public void addListener(ISpeechRecognitionListener aListener) {
        if (mListeners == null) {
            mListeners = new ArrayList<>();
        }
        mListeners.add(aListener);
    }

    protected void notifyListeners(MozillaSpeechService.SpeechState aState, Object aPayload) {
        if (aState == SpeechState.STT_RESULT || aState == SpeechState.ERROR
                || aState == SpeechState.NO_VOICE || aState == SpeechState.CANCELED) {
            isIdle = true;
        }
        mState = aState;
        for (ISpeechRecognitionListener listener : mListeners) {
            listener.onSpeechStatusChanged(aState, aPayload);
        }
    }

    public void cancel() {
        if (this.mNetworkSpeechRecognition != null) {
            this.mNetworkSpeechRecognition.cancel();
        }
        if (this.mLocalSpeechRecognition != null) {
            this.mLocalSpeechRecognition.cancel();
        }
        if (this.mContinuousNetworkSpeechRecognition != null) {
            this.mContinuousNetworkSpeechRecognition.cancel();
        }
    }

    public void removeListener(ISpeechRecognitionListener aListener) {
        if (mListeners != null) {
            mListeners.remove(aListener);
        }
    }

    public void storeSamples(boolean yesOrNo) {
        this.mNetworkSettings.mStoreSamples = yesOrNo;
    }

    public void storeTranscriptions(boolean yesOrNo) {
        this.mNetworkSettings.mStoreTranscriptions = yesOrNo;
    }

    public void setLanguage(String language) {
        this.mNetworkSettings.mLanguage = language;
    }

    public String getLanguageDir() {
        return LocalSpeechRecognition.getLanguageDir(this.mNetworkSettings.mLanguage);
    }

    public void useDeepSpeech(boolean yesOrNo) {
        this.useDeepSpeech = yesOrNo;
    }

    public void setContinuousMode(boolean yesOrNo) {
        this.continuousMode = yesOrNo;
    }

    public String getModelPath() {
        return this.mModelPath;
    }

    // This sets model's root path, not including the language
    public void setModelPath(String aModelPath) {
        this.mModelPath = aModelPath;
    }

    public boolean ensureModelInstalled() {
        return LocalSpeechRecognition.ensureModelInstalled(this.getModelPath() + "/" + this.getLanguageDir());
    }

    public String getModelDownloadURL() {
        return LocalSpeechRecognition.getModelDownloadURL(this.getLanguageDir());
    }

    public void setProductTag(String tag) {
        this.mNetworkSettings.mProductTag = tag;
    }

}
