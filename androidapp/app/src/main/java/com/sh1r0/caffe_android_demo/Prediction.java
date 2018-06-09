package com.sh1r0.caffe_android_demo;

/**
 * Created by peterma on 5/5/18.
 */

public class Prediction {
    private int[] _predictions;
    private float[] _confidences;

    public Prediction(int[] predictions, float[] confidences)
    {
        _predictions = predictions;
        _confidences = confidences;
    }

    public int[] getPredictions()
    {
        return _predictions;
    }

    public float[] getConfidence()
    {
        return _confidences;
    }

}
