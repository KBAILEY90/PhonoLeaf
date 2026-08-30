package com.phonoleaf.app;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Register native plugins before the bridge starts.
        registerPlugin(PhonoLeafTtsPlugin.class);
        registerPlugin(SecureStoragePlugin.class); // Keystore-backed storage for pl_rtoken
        registerPlugin(EmailComposerPlugin.class); // direct-to-mail-app compose, bypassing the generic share chooser
        registerPlugin(LocalFolderPlugin.class); // SAF folder connect + manual refresh for LocalBooks
        registerPlugin(StoreReviewPlugin.class); // Play In-App Review prompt after finishing a book
        super.onCreate(savedInstanceState);
    }
}
