package com.mozilla.speechlibrary;

public class MicData {
    private double fftsum;
    private byte[] bytes;

    public MicData(double fftsum, byte[] bytes) {
        this.fftsum = fftsum;
        this.bytes = bytes;
    }

    public double getFftsum() {
        return fftsum;
    }

    public void setFftsum(double fftsum) {
        this.fftsum = fftsum;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }
}
