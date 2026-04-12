package com.vardanrattan.echoes;

import com.vardanrattan.echoes.network.EchoNetworking;
import com.vardanrattan.echoes.network.EchoClientNetworking;
import net.fabricmc.api.ClientModInitializer;

public class EchoesClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register payload types and (later) client-side receivers.
		EchoNetworking.initClient();
		EchoClientNetworking.init();
	}
}