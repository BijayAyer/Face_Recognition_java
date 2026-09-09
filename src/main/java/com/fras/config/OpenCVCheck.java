package com.fras.config;

import nu.pattern.OpenCV;
import org.opencv.core.Mat;
import org.opencv.core.CvType;

public class OpenCVCheck {
    public static void main(String[] args) {
        OpenCV.loadLocally();
        Mat mat = Mat.eye(3, 3, CvType.CV_8UC1);
        System.out.println("OpenCV loaded successfully:\n" + mat.dump());
    }
}