package Mrtt555;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bot de farming intégré directement dans le mod Minecraft
 * Remplace l'ancien bot externe JavaFX + Robot par des contrôles natifs Minecraft
 */
public class HarvestBotClient {
    private static final Logger LOGGER = LoggerFactory.getLogger("HarvestBotClient");
    
    // --- Configuration ---
    private final Map<String, BlockPos> corners = new HashMap<>();
    private int itemDropDelay = 240; // secondes entre les drops d'items (4 minutes par défaut)
    
    // --- États du bot ---
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private BotState currentState = BotState.STOPPED;
    private int targetCornerIndex = 0;
    
    // --- État sauvegardé pour reprendre ---
    private BotState lastValidState = BotState.STOPPED;
    private int lastValidTargetCornerIndex = 0;
    private boolean hasValidState = false;
    
    // --- Timer pour le drop d'items ---
    private long lastItemDropTime = 0;
    
    // --- Rotation naturelle ---
    private final Random random = new Random();
    private float baseRotationSpeed = 8.0f;
    private float currentRotationMomentum = 0.0f;
    private long lastRotationTime = 0;
    private long lastRotationRequestTime = 0;
    private float targetPitch = 30.0f;
    private float currentPitchOffset = 0.0f;
    private boolean isRotating = false;
    private long headBobbingTimer = 0;
    
    // --- État pour le Zigzag ---
    private boolean isStrafingRight = true;
    private long lastStrafeSwitchTime = 0;
    private static final long STRAFE_DURATION_MS = 600;
    
    // --- Mouvements de tête pour optimiser la récolte ---
    private float baseTargetYaw = 0.0f; // Direction principale
    private float headSweepOffset = 0.0f; // Offset actuel du balayage
    private boolean sweepingRight = true; // Direction du balayage
    private long lastHeadSweepTime = 0;
    private static final float MIN_HEAD_SWEEP = 30.0f; // Minimum ±30 degrés
    private static final float MAX_HEAD_SWEEP = 35.0f; // Maximum ±35 degrés
    private static final long HEAD_SWEEP_INTERVAL_MS = 150; // Changement toutes les 150ms
    private float currentMaxSweep = MAX_HEAD_SWEEP; // Valeur actuelle variable
    
    // --- Clic maintenu pour le mining ---
    private boolean shouldMaintainAttack = false; // Indique si on doit maintenir l'attaque
    
    // --- Mining direct avec chat ouvert ---
    private long lastDirectMiningTime = 0;
    private static final long MINING_INTERVAL_MS = 100; // 100ms entre chaque action de mining (10/sec)
    
    // --- Machine à États ---
    public enum BotState {
        STOPPED,          // Bot arrêté
        GOING_TO_START,   // Se déplace vers corner1
        HARVESTING_ROW,   // Récolte en zigzaguant
        TURNING_AT_CORNER // Rotation au coin
    }
    
    // --- Instance singleton ---
    private static HarvestBotClient instance;
    
    private HarvestBotClient() {
        // Enregistrer le tick handler
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
        LOGGER.info("HarvestBotClient initialized");
    }
    
    public static HarvestBotClient getInstance() {
        if (instance == null) {
            instance = new HarvestBotClient();
        }
        return instance;
    }
    
    /**
     * Appelé à chaque tick du client (20 fois par seconde)
     */
    private void onClientTick(MinecraftClient client) {
        if (!isRunning.get() || client.player == null || client.world == null) {
            return;
        }
        
        // Continuer même si une interface est ouverte (chat, inventaire, etc.)
        // Cela permet de farmer en arrière-plan
        
        // Si une interface est ouverte et qu'on doit maintenir l'attaque, forcer le clic maintenu
        if (client.currentScreen != null && shouldMaintainAttack) {
            maintainAttackWithInterfaceOpen(client);
        }
        
        // Exécuter la machine à états
        switch (currentState) {
            case GOING_TO_START:
                handleGoingToStart(client);
                break;
            case HARVESTING_ROW:
                handleHarvestingRow(client);
                break;
            case TURNING_AT_CORNER:
                handleTurningAtCorner(client);
                break;
            case STOPPED:
                // Ne rien faire
                break;
        }
    }
    
