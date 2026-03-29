package com.vardanrattan.echoes;

import com.vardanrattan.echoes.config.EchoesConfig;
import com.vardanrattan.echoes.command.EchoesCommand;
import com.vardanrattan.echoes.events.EchoesServerEvents;
import com.vardanrattan.echoes.events.PlaybackTriggerService;
import com.vardanrattan.echoes.events.RecordingSessionManager;
import com.vardanrattan.echoes.network.EchoNetworking;
import com.vardanrattan.echoes.item.EchoItems;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Echoes implements ModInitializer {
	public static final String MOD_ID = "echoes";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static RecordingSessionManager recordingSessionManager;
	private static PlaybackTriggerService playbackTriggerService;

	@Override
	public void onInitialize() {
		// Load configuration snapshot once at startup.
		EchoesConfig.load();

		playbackTriggerService = new PlaybackTriggerService();
		recordingSessionManager = new RecordingSessionManager();

		// Networking (CustomPacketPayload + StreamCodec).
		EchoNetworking.init(playbackTriggerService);

		// Register server-side event hooks.
		EchoesServerEvents.register(recordingSessionManager, playbackTriggerService);

		// Items
		EchoItems.register();

		// Commands
		EchoesCommand.registerCallback();

		LOGGER.info("Echoes initialized. Config enabled={}, version={}",
				EchoesConfig.get().isEnabled(),
				EchoesConfig.get().getConfigVersion());
	}

	public static RecordingSessionManager getRecordingSessionManager() {
		return recordingSessionManager;
	}

	public static PlaybackTriggerService getPlaybackTriggerService() {
		return playbackTriggerService;
	}
}
