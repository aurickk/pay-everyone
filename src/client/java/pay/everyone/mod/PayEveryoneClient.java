package pay.everyone.mod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PayEveryoneClient implements ClientModInitializer {
	// Keybind to stop payments (default: K key)
	private static KeyMapping stopPaymentKey;
	
	@Override
	public void onInitializeClient() {
		// Register client-side commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			PayCommands.register(dispatcher);
		});
		
		// Register keybind for stopping payments
		stopPaymentKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
			"key.payeveryone.stop", // Translation key
			InputConstants.Type.KEYSYM,
			GLFW.GLFW_KEY_K, // Default key: K
			"category.payeveryone" // Category translation key
		));
		
		// Register tick event to check for keybind press
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (stopPaymentKey.consumeClick()) {
				PayManager payManager = PayManager.getInstance();
				
				// Stop payment if in progress
				if (payManager.isPaying()) {
					payManager.stopPaying();
					if (client.player != null) {
						client.player.displayClientMessage(
							Component.literal("§c[Pay Everyone] Payment stopped via keybind!"), 
							false
						);
					}
				}
				// Also stop tabscan if in progress
				else if (payManager.isTabScanning()) {
					payManager.stopTabScan();
					if (client.player != null) {
						client.player.displayClientMessage(
							Component.literal("§c[Pay Everyone] Tab scan stopped via keybind!"), 
							false
						);
					}
				}
			}
		});
	}
}
