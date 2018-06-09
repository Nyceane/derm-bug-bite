package com.sh1r0.caffe_android_demo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.sh1r0.caffe_android_lib.CaffeMobile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Scanner;


public class MainActivity extends AppCompatActivity
        implements CNNListener, ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String LOG_TAG = "MainActivity";
    private static final int REQUEST_IMAGE_CAPTURE = 100;
    private static final int REQUEST_IMAGE_SELECT = 200;
    public static final int MEDIA_TYPE_IMAGE = 1;
    private static String[] IMAGENET_CLASSES;

    private Button btnCamera;
    private Button btnSelect;
    private Button btnTokbox;
    private ImageView ivCaptured;
    private TextView tvLabel;
    private TextView tvConfidence;
    private Uri fileUri;
    private ProgressDialog dialog;
    private Bitmap bmp;
    private CaffeMobile caffeMobile;
    File sdcard = Environment.getExternalStorageDirectory();
    String modelDir = sdcard.getAbsolutePath() + "/caffe_mobile/bitenet";
    String modelProto = modelDir + "/deploy.prototxt";
    String modelBinary = modelDir + "/caffe_model_1_iter_1000.caffemodel";

    static {
        System.loadLibrary("caffe");
        System.loadLibrary("caffe_jni");
    }

    private void initCaffe() {
        caffeMobile = new CaffeMobile();
        caffeMobile.setNumThreads(4);
        caffeMobile.enableLog(true);
        caffeMobile.loadModel(modelProto, modelBinary);

        float[] meanValues = {134, 138, 137};
        caffeMobile.setMean(meanValues);

        AssetManager am = this.getAssets();
        try {
            InputStream is = am.open("synset_words.txt");
            Scanner sc = new Scanner(is);
            List<String> lines = new ArrayList<String>();
            while (sc.hasNextLine()) {
                final String temp = sc.nextLine();
                lines.add(temp);
            }
            IMAGENET_CLASSES = lines.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final int PERMISSION_REQUEST_CODE = 1;
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.v("value", "Permission is now granted!");
            initCaffe();
        }
    }

    private String imgPath = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().requestFeature(Window.FEATURE_ACTION_BAR);
        getSupportActionBar().hide();

        setContentView(R.layout.activity_main);


        if(this.getIntent().hasExtra("imgPath"))
        {
            imgPath = this.getIntent().getStringExtra("imgPath");
        }
        else
        {
            finish();
        }

        ivCaptured = (ImageView) findViewById(R.id.ivCaptured);
        ivCaptured.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, DetailActivity.class);
                MainActivity.this.startActivity(intent);

            }
        });

        tvLabel = (TextView) findViewById(R.id.tvLabel);
        tvConfidence = (TextView) findViewById(R.id.tvConfidence);

        btnTokbox = (Button) findViewById(R.id.btnTokbox);
        btnTokbox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, ThermoActivity.class);
                MainActivity.this.startActivity(intent);
            }
        });

        btnCamera = (Button) findViewById(R.id.btnCamera);
        btnCamera.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                fileUri = getOutputMediaFileUri(MEDIA_TYPE_IMAGE);
                Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                i.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
                startActivityForResult(i, REQUEST_IMAGE_CAPTURE);
            }
        });

        btnSelect = (Button) findViewById(R.id.btnSelect);
        btnSelect.setOnClickListener(new Button.OnClickListener() {
            public void onClick(View v) {
                initPrediction();
                Intent i = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(i, REQUEST_IMAGE_SELECT);
            }
        });

        // TODO: implement a splash screen(?)

        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.v("value", "Permission is granted");
                initCaffe();
            } else {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            }
        } else {
            // Code for Below 23 API Oriented Device
            // Do next code
            initCaffe();
        }

        init();
    }

    private void init()
    {
        bmp = BitmapFactory.decodeFile(imgPath);
        Log.d(LOG_TAG, imgPath);
        Log.d(LOG_TAG, String.valueOf(bmp.getHeight()));
        Log.d(LOG_TAG, String.valueOf(bmp.getWidth()));

        dialog = ProgressDialog.show(MainActivity.this, "Predicting...", "Wait for one sec...", true);

        CNNTask cnnTask = new CNNTask(MainActivity.this);
        cnnTask.execute(imgPath);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((requestCode == REQUEST_IMAGE_CAPTURE || requestCode == REQUEST_IMAGE_SELECT) && resultCode == RESULT_OK) {
            String imgPath;

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                imgPath = fileUri.getPath();
            } else {
                Uri selectedImage = data.getData();
                String[] filePathColumn = {MediaStore.Images.Media.DATA};
                Cursor cursor = MainActivity.this.getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                cursor.moveToFirst();
                int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                imgPath = cursor.getString(columnIndex);
                cursor.close();
            }

            bmp = BitmapFactory.decodeFile(imgPath);
            Log.d(LOG_TAG, imgPath);
            Log.d(LOG_TAG, String.valueOf(bmp.getHeight()));
            Log.d(LOG_TAG, String.valueOf(bmp.getWidth()));

            dialog = ProgressDialog.show(MainActivity.this, "Predicting...", "Wait for one sec...", true);

            CNNTask cnnTask = new CNNTask(MainActivity.this);
            cnnTask.execute(imgPath);
        } else {
            btnCamera.setEnabled(true);
            btnSelect.setEnabled(true);
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private void initPrediction() {
        btnCamera.setEnabled(false);
        btnSelect.setEnabled(false);
       // tvLabel.setText("");
    }

    private class CNNTask extends AsyncTask<String, Void, Prediction> {
        private CNNListener listener;
        private long startTime;

        public CNNTask(CNNListener listener) {
            this.listener = listener;
        }

        @Override
        protected Prediction doInBackground(String... strings) {
            startTime = SystemClock.uptimeMillis();
            Prediction ret = new Prediction(caffeMobile.predictImage(strings[0], 5), caffeMobile.getConfidenceScore(strings[0]));
            return ret;
        }

        @Override
        protected void onPostExecute(Prediction integer) {
            Log.i(LOG_TAG, String.format("elapsed wall time: %d ms", SystemClock.uptimeMillis() - startTime));
            listener.onTaskCompleted(integer);
            super.onPostExecute(integer);
        }
    }

    @Override
    public void onTaskCompleted(Prediction result) {
        ivCaptured.setImageBitmap(bmp);

        String label = "";
        String label2 = "";
        for (int i = 0; i < 4; i++) {
            label += IMAGENET_CLASSES[result.getPredictions()[i]];
            label += "\n";
        }

        Float confidence[] = new Float[result.getConfidence().length];

        for (int i = 0; i < confidence.length; i++)
        {
            confidence[i] = result.getConfidence()[i];
        }

        Arrays.sort(confidence, Collections.reverseOrder());

        for(int i = 0; i < 4; i++)
        {
            int percentage = (int) Math.ceil(confidence[i] * 100);
            label2 += String.valueOf(percentage) + "%";
            //label2 += String.valueOf(confidence[i]);
            label2 += "\n";
        }


        tvLabel.setText(label);
        tvConfidence.setText(label2);
        btnCamera.setEnabled(true);
        btnSelect.setEnabled(true);

        if (dialog != null) {
            dialog.dismiss();
        }
    }

    /**
     * Create a file Uri for saving an image or video
     */
    private static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "Caffe-Android-Demo");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else {
            return null;
        }

        return mediaFile;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
