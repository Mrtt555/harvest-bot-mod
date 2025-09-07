package Mrtt555;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Map;

/**
 * HUD overlay pour afficher les informations du bot de farming
 */
public class HarvestBotHud {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("HarvestBotHud");
    private static HarvestBotHud instance;
    private boolean visible = true;
    
    // Couleurs
    private static final int COLOR_BACKGROUND = 0x80000000; // Noir transparent
    private static final int COLOR_HEADER = 0xFFFFFF00;     // Jaune
    private static final int COLOR_ACTIVE = 0xFF00FF00;     // Vert
    private static final int COLOR_INACTIVE = 0xFFFF0000;   // Rouge
    private static final int COLOR_INFO = 0xFFAAAAAA;      // Gris clair
    private static final int COLOR_CORNER = 0xFF00FFFF;    // Cyan
    
    private HarvestBotHud() {
        // Enregistrer le callback de rendu HUD
        HudRenderCallback.EVENT.register(this::onHudRender);
        LOGGER.info("HarvestBot HUD initialized");
    }
    
    public static HarvestBotHud getInstance() {
        if (instance == null) {
            instance = new HarvestBotHud();
        }
        return instance;
    }
    
    public void toggle() {
        visible = !visible;
        LOGGER.info("HUD visibility toggled: {}", visible);
    }
    
    public void setVisible(boolean visible) {
        this.visible = visible;
    }
    
    public boolean isVisible() {
        return visible;
    }
    
    /**
     * Callback de rendu du HUD
     */
    private void onHudRender(DrawContext drawContext, RenderTickCounter tickCounter) {
        if (!visible) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        renderHud(drawContext, client);
    }
    
    /**
     * Rend le HUD principal
     */
    private void renderHud(DrawContext drawContext, MinecraftClient client) {
        TextRenderer textRenderer = client.textRenderer;
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        // Position du HUD (décollé du coin supérieur droit)
        int hudWidth = 170;
        int hudX = screenWidth - hudWidth - 10;
        int hudY = 10;
        int lineHeight = 10;
        
        HarvestBotClient bot = HarvestBotClient.getInstance();
        
        // Calculer la hauteur du HUD
        int hudHeight = calculateHudHeight(bot);
        
        // Fond semi-transparent
        drawContext.fill(hudX, hudY, hudX + hudWidth, hudY + hudHeight, COLOR_BACKGROUND);
        
        int y = hudY;
        
        // Header avec pseudo
        drawContext.drawText(textRenderer, Text.literal("HARVEST BOT - Mrtt555"), hudX, y, COLOR_HEADER, false);
        y += lineHeight + 2;
        
        // Statut du bot
        String status;
        int statusColor;
        if (bot.isRunning()) {
            status = "ACTIF";
            statusColor = COLOR_ACTIVE;
        } else if (bot.canResume()) {
            status = "PAUSE (F12 = reset)";
            statusColor = COLOR_CORNER; // Cyan pour indiquer la reprise possible
        } else {
            status = "ARRÊTÉ";
            statusColor = COLOR_INACTIVE;
        }
        drawContext.drawText(textRenderer, Text.literal("Statut: " + status), hudX, y, statusColor, false);
        y += lineHeight;
        
        if (bot.isRunning()) {
            String state = getStateDisplayName(bot.getCurrentState());
            drawContext.drawText(textRenderer, Text.literal("État: " + state), hudX, y, COLOR_INFO, false);
            y += lineHeight;
        }
        
        y += 2; // Espacement
        
        // Position du joueur (compact)
        BlockPos playerPos = client.player.getBlockPos();
        drawContext.drawText(textRenderer, Text.literal("Pos: X=" + playerPos.getX() + " Z=" + playerPos.getZ()), hudX, y, COLOR_INFO, false);
        y += lineHeight + 2;
        
        // Coins configurés (compact)
        Map<String, BlockPos> corners = bot.getCorners();
        int setCorners = 0;
        for (int i = 1; i <= 4; i++) {
            if (corners.get("corner" + i) != null) setCorners++;
        }
        drawContext.drawText(textRenderer, Text.literal("Coins: " + setCorners + "/4 définis"), hudX, y, COLOR_CORNER, false);
        y += lineHeight + 2;
        
        // Configuration (compact) avec temps restant
        String dropInfo = "Drop delay: " + bot.getItemDropDelay() + "s";
        if (bot.isRunning() && bot.getTimeSinceLastDrop() >= 0) {
            long timeRemaining = bot.getItemDropDelay() - bot.getTimeSinceLastDrop();
            if (timeRemaining > 0) {
                dropInfo += " (dans " + timeRemaining + "s)";
            } else {
                dropInfo += " (maintenant!)";
            }
        }
        drawContext.drawText(textRenderer, Text.literal(dropInfo), hudX, y, COLOR_INFO, false);
        y += lineHeight + 2;
        
        // Contrôles (simplifié)
        String startKey = getKeyName(HarvestBotKeybindings.getStartBotKey());
        String stopKey = getKeyName(HarvestBotKeybindings.getStopBotKey());
        
        drawContext.drawText(textRenderer, Text.literal("Start/Stop: " + startKey + "/" + stopKey), hudX, y, COLOR_INFO, false);
    }
    
    /**
     * Calcule la hauteur nécessaire pour le HUD
     */
    private int calculateHudHeight(HarvestBotClient bot) {
        int lineHeight = 10; // Mis à jour pour correspondre à la nouvelle valeur
        int lines = 0;
        
        lines += 2; // Header + statut
        if (bot.isRunning()) {
            lines += 1; // État
        }
        lines += 1; // Position (une ligne)
        lines += 1; // Coins (une ligne)
        lines += 1; // Configuration (une ligne)
        lines += 1; // Contrôles (1 ligne compacte)
        
        return lines * lineHeight + 15; // +15 pour les espacements
    }
    
    /**
     * Dessine du texte aligné à droite
     */
    private void drawTextRightAligned(DrawContext drawContext, TextRenderer textRenderer, String text, int hudX, int hudWidth, int y, int color) {
        int textWidth = textRenderer.getWidth(text);
        int rightAlignedX = hudX + hudWidth - textWidth - 2; // -2 pour une petite marge
        drawContext.drawText(textRenderer, Text.literal(text), rightAlignedX, y, color, false);
    }
    
    /**
     * Obtient le nom d'une touche pour l'affichage
     */
    private String getKeyName(net.minecraft.client.option.KeyBinding keyBinding) {
        if (keyBinding == null) {
            return "?";
        }
        return keyBinding.getBoundKeyLocalizedText().getString();
    }
    
    /**
     * Convertit l'état du bot en nom affichable
     */
    private String getStateDisplayName(HarvestBotClient.BotState state) {
        switch (state) {
            case STOPPED: return "Arrêté";
            case GOING_TO_START: return "Vers coin 1";
            case HARVESTING_ROW: return "Récolte";
            case TURNING_AT_CORNER: return "Rotation";
            default: return "Inconnu";
        }
    }
}