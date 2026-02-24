package com.google.android.play.core.splitinstall;
import java.util.ArrayList;
import java.util.List;
public class SplitInstallRequest {
    public List<String> getModuleNames() { return new ArrayList<>(); }
    public static class Builder {
        public Builder addModule(String moduleName) { return this; }
        public SplitInstallRequest build() { return new SplitInstallRequest(); }
    }
    public static Builder newBuilder() { return new Builder(); }
}