    /**
     * Démarre le bot de farming
     */
    public void startBot() {
        if (isRunning.get()) {
            LOGGER.info("Bot already running");
            return;
        }
        
        if (corners.size() < 4) {
            sendMessage("§cErreur: Définissez les 4 coins du champ d'abord!");
            return;
        }
        
        // Log tous les corners définis
        for (int i = 1; i <= 4; i++) {
            String cornerName = "corner" + i;
            BlockPos corner = corners.get(cornerName);
            if (corner != null) {
                LOGGER.info("Corner {} set at: X={}, Z={}", i, corner.getX(), corner.getZ());
            } else {
                LOGGER.error("Corner {} is NULL!", i);
            }
        }
        
        isRunning.set(true);
        
        // Reprendre l'état sauvegardé si disponible
        if (hasValidState && lastValidState != BotState.STOPPED) {
            currentState = lastValidState;
            targetCornerIndex = lastValidTargetCornerIndex;
            sendMessage("§aBot repris! État: " + getStateDisplayName(currentState) + 
                       ", Cible: corner" + (targetCornerIndex + 1));
            LOGGER.info("Resuming bot from saved state: {} targeting corner{}", 
                       currentState, targetCornerIndex + 1);
        } else {
            currentState = BotState.GOING_TO_START;
            targetCornerIndex = 0;
            sendMessage("§aBot de farming démarré!");
            LOGGER.info("Starting bot from beginning");
        }
    }
    
    /**
     * Arrête le bot de farming
     */
    public void stopBot() {
        if (!isRunning.get()) {
            return;
        }
        
        // Sauvegarder l'état actuel avant d'arrêter
        if (currentState != BotState.STOPPED) {
            lastValidState = currentState;
            lastValidTargetCornerIndex = targetCornerIndex;
            hasValidState = true;
            LOGGER.info("Saving bot state: {} targeting corner{}", currentState, targetCornerIndex + 1);
        }
        
        isRunning.set(false);
        currentState = BotState.STOPPED;
        
        // Arrêter tous les mouvements
        stopAllMovements();
        
        // Réinitialiser le balayage de tête
        headSweepOffset = 0.0f;
        sweepingRight = true;
        currentMaxSweep = MAX_HEAD_SWEEP;
        
        // Arrêter le maintien d'attaque
        shouldMaintainAttack = false;
        
        // Réinitialiser le mining direct
        lastDirectMiningTime = 0;
        
        sendMessage("§cBot mis en pause! F6=reprendre, F12=reset complet");
        LOGGER.info("Harvest bot paused and state saved");
    }
    
    /**
     * Configure un coin du champ
     */
    public void setCorner(int cornerIndex) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        BlockPos playerPos = client.player.getBlockPos();
        String cornerName = "corner" + (cornerIndex + 1);
        corners.put(cornerName, playerPos);
        
        sendMessage(String.format("§eCoin %d défini: X=%d, Z=%d", 
                cornerIndex + 1, playerPos.getX(), playerPos.getZ()));
        
