package com.techmonkey.raildirector.client;

import net.fabricmc.api.ClientModInitializer;

public final class RailDirectorClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RailRouteOverlay.register();
    }
}
