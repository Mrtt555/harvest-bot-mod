package Mrtt555;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

/**
 * Gestion des contrôles clavier pour le bot de farming intégré
 */
public class HarvestBotKeybindings implements ClientModInitializer {
    
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("HarvestBotKeybindings");

    // Keybindings pour le bot
    private static KeyBinding startBotKey;
    private static KeyBinding stopBotKey;
    private static KeyBinding setCornerKey;
    private static KeyBinding toggleHudKey;
    private static KeyBinding increaseDelayKey;
    private static KeyBinding decreaseDelayKey;
    private static KeyBinding resetStateKey;
    
    // État pour le cycling des coins
    private static int currentCornerIndex = 0;
    
    // Anti-spam pour les keybindings
    private static long lastKeyPressTime = 0;
    private static final long KEY_PRESS_COOLDOWN = 100; // 100ms cooldown

    @Override
    public void onInitializeClient() {
        LOGGER.info("Initializing HarvestBot Keybindings...");

        // Enregistrer les keybindings
        registerKeyBindings();

        // Enregistrer les événements de tick pour gérer les keybindings
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            handleKeyBindings();
        });

        LOGGER.info("HarvestBot Keybindings initialized successfully");
    }

    /**
     * Enregistre tous les keybindings du bot
     */
    private void registerKeyBindings() {
        // Start Bot
        startBotKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.start_bot",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6, // F6 par défaut
                "category.player-api-mod.harvest"
        ));

        // Stop Bot
        stopBotKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.stop_bot",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F7, // F7 par défaut
                "category.player-api-mod.harvest"
        ));

        // Set Corner (cycle)
        setCornerKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.set_corner",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F8, // F8 par défaut
                "category.player-api-mod.harvest"
        ));

        // Toggle HUD
        toggleHudKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.toggle_hud",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F9, // F9 par défaut
                "category.player-api-mod.harvest"
        ));

        // Increase Drop Delay
        increaseDelayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.increase_delay",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F10, // F10 par défaut
                "category.player-api-mod.harvest"
        ));

        // Decrease Drop Delay
        decreaseDelayKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.decrease_delay",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F11, // F11 par défaut
                "category.player-api-mod.harvest"
        ));

        // Reset Bot State
        resetStateKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.player-api-mod.reset_state",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F12, // F12 par défaut
                "category.player-api-mod.harvest"
        ));

        LOGGER.info("Keybindings registered: F6 (start), F7 (stop), F8 (set corner), F9 (toggle HUD), F10 (+delay), F11 (-delay), F12 (reset)");
    }

    /**
     * Gère les actions des keybindings
     */
    private void handleKeyBindings() {
        // Anti-spam protection
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastKeyPressTime < KEY_PRESS_COOLDOWN) {
            return;
        }
        
        HarvestBotClient bot = HarvestBotClient.getInstance();
        
        // Start Bot
        if (startBotKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            LOGGER.info("Start bot key pressed");
            bot.startBot();
        }

        // Stop Bot
        if (stopBotKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            LOGGER.info("Stop bot key pressed");
            bot.stopBot();
        }

        // Set Corner (cycle through corners 1-4)
        if (setCornerKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            bot.setCorner(currentCornerIndex);
            currentCornerIndex = (currentCornerIndex + 1) % 4; // Cycle 0→1→2→3→0
            LOGGER.info("Set corner key pressed, next corner index: {}", currentCornerIndex);
        }

        // Toggle HUD
        if (toggleHudKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            HarvestBotHud hud = HarvestBotHud.getInstance();
            hud.toggle();
            LOGGER.info("Toggle HUD key pressed");
        }

        // Increase Drop Delay
        if (increaseDelayKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            int currentDelay = bot.getItemDropDelay();
            int newDelay = Math.min(360, currentDelay + 1); // Max 360 seconds (6 minutes), increment by 1 second
            bot.setItemDropDelay(newDelay);
            LOGGER.info("Drop delay increased to {}s", newDelay);
        }

        // Decrease Drop Delay
        if (decreaseDelayKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            int currentDelay = bot.getItemDropDelay();
            int newDelay = Math.max(1, currentDelay - 1); // Min 1 second, decrement by 1 second
            bot.setItemDropDelay(newDelay);
            LOGGER.info("Drop delay decreased to {}s", newDelay);
        }

        // Reset Bot State
        if (resetStateKey.wasPressed()) {
            lastKeyPressTime = currentTime;
            bot.resetSavedState();
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§eÉtat du bot réinitialisé. F6 = démarrage complet."), false);
            LOGGER.info("Reset state key pressed");
        }
    }

    // Getters statiques pour accéder aux keybindings depuis d'autres classes
    public static KeyBinding getStartBotKey() { return startBotKey; }
    public static KeyBinding getStopBotKey() { return stopBotKey; }
    public static KeyBinding getSetCornerKey() { return setCornerKey; }
    public static KeyBinding getToggleHudKey() { return toggleHudKey; }
    public static KeyBinding getIncreaseDelayKey() { return increaseDelayKey; }
    public static KeyBinding getDecreaseDelayKey() { return decreaseDelayKey; }
    public static KeyBinding getResetStateKey() { return resetStateKey; }
}