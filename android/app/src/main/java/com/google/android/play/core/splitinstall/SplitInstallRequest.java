package com.google.android.play.core.splitinstall;

public class SplitInstallRequest {
    public static Builder newBuilder() { return new Builder(); }
    public static class Builder {
        public Builder addModule(String moduleName) { return this; }
        public SplitInstallRequest build() { return new SplitInstallRequest(); }
    }
}
