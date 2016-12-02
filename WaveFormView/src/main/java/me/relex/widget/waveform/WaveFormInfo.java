package me.relex.widget.waveform;

import java.util.List;

public class WaveFormInfo {

    //采样率（每秒采样数)
    private int sample_rate;
    //每像素点的采样数
    private int samples_per_pixel;
    private int bits;
    //像素长度，data长度一半
    private int length;

    private List<Integer> data;

    public int getSample_rate() {
        return sample_rate;
    }

    public void setSample_rate(int sample_rate) {
        this.sample_rate = sample_rate;
    }

    public int getSamples_per_pixel() {
        return samples_per_pixel;
    }

    public void setSamples_per_pixel(int samples_per_pixel) {
        this.samples_per_pixel = samples_per_pixel;
    }

    public int getBits() {
        return bits;
    }

    public void setBits(int bits) {
        this.bits = bits;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public List<Integer> getData() {
        return data;
    }

    public void setData(List<Integer> data) {
        this.data = data;
    }
}