        LOGGER.info("Corner {} set to {}", cornerIndex + 1, playerPos);
    }
    
    /**
     * Gère l'état GOING_TO_START
     */
    private void handleGoingToStart(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        BlockPos corner1 = corners.get("corner1");
        
        if (corner1 == null) {
            stopBot();
            return;
        }
        
        BlockPos playerPos = player.getBlockPos();
        double distance = playerPos.getSquaredDistance(corner1);
        
        // LOGGER.debug("Going to corner1 at X={}, Z={}, player at X={}, Z={}, distance: {}", 
        //             corner1.getX(), corner1.getZ(), playerPos.getX(), playerPos.getZ(), distance);
        
        // Si arrivé à destination
        if (distance < 4) { // 2 blocs de distance
            LOGGER.info("Reached corner1, starting harvest");
            
            // Dropper un item pour démarrer le timer
            dropItem(client);
            lastItemDropTime = System.currentTimeMillis();
            LOGGER.info("Item dropped at corner1, timer started for {} seconds", itemDropDelay);
            
            currentState = BotState.HARVESTING_ROW;
            targetCornerIndex = 1; // Prochaine cible: corner2
            return;
        }
        
        // Se déplacer vers corner1
        moveToPosition(client, corner1);
    }
    
    /**
     * Gère l'état HARVESTING_ROW  
     */
    private void handleHarvestingRow(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        String[] cornerNames = {"corner1", "corner2", "corner3", "corner4"};
        BlockPos targetCorner = corners.get(cornerNames[targetCornerIndex]);
        
        if (targetCorner == null) {
            LOGGER.error("Target corner {} is null, stopping bot", cornerNames[targetCornerIndex]);
            stopBot();
            return;
        }
        
        BlockPos playerPos = player.getBlockPos();
        double distance = playerPos.getSquaredDistance(targetCorner);
        
        // LOGGER.debug("Harvesting toward {}, distance: {}", cornerNames[targetCornerIndex], distance);
        
        // Si arrivé au coin (distance augmentée pour détecter plus tôt)
        if (distance < 16) { // 4 blocs de distance
            LOGGER.info("Reached {} (distance: {}), turning to next corner", cornerNames[targetCornerIndex], distance);
            currentState = BotState.TURNING_AT_CORNER;
            lastStrafeSwitchTime = 0;
            return;
        }
        
        // Vérifier si il faut dropper un item
        checkAndDropItem(client);
        
        // Mouvement en zigzag avec mining
        performZigzagMovement(client, targetCorner);
    }
    
    /**
     * Gère l'état TURNING_AT_CORNER
     */
    private void handleTurningAtCorner(MinecraftClient client) {
        // Calculer la prochaine direction
        int nextCornerIndex = (targetCornerIndex + 1) % 4;
        String[] cornerNames = {"corner1", "corner2", "corner3", "corner4"};
        String currentCornerName = cornerNames[targetCornerIndex];
        String nextCornerName = cornerNames[nextCornerIndex];
        BlockPos nextCorner = corners.get(nextCornerName);
        
        if (nextCorner == null) {
            LOGGER.error("Next corner {} is null, stopping bot", nextCornerName);
            stopBot();
            return;
        }
        
        // Forcer sprint et mining pendant la rotation même avec interface ouverte
        shouldMaintainAttack = true;
        forceKeyPressed(client.options.sprintKey, true);
        forceKeyPressed(client.options.attackKey, true);
        
        // Vérifier si il faut dropper un item même pendant la rotation
        checkAndDropItem(client);
        
        // Rotation vers la prochaine cible
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        
        float targetYaw = calculateYawToTarget(playerPos, nextCorner);
        float currentYaw = player.getYaw();
        float yawDiff = calculateYawDifference(currentYaw, targetYaw);
        
        // LOGGER.debug("Turning from {} to {}: yaw {} -> {} (diff: {})", 
        //              currentCornerName, nextCornerName, currentYaw, targetYaw, yawDiff);
        
        // Si bien orienté, passer à l'état suivant
        if (Math.abs(yawDiff) < 5.0f) { // Réduction de la tolérance pour plus de précision
            LOGGER.info("Rotation completed from {} to {}, continuing harvest", currentCornerName, nextCornerName);
            targetCornerIndex = nextCornerIndex;
            currentState = BotState.HARVESTING_ROW;
            return;
        }
        
        // Effectuer rotation progressive
        rotateToTarget(targetYaw);
    }
    
    /**
     * Effectue le mouvement en zigzag avec mining
     */
    private void performZigzagMovement(MinecraftClient client, BlockPos target) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        
        // Calculer direction principale vers la cible
        baseTargetYaw = calculateYawToTarget(playerPos, target);
        
        // Calculer le mouvement de tête pour optimiser la récolte
        updateHeadSweepMovement();
        
        // Direction finale = direction principale + balayage de tête
        float finalTargetYaw = baseTargetYaw + headSweepOffset;
        
        float currentYaw = player.getYaw();
        float yawDiff = calculateYawDifference(currentYaw, finalTargetYaw);
        
        // Orienter avec balayage de tête (plus tolérant pour permettre les oscillations)
        if (Math.abs(yawDiff) > 5.0f && shouldRotateNow()) {
            rotateToTarget(finalTargetYaw);
            // Réduire légèrement le momentum lors des corrections de direction
            currentRotationMomentum *= 0.95f; // Moins agressif pour permettre le balayage
        }
        
        // Forcer les mouvements même si une interface est ouverte
        forceKeyPressed(client.options.forwardKey, true);
        forceKeyPressed(client.options.sprintKey, true);
        
        // Mining continu même avec interface ouverte - activer le maintien d'attaque
        shouldMaintainAttack = true;
        forceKeyPressed(client.options.attackKey, true);
        
        // Changer de direction de strafe périodiquement  
        if (System.currentTimeMillis() - lastStrafeSwitchTime > STRAFE_DURATION_MS) {
            isStrafingRight = !isStrafingRight;
            lastStrafeSwitchTime = System.currentTimeMillis();
            // LOGGER.debug("Switching strafe direction: {}", isStrafingRight ? "RIGHT" : "LEFT");
        }
        
        // Appliquer le strafe même avec interface ouverte
        if (isStrafingRight) {
            forceKeyPressed(client.options.rightKey, true);
            forceKeyPressed(client.options.leftKey, false);
        } else {
            forceKeyPressed(client.options.leftKey, true);
            forceKeyPressed(client.options.rightKey, false);
        }
    }
    
    /**
     * Se déplace vers une position donnée
     */
    private void moveToPosition(MinecraftClient client, BlockPos target) {
        ClientPlayerEntity player = client.player;
        BlockPos playerPos = player.getBlockPos();
        
        // Forcer le sprint même avec interface ouverte
        forceKeyPressed(client.options.sprintKey, true);
        
        // Calculer la direction
        float targetYaw = calculateYawToTarget(playerPos, target);
        float currentYaw = player.getYaw();
        float yawDiff = calculateYawDifference(currentYaw, targetYaw);
        
        // LOGGER.debug("Moving to target X={}, Z={}: current yaw={}, target yaw={}, diff={}", 
        //             target.getX(), target.getZ(), currentYaw, targetYaw, yawDiff);
        
        // Rotation progressive si nécessaire (avec délai naturel)
        if (Math.abs(yawDiff) > 5.0f && shouldRotateNow()) {
            rotateToTarget(targetYaw);
        }
        
        // Forcer l'avancement même avec interface ouverte
        forceKeyPressed(client.options.forwardKey, true);
    }
    
    /**
     * Rotation progressive vers la cible avec mouvements naturels
     */
    private void rotateToTarget(float targetYaw) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        try {
            long currentTime = System.currentTimeMillis();
            float currentYaw = client.player.getYaw();
            float yawDiff = calculateYawDifference(currentYaw, targetYaw);
            
            // Vitesse de rotation variable avec acceleration/deceleration naturelle
            float targetSpeed = calculateNaturalRotationSpeed(Math.abs(yawDiff));
            
            // Lissage du momentum (acceleration/deceleration progressive)
            float speedDelta = targetSpeed - Math.abs(currentRotationMomentum);
            float acceleration = speedDelta * 0.3f; // 30% d'ajustement par tick
            currentRotationMomentum += acceleration;
            
            // Limiter la vitesse de rotation
            currentRotationMomentum = Math.max(0.5f, Math.min(currentRotationMomentum, 12.0f));
            
            // Appliquer la direction
            float rotationSpeed = Math.signum(yawDiff) * currentRotationMomentum;
            
            // Ajouter micro-variations pour paraître naturel
            if (currentTime - lastRotationTime > 50) { // Tous les 50ms
                float microVariation = (random.nextFloat() - 0.5f) * 0.8f; // ±0.4°
                rotationSpeed += microVariation;
                lastRotationTime = currentTime;
            }
            
            float newYaw = currentYaw + rotationSpeed;
            
            // Pitch avec variations subtiles + head bobbing pendant mouvement
            updateNaturalPitch();
            float headBobbingOffset = calculateHeadBobbingOffset();
            float finalPitch = targetPitch + currentPitchOffset + headBobbingOffset;
            
            client.player.setYaw(newYaw);
            client.player.setPitch(finalPitch);
            
        } catch (Exception e) {
            LOGGER.error("Error during rotation: {}", e.getMessage());
        }
    }
    
    /**
     * Calcule une vitesse de rotation naturelle basée sur la distance angulaire
     */
    private float calculateNaturalRotationSpeed(float angleDiff) {
        // Vitesse adaptative: plus lent pour les petits ajustements, plus rapide pour les grandes rotations
        if (angleDiff > 90.0f) {
            return baseRotationSpeed + random.nextFloat() * 3.0f; // 8-11°/tick pour grandes rotations
        } else if (angleDiff > 30.0f) {
            return baseRotationSpeed * 0.75f + random.nextFloat() * 2.0f; // 6-8°/tick pour rotations moyennes
        } else if (angleDiff > 10.0f) {
            return baseRotationSpeed * 0.5f + random.nextFloat() * 1.5f; // 4-5.5°/tick pour petites rotations
        } else {
            return baseRotationSpeed * 0.25f + random.nextFloat() * 0.8f; // 2-2.8°/tick pour micro-ajustements
        }
    }
    
    /**
     * Met à jour le pitch avec des variations naturelles subtiles
     */
    private void updateNaturalPitch() {
        long currentTime = System.currentTimeMillis();
        
        // Variation lente du pitch de base (tous les 2-4 secondes)
        if (currentTime % 3000 < 50) { // Environ toutes les 3 secondes
            targetPitch = 28.0f + random.nextFloat() * 4.0f; // Entre 28° et 32°
        }
        
        // Micro-oscillations du pitch (tous les 100-300ms)
        if (currentTime % (200 + random.nextInt(200)) < 50) {
            currentPitchOffset = (random.nextFloat() - 0.5f) * 1.5f; // ±0.75°
        }
        
        // Atténuation progressive de l'offset
        currentPitchOffset *= 0.98f;
    }
    
    /**
     * Calcule un léger "head bobbing" pendant les mouvements pour simuler un joueur naturel
     */
    private float calculateHeadBobbingOffset() {
        long currentTime = System.currentTimeMillis();
        headBobbingTimer = currentTime;
        
        // Head bobbing subtil basé sur le temps (simulation de la marche)
        // Oscillation lente: 0.8-1.2 secondes par cycle
        double cycleTime = 1000 + (Math.sin(currentTime * 0.0003) * 200); // 0.8-1.2 sec
        double phase = (currentTime % (long)cycleTime) / cycleTime * 2 * Math.PI;
        
        // Amplitude très réduite pour être subtile (±0.3°)
        float baseOffset = (float)(Math.sin(phase) * 0.3);
        
        // Ajouter une seconde harmonique pour plus de naturel
        double phase2 = phase * 2.1; // Légèrement désynchronisé
        float secondaryOffset = (float)(Math.sin(phase2) * 0.1);
        
        return baseOffset + secondaryOffset;
    }
    
    /**
     * Met à jour le mouvement de balayage de tête pour optimiser la récolte
     * Varie aléatoirement entre 30° et 35° dans toutes les directions
     */
    private void updateHeadSweepMovement() {
        long currentTime = System.currentTimeMillis();
        
        // Changer de direction de balayage périodiquement
        if (currentTime - lastHeadSweepTime > HEAD_SWEEP_INTERVAL_MS) {
            lastHeadSweepTime = currentTime;
            
            // Varier aléatoirement l'amplitude entre 30° et 35° à chaque changement de direction
            if ((sweepingRight && headSweepOffset >= currentMaxSweep) || 
                (!sweepingRight && headSweepOffset <= -currentMaxSweep)) {
                // Nouvelle amplitude aléatoire entre 30° et 35°
                currentMaxSweep = MIN_HEAD_SWEEP + random.nextFloat() * (MAX_HEAD_SWEEP - MIN_HEAD_SWEEP);
            }
            
            // Vitesse de balayage constante
            float sweepSpeed = 8.0f;
            
            // Progression progressive du balayage avec amplitude variable
            if (sweepingRight) {
                headSweepOffset += sweepSpeed;
                if (headSweepOffset >= currentMaxSweep) {
                    headSweepOffset = currentMaxSweep;
                    sweepingRight = false; // Changer de direction
                }
            } else {
                headSweepOffset -= sweepSpeed;
                if (headSweepOffset <= -currentMaxSweep) {
                    headSweepOffset = -currentMaxSweep;
                    sweepingRight = true; // Changer de direction
                }
            }
            
            // LOGGER.debug("Head sweep: offset={}, direction={}, currentMaxSweep={}", 
            //              headSweepOffset, sweepingRight ? "RIGHT" : "LEFT", currentMaxSweep);
        }
    }
    
    
    
    /**
     * Arrête tous les mouvements
     */
    private void stopAllMovements() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options != null) {
            client.options.forwardKey.setPressed(false);
            client.options.backKey.setPressed(false);
            client.options.leftKey.setPressed(false);
            client.options.rightKey.setPressed(false);
            client.options.sprintKey.setPressed(false);
            client.options.attackKey.setPressed(false);
        }
    }
    
    /**
     * Calcule le yaw nécessaire pour regarder vers une cible
     * Convention Minecraft: Nord=180°, Est=-90°, Sud=0°, Ouest=90°
     */
    private float calculateYawToTarget(BlockPos from, BlockPos to) {
        double deltaX = to.getX() - from.getX();
        double deltaZ = to.getZ() - from.getZ();
        
        // LOGGER.debug("Calculating yaw: from X={}, Z={} to X={}, Z={}, deltaX={}, deltaZ={}", 
        //             from.getX(), from.getZ(), to.getX(), to.getZ(), deltaX, deltaZ);
        
        // Calcul correct pour les conventions Minecraft
        double angleRad = Math.atan2(-deltaX, deltaZ); // Inverser deltaX pour les conventions MC
        double angleDeg = Math.toDegrees(angleRad);
        
        // Normaliser l'angle entre -180 et 180
        float yaw = (float) angleDeg;
        while (yaw > 180.0f) yaw -= 360.0f;
        while (yaw <= -180.0f) yaw += 360.0f;
        
        // LOGGER.debug("Calculated yaw: {} degrees", yaw);
        return yaw;
    }
    
    /**
     * Détermine si une rotation peut être effectuée maintenant (évite les mouvements trop fréquents)
     */
    private boolean shouldRotateNow() {
        long currentTime = System.currentTimeMillis();
        
        // Si pas de rotation récente, autoriser
        if (!isRotating) {
            // Délai variable entre 80-150ms pour paraître naturel
            long minDelay = 80 + random.nextInt(70);
            if (currentTime - lastRotationRequestTime > minDelay) {
                lastRotationRequestTime = currentTime;
                isRotating = true;
                return true;
            }
            return false;
        }
        
        // Si rotation en cours, continuer pendant 200-400ms puis pause
        long rotationDuration = 200 + random.nextInt(200);
        if (currentTime - lastRotationRequestTime > rotationDuration) {
            isRotating = false;
            // Ajouter une petite pause après rotation (50-120ms)
            lastRotationRequestTime = currentTime + 50 + random.nextInt(70);
        }
        
        return isRotating;
    }
    
    /**
     * Calcule la différence entre deux angles
     */
    private float calculateYawDifference(float current, float target) {
        float diff = target - current;
        while (diff <= -180.0f) diff += 360.0f;
        while (diff > 180.0f) diff -= 360.0f;
        return diff;
    }
    
    /**
     * Envoie un message au joueur
     */
    private void sendMessage(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
    
    // --- Getters/Setters pour configuration ---
    
    public boolean isRunning() {
        return isRunning.get();
    }
    
    public BotState getCurrentState() {
        return currentState;
    }
    
    public Map<String, BlockPos> getCorners() {
        return new HashMap<>(corners);
    }
    
    public int getItemDropDelay() {
        return itemDropDelay;
    }
    
    public void setItemDropDelay(int delay) {
        this.itemDropDelay = Math.max(1, Math.min(360, delay)); // Entre 1s et 360s (6 minutes)
        LOGGER.info("Item drop delay set to {}s", this.itemDropDelay);
    }
    
    /**
     * Réinitialise l'état sauvegardé (redémarre depuis le début)
     */
    public void resetSavedState() {
        hasValidState = false;
        lastValidState = BotState.STOPPED;
        lastValidTargetCornerIndex = 0;
        LOGGER.info("Saved state reset");
    }
    
    /**
     * Indique si le bot peut reprendre depuis un état sauvegardé
     */
    public boolean canResume() {
        return hasValidState && lastValidState != BotState.STOPPED;
    }
    
    /**
     * Obtient le temps écoulé depuis le dernier drop en secondes
     */
    public long getTimeSinceLastDrop() {
        if (lastItemDropTime == 0) {
            return -1; // Aucun drop effectué
        }
        return (System.currentTimeMillis() - lastItemDropTime) / 1000;
    }
    
    /**
     * Vérifie si il faut dropper un item selon le timer
     */
    private void checkAndDropItem(MinecraftClient client) {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastDrop = (currentTime - lastItemDropTime) / 1000; // en secondes
        
        if (timeSinceLastDrop >= itemDropDelay) {
            dropItem(client);
            lastItemDropTime = currentTime;
            LOGGER.info("Item dropped after {} seconds (delay: {}s)", timeSinceLastDrop, itemDropDelay);
        }
    }
    
    /**
     * Droppe un item de l'inventaire même avec le chat ouvert
     */
    private void dropItem(MinecraftClient client) {
        if (client.player == null) {
            return;
        }
        
        try {
            // Forcer le drop même si une interface est ouverte
            forceKeyPressed(client.options.dropKey, true);
            
            // Si le chat est ouvert, forcer le drop directement via l'interaction manager
            if (client.currentScreen instanceof ChatScreen) {
                performDirectDrop(client);
            }
            
            // Relâcher la touche après un court délai (simuler un appui bref)
            new Thread(() -> {
                try {
                    Thread.sleep(50); // 50ms d'appui
                    forceKeyPressed(client.options.dropKey, false);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
            
            // LOGGER.debug("Drop key pressed with force");
        } catch (Exception e) {
            LOGGER.error("Error dropping item: {}", e.getMessage());
        }
    }
    
    /**
     * Effectue le drop direct même avec le chat ouvert
     */
    private void performDirectDrop(MinecraftClient client) {
        try {
            if (client.player != null && client.interactionManager != null) {
                // Simuler le drop en utilisant l'action directe sur l'item sélectionné
                int selectedSlot = client.player.getInventory().selectedSlot;
                if (!client.player.getInventory().getStack(selectedSlot).isEmpty()) {
                    // Utiliser la méthode correcte pour dropper l'item sélectionné
                    client.player.dropSelectedItem(false); // false = drop 1 item seulement
                    LOGGER.debug("Direct drop performed from selected slot {}", selectedSlot);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Direct drop failed: {}", e.getMessage());
        }
    }
    
    /**
     * Convertit l'état du bot en nom affichable
     */
    private String getStateDisplayName(BotState state) {
        switch (state) {
            case STOPPED: return "Arrêté";
            case GOING_TO_START: return "Vers coin 1";
            case HARVESTING_ROW: return "Récolte";
            case TURNING_AT_CORNER: return "Rotation";
            default: return "Inconnu";
        }
    }
    
    /**
     * Force l'appui d'une touche même si une interface est ouverte
     */
    private void forceKeyPressed(net.minecraft.client.option.KeyBinding key, boolean pressed) {
        try {
            // Forcer l'état de la touche de manière persistante
            key.setPressed(pressed);
            
            MinecraftClient client = MinecraftClient.getInstance();
            boolean interfaceIsOpen = client.currentScreen != null;
            
            // Pour l'attackKey (mining), maintenir la touche pressée de manière plus agressive
            if (pressed && key == client.options.attackKey) {
                // Maintenir la touche pressée dans tous les cas
                key.setPressed(true);
                
                // Si une interface est ouverte, forcer encore plus
                if (interfaceIsOpen) {
                    key.setPressed(true);
                    key.setPressed(true); // Triple-assurance
                }
            }
            
            // Pour la dropKey, forcer l'action directement si une interface est ouverte
            if (pressed && key == client.options.dropKey && interfaceIsOpen) {
                performDirectDrop(client);
            }
            
        } catch (Exception e) {
            // Fallback silencieux en cas d'erreur
            LOGGER.debug("Could not force key press: {}", e.getMessage());
        }
    }
    
    /**
     * Maintient le clic même avec une interface ouverte (chat, inventaire, etc.)
     */
    private void maintainAttackWithInterfaceOpen(MinecraftClient client) {
        try {
            // Méthode 1: Forcer la touche pressée (fonctionne parfois)
            client.options.attackKey.setPressed(true);
            
            // Méthode 2: Simulation directe de mining pour contourner le blocage des interfaces
            simulateDirectMining(client);
            
            // LOGGER.debug("Maintaining attack with interface open: {}", 
            //              client.currentScreen.getClass().getSimpleName());
        } catch (Exception e) {
            // Erreur silencieuse pour éviter les spams de log
            LOGGER.debug("Maintain attack with interface open failed: {}", e.getMessage());
        }
    }
    
    /**
     * Simule le mining directement pour contourner le blocage des interfaces
     */
    private void simulateDirectMining(MinecraftClient client) {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Throttling pour éviter trop d'actions
            if (currentTime - lastDirectMiningTime < MINING_INTERVAL_MS) {
                return;
            }
            
            // Simuler l'action de mining si on vise un bloc
            if (client.crosshairTarget != null && client.crosshairTarget.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) client.crosshairTarget;
                BlockPos targetPos = blockHit.getBlockPos();
                
                if (client.world != null && !client.world.getBlockState(targetPos).isAir()) {
                    if (client.interactionManager != null && client.player != null) {
                        // Simuler l'attaque continue sur le bloc
                        client.interactionManager.attackBlock(targetPos, blockHit.getSide());
                        client.player.swingHand(client.player.getActiveHand());
                        
                        lastDirectMiningTime = currentTime;
                        
                        // LOGGER.debug("Simulating mining on block at {} with interface open", targetPos);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Direct mining simulation failed: {}", e.getMessage());
        }
    }
}