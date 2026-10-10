package com.vws.createfactorygraph.world;

import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** True on the dedicated or integrated server thread. Client threads run the original Create code. */
public final class ServerThread {
    public static boolean isServer() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        return server != null && server.isSameThread();
    }

    private ServerThread() {}
}
