package com.phonoleaf.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register native plugins before the bridge starts.
        registerPlugin(PhonoLeafTtsPlugin.class);
        registerPlugin(SecureStoragePlugin.class); // Keystore-backed storage for pl_rtoken
        super.onCreate(savedInstanceState);
    }
}
