package com.practice.camera2view;

public class Config {
    int previewWidth;
    int previewHeight;
    int rotation;
    int cameraId;
    boolean fast;

    public Config() {
    }

    private Config(Builder builder) {
        setPreviewWidth(builder.previewWidth);
        setPreviewHeight(builder.previewHeight);
        setRotation(builder.rotation);
        setCameraId(builder.cameraId);
        setFast(builder.fast);
    }

    public boolean isFast() {
        return fast;
    }

    public void setFast(boolean fast) {
        this.fast = fast;
    }

    public int getPreviewWidth() {
        return previewWidth;
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    public int getPreviewHeight() {
        return previewHeight;
    }

    public void setPreviewHeight(int previewHeight) {
        this.previewHeight = previewHeight;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation;
    }

    public int getCameraId() {
        return cameraId;
    }

    public void setCameraId(int cameraId) {
        this.cameraId = cameraId;
    }

    public static final class Builder {
        private int previewWidth;
        private int previewHeight;
        private int rotation;
        private int cameraId;
        private boolean fast;

        public Builder() {
        }

        public Builder previewWidth(int val) {
            previewWidth = val;
            return this;
        }

        public Builder previewHeight(int val) {
            previewHeight = val;
            return this;
        }

        public Builder rotation(int val) {
            rotation = val;
            return this;
        }

        public Builder cameraId(int val) {
            cameraId = val;
            return this;
        }

        public Builder fast(boolean val) {
            fast = val;
            return this;
        }

        public Config build() {
            return new Config(this);
        }
    }
}
