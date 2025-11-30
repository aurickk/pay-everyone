package pay.everyone.mod;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;

public class PayEveryoneClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register client-side commands
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			PayCommands.register(dispatcher);
		});
	}
}