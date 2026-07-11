package com.phonoleaf.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register the native Kokoro TTS plugin before the bridge starts.
        registerPlugin(PhonoLeafTtsPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
