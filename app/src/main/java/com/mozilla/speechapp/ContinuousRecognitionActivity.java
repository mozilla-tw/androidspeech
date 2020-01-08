package com.mozilla.speechapp;

import android.Manifest;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.TextView;
import android.widget.Toast;

import com.gauravk.audiovisualizer.visualizer.WaveVisualizer;
import com.mozilla.speechlibrary.ISpeechRecognitionListener;
import com.mozilla.speechlibrary.MicData;
import com.mozilla.speechlibrary.MozillaSpeechService;
import com.mozilla.speechlibrary.STTResult;
import com.mozilla.speechmodule.R;

import net.lingala.zip4j.core.ZipFile;

import java.io.File;
import java.util.Stack;

public class ContinuousRecognitionActivity extends AppCompatActivity implements ISpeechRecognitionListener, CompoundButton.OnCheckedChangeListener {

    private static final String TAG = "ContinuousRecognitionActivity";
    private static final String LANG = "en-US";

    private static long sDownloadId;
    private static DownloadManager sDownloadManager;

    private MozillaSpeechService mMozillaSpeechService;
    private boolean mIsRecording = false;
    private FloatingActionButton mFab;
    private TextView outputText;
    private WaveVisualizer mVisualizer;
    private Stack<Integer> textLengths = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_recognition);
        mMozillaSpeechService = MozillaSpeechService.getInstance();
        initialize();
    }

    private void initialize() {

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO},
                    123);
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    124);
        }

        mMozillaSpeechService.addListener(this);

        mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(view -> {
            if (mIsRecording) {
                stop();
            } else {
                start();
            }
        });

        outputText = findViewById(R.id.output);
        outputText.setMovementMethod(new ScrollingMovementMethod());

        mVisualizer = findViewById(R.id.blast);

        findViewById(R.id.clear_btn).setOnClickListener(view -> {
            textLengths.clear();
            outputText.setText("");
        });
        findViewById(R.id.reset_btn).setOnClickListener(view -> {
            if (!textLengths.empty()) {
                String currentText = outputText.getText().toString();
                outputText.setText(currentText.substring(0, currentText.length() - textLengths.pop()));
            }
        });
    }

    private void start() {
        try {
            mMozillaSpeechService.setLanguage(LANG);
            mMozillaSpeechService.setProductTag("ContinuousRecognition");
            mMozillaSpeechService.setModelPath(getExternalFilesDir("models").getAbsolutePath());
            mMozillaSpeechService.setContinuousMode(true);
            if (mMozillaSpeechService.ensureModelInstalled()) {
                mIsRecording = true;
                mFab.setImageResource(R.drawable.ic_stop);
                mMozillaSpeechService.start(getApplicationContext());
            } else {
                maybeDownloadOrExtractModel(getExternalFilesDir("models").getAbsolutePath(), mMozillaSpeechService.getLanguageDir());
            }
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    private void stop() {
        mIsRecording = false;
        mFab.setImageResource(R.drawable.ic_mic);
        try {
            mMozillaSpeechService.cancel();
        } catch (Exception e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    public void onSpeechStatusChanged(MozillaSpeechService.SpeechState aState, Object aPayload){
        this.runOnUiThread(() -> {
            switch (aState) {
                case DECODING:
                    Log.d(TAG, "Decoding...");
                    break;
                case MIC_ACTIVITY:
                    MicData micData = (MicData) aPayload;
                    Log.d(TAG, "bytes: size: " + micData.getBytes().length);
                    mVisualizer.setRawAudioBytes(micData.getBytes());
                    break;
                case STT_RESULT:
                    String text = ((STTResult)aPayload).mTranscription;
                    float confidence = ((STTResult)aPayload).mConfidence;
                    Log.d(TAG, String.format("Success: %s (%s)", text, confidence));
                    String appendText = text + " ";
                    outputText.append(appendText);
                    textLengths.push(appendText.length());
                    break;
                case START_LISTEN:
                    Log.d(TAG, "Started to listen");
                    break;
                case NO_VOICE:
                    Log.d(TAG, "No Voice detected");
                    break;
                case CANCELED:
                    Log.d(TAG, "Canceled");
                    stop();
                    break;
                case ERROR:
                    Log.d(TAG, "Error:" + aPayload);
                    break;
                default:
                    break;
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stop();
        removeListener();
        if (mVisualizer != null) {
            mVisualizer.release();
        }
    }

    public void removeListener() {
        mMozillaSpeechService.removeListener(this);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (buttonView.equals(findViewById(R.id.switchTranscriptions))) {
            mMozillaSpeechService.storeTranscriptions(isChecked);
        } else if (buttonView.equals(findViewById(R.id.switchSamples))) {
            mMozillaSpeechService.storeSamples(isChecked);
        } else if (buttonView.equals(findViewById(R.id.useDeepSpeech))) {
            mMozillaSpeechService.useDeepSpeech(isChecked);
        }
    }

    private class AsyncUnzip extends AsyncTask<String, Void, Boolean> {

        @Override
        protected void onPreExecute() {
            Toast noModel = Toast.makeText(getApplicationContext(), "Extracting downloaded model", Toast.LENGTH_LONG);
            Log.d(TAG, "Extracting downloaded model");
            noModel.show();
        }

        @Override
        protected Boolean doInBackground(String...params) {
            String aZipFile = params[0], aRootModelsPath = params[1];
            try {
                ZipFile zf = new ZipFile(aZipFile);
                zf.extractAll(aRootModelsPath);
            } catch (Exception e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }

            return (new File(aZipFile)).delete();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            mMozillaSpeechService.start(getApplicationContext());
            mFab.setEnabled(true);
        }

    }

    public void maybeDownloadOrExtractModel(String aModelsPath, String aLang) {
        String zipFile   = aModelsPath + "/" + aLang + ".zip";
        String aModelPath= aModelsPath + "/" + aLang + "/";

        File aModelFolder = new File(aModelPath);
        if (!aModelFolder.exists()) {
            aModelFolder.mkdirs();
        }

        Uri modelZipURL  = Uri.parse(mMozillaSpeechService.getModelDownloadURL());
        Uri modelZipFile = Uri.parse("file://" + zipFile);

        mFab.setEnabled(false);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
                    long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);
                    DownloadManager.Query query = new DownloadManager.Query();
                    query.setFilterById(downloadId);
                    Cursor c = sDownloadManager.query(query);
                    if (c.moveToFirst()) {
                        int columnIndex = c.getColumnIndex(DownloadManager.COLUMN_STATUS);
                        if (DownloadManager.STATUS_SUCCESSFUL == c.getInt(columnIndex)) {
                            Log.d(TAG, "Download successfull");

                            new AsyncUnzip().execute(zipFile, aModelPath);
                        }
                    }
                }
            }
        };

        Toast noModel = Toast.makeText(getApplicationContext(), "No model has been found for language '" + aLang + "'. Triggering download ...", Toast.LENGTH_LONG);
        Log.d(TAG, "No model has been found for language '" + aLang + "'. Triggering download ...");
        noModel.show();

        sDownloadManager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(modelZipURL);
        request.setTitle("DeepSpeech " + aLang);
        request.setDescription("DeepSpeech Model");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setVisibleInDownloadsUi(false);
        request.setDestinationUri(modelZipFile);
        sDownloadId = sDownloadManager.enqueue(request);

        getApplicationContext().registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }
}
