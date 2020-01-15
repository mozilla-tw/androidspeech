package com.mozilla.speechapp;

import android.Manifest;
import android.app.DownloadManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
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
    private static final int PERMISSION_REQUEST_CODE = 123;

    private static long sDownloadId;
    private static DownloadManager sDownloadManager;

    private MozillaSpeechService mMozillaSpeechService;
    private boolean mIsRecording = false;
    private FloatingActionButton mFab;
    private CursorAwareEditText outputText;
    private View mShareButton;
    private WaveVisualizer mVisualizer;
    private Stack<Integer> textLengths = new Stack<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_continuous_recognition);
        mMozillaSpeechService = MozillaSpeechService.getInstance();
        initialize();
    }

    String selectedWord = null;
    boolean autoSelectEnabled = true;

    private void initialize() {

        checkPermission();

        mMozillaSpeechService.addListener(this);

        mShareButton = findViewById(R.id.share_btn);
        mShareButton.setOnClickListener(view -> shareText(outputText.getText().toString()));

        mFab = findViewById(R.id.fab);
        mFab.setOnClickListener(view -> {
            if (mIsRecording) {
                stop();
                boolean hasText = !outputText.getText().toString().isEmpty();
                mShareButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
            } else {
                start();
                mShareButton.setVisibility(View.GONE);
            }
        });

        outputText = findViewById(R.id.output);
        outputText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                String afterText = s.toString();
                int start = outputText.getSelectionStart();
                int end = outputText.getSelectionEnd();
                if (start != -1 && end != -1) {
                    if (!afterText.substring(start, end).equals(selectedWord)) {
                        autoSelectEnabled = false;
                        new Handler().postDelayed(() -> autoSelectEnabled = true, 300);
                        outputText.setSelection(end, end);
                    }
                }
            }
        });
        outputText.setSelectionChangedListener((selStart, selEnd) -> {
            if (selStart != -1 && selStart == selEnd && autoSelectEnabled) {
                String text = outputText.getText().toString();
                Pair<Integer, Integer> pair = findWord(text, selStart);
                int start = pair.first;
                int end = pair.second;
                String word = text.substring(start, end);
                outputText.setSelection(start, end);
                selectedWord = word;
            }
        });

        mVisualizer = findViewById(R.id.blast);

        findViewById(R.id.clear_btn).setOnClickListener(view -> {
            textLengths.clear();
            outputText.setText("");
            mShareButton.setVisibility(View.GONE);
        });
        findViewById(R.id.go_to_bottom_btn).setOnClickListener(view -> {
            outputText.setSelection(outputText.getText().toString().length());
        });
        findViewById(R.id.reset_btn).setOnClickListener(view -> {
            if (!textLengths.empty()) {
                String currentText = outputText.getText().toString();
                outputText.setText(currentText.substring(0, currentText.length() - textLengths.pop()));
            }
            boolean hasText = !outputText.getText().toString().isEmpty();
            mShareButton.setVisibility(hasText ? View.VISIBLE : View.GONE);
        });
    }

    private void checkPermission() {
        if (!hasPermissions(this, Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
        }
    }

    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            for (int grantResult : grantResults) {
                if (grantResult != PackageManager.PERMISSION_GRANTED) {
                    checkPermission();
                    break;
                }
            }
        }
    }

    // Pair(start, end)
    private Pair<Integer, Integer> findWord(String text, int selectedIndex) {
        int start = selectedIndex;
        int end = selectedIndex;

        if (selectedIndex >= text.length() || text.charAt(selectedIndex) == ' ') {
            while(start > 0) {
                start--;
                if (text.charAt(start) == ' ' || text.charAt(start) == '\n') {
                    start++;
                    break;
                }
            }
        } else {
            while(start > 0) {
                start--;
                if (text.charAt(start) == ' ' || text.charAt(start) == '\n') {
                    start++;
                    break;
                }
            }
            do {
                end++;
            } while (end < text.length() && text.charAt(end) != ' ' && text.charAt(end) != '\n');
        }

        return new Pair<>(start, end);
    }

    private void shareText(String text) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, text);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, null);
        startActivity(shareIntent);
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
        private ProgressDialog dialog;

        public AsyncUnzip() {
            dialog = new ProgressDialog(ContinuousRecognitionActivity.this);
        }

        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Extracting downloaded model");
            dialog.setMessage("Extracting downloaded model");
            dialog.setCancelable(false);
            dialog.show();
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
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            mFab.setEnabled(true);
            start();
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
