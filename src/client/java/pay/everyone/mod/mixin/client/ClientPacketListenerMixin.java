package pay.everyone.mod.mixin.client;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pay.everyone.mod.PayManager;

import java.util.ArrayList;
import java.util.List;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {
    @Inject(at = @At("HEAD"), method = "handleSystemChat")
    private void onSystemChat(ClientboundSystemChatPacket packet, CallbackInfo ci) {
        Component message = packet.content();
        if (message != null) {
            String messageText = message.getString();
            
            // Check if this is a /list command response
            PayManager.getInstance().handleListCommandResponse(messageText);
        }
    }

    @Inject(at = @At("HEAD"), method = "handleCommandSuggestions")
    private void onCommandSuggestions(ClientboundCommandSuggestionsPacket packet, CallbackInfo ci) {
        // Check if we're waiting for player suggestions from tabscan
        if (PayManager.getInstance().isTabScanning()) {
            List<String> playerNames = new ArrayList<>();
            
            // Get the request ID from the packet
            int requestId = packet.id();
            
            // Extract suggestions from the packet entries
            for (ClientboundCommandSuggestionsPacket.Entry entry : packet.suggestions()) {
                String text = entry.text();
                if (text != null && !text.isEmpty()) {
                    playerNames.add(text);
                }
            }
            
            PayManager.getInstance().handleTabCompletionResponse(requestId, playerNames);
        }
    }
    
    @Inject(at = @At("TAIL"), method = "handleOpenScreen")
    private void onOpenScreen(ClientboundOpenScreenPacket packet, CallbackInfo ci) {
        // Check if auto-confirm is enabled and we're paying
        if (PayManager.getInstance().isAutoConfirmEnabled()) {
            int containerId = packet.getContainerId();
            PayManager.getInstance().handleContainerOpened(containerId);
        }
    }
}

