package net.pocmod.utility;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PoCUtilityMod implements ClientModInitializer {

    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        // 1. Initialisiere den ModuleManager
        ModuleManager.init();

        // 2. Registriere den Hotkey 'O' zum Öffnen des GUI
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.pocmod.opengui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                "category.pocmod"
        ));

        // 3. Registriere den Client-Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            // Prüfe, ob die Menütaste gedrückt wurde
            if (openGuiKey.wasPressed() && client.currentScreen == null) {
                client.setScreen(new PoCConfigScreen());
            }

            // Ticke alle aktiven Cheat-Module
            ModuleManager.tick(client);
        });
    }

    // ==========================================
    // MODULE SYSTEM BASE STRUCTURE
    // ==========================================

    public static class Setting<T> {
        public final String name;
        private T value;
        private final T[] options;
        private int optionIndex = 0;

        public Setting(String name, T value, T[] options) {
            this.name = name;
            this.value = value;
            this.options = options;
        }

        public T getValue() {
            return value;
        }

        public void cycle() {
            if (options != null && options.length > 0) {
                optionIndex = (optionIndex + 1) % options.length;
                this.value = options[optionIndex];
            }
        }
    }

    public static abstract class Module {
        public final String name;
        public boolean enabled = false;
        public final List<Setting<?>> settings = new ArrayList<>();

        public Module(String name) {
            this.name = name;
        }

        public void toggle() {
            this.enabled = !this.enabled;
            if (enabled) {
                onEnable();
            } else {
                onDisable();
            }
        }

        public void onEnable() {}
        public void onDisable() {}
        public abstract void onTick(MinecraftClient client);
    }

    // ==========================================
    // MODULE MANAGER
    // ==========================================

    public static class ModuleManager {
        private static final List<Module> modules = new ArrayList<>();

        public static void init() {
            modules.add(new FlyModule());
            modules.add(new SpeedModule());
            modules.add(new JesusModule());
            modules.add(new ESPModule());
        }

        public static List<Module> getModules() {
            return modules;
        }

        public static void tick(MinecraftClient client) {
            for (Module m : modules) {
                m.onTick(client);
            }
        }
    }

    // ==========================================
    // CHEAT MODULES IMPLEMENTATIONS
    // ==========================================

    // 1. Fly Module
    public static class FlyModule extends Module {
        public FlyModule() {
            super("Fly");
        }

        @Override
        public void onTick(MinecraftClient client) {
            if (client.player == null) return;
            if (enabled) {
                // Manipuliert die Client-Capabilities, um Fliegen zu erlauben
                client.player.getAbilities().allowFlying = true;
            }
        }

        @Override
        public void onDisable() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null && !client.player.isCreative() && !client.player.isSpectator()) {
                client.player.getAbilities().allowFlying = false;
                client.player.getAbilities().flying = false;
            }
        }
    }

    // 2. Speed Module
    public static class SpeedModule extends Module {
        public final Setting<Double> multiplier = new Setting<>(
                "Faktor", 1.5, new Double[]{1.2, 1.5, 2.0, 3.0, 5.0}
        );

        public SpeedModule() {
            super("Speed");
            settings.add(multiplier);
        }

        @Override
        public void onTick(MinecraftClient client) {
            if (client.player == null || !enabled) return;

            // Multipliziert die horizontale Geschwindigkeit, wenn der Spieler sich aktiv bewegt
            if (client.player.isOnGround() && (client.player.input.movementForward != 0 || client.player.input.movementSideways != 0)) {
                double factor = multiplier.getValue();
                Vec3d vel = client.player.getVelocity();
                client.player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
            }
        }
    }

    // 3. Jesus Module (Wasserlaufen)
    public static class JesusModule extends Module {
        public JesusModule() {
            super("Jesus");
        }

        @Override
        public void onTick(MinecraftClient client) {
            if (client.player == null || !enabled) return;

            // Falls der Spieler Wasser oder Lava berührt, wird er nach oben gedrückt
            if (client.player.isTouchingWater() || client.player.isInLava()) {
                Vec3d vel = client.player.getVelocity();
                // Setzt die Y-Geschwindigkeit leicht positiv, um auf der Oberfläche zu treiben
                client.player.setVelocity(vel.x, 0.12, vel.z);
                client.player.setOnGround(true); // Täuscht Bodenkontakt vor
            }
        }
    }

    // 4. Player ESP Module (Glow Effect Method)
    public static class ESPModule extends Module {
        public ESPModule() {
            super("Player ESP");
        }

        @Override
        public void onTick(MinecraftClient client) {
            if (client.world == null || client.player == null) return;

            for (AbstractClientPlayerEntity target : client.world.getPlayers()) {
                if (target == client.player) continue;

                if (enabled) {
                    // Nutzt den nativen Glowing-Effekt (In-Game Outlines) clientseitig
                    target.setGlowing(true);
                } else {
                    target.setGlowing(false);
                }
            }
        }

        @Override
        public void onDisable() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) return;
            for (AbstractClientPlayerEntity target : client.world.getPlayers()) {
                target.setGlowing(false);
            }
        }
    }

    // ==========================================
    // CONFIG GUI SCREEN (KEY: O)
    // ==========================================

    public static class PoCConfigScreen extends Screen {
        public PoCConfigScreen() {
            super(Text.literal("Anti-Cheat Test Framework (PoC)"));
        }

        @Override
        protected void init() {
            int startY = 60;
            int buttonWidth = 140;
            int buttonHeight = 20;
            int spacing = 26;

            int index = 0;
            for (Module module : ModuleManager.getModules()) {
                int y = startY + (index * spacing);
                int leftX = this.width / 2 - buttonWidth - 5;
                int rightX = this.width / 2 + 5;

                // Button für Modul-Toggle (AN / AUS)
                ButtonWidget toggleButton = ButtonWidget.builder(
                        Text.literal(module.name + ": " + (module.enabled ? "AN" : "AUS")),
                        button -> {
                            module.toggle();
                            button.setMessage(Text.literal(module.name + ": " + (module.enabled ? "AN" : "AUS")));
                        }
                ).dimensions(leftX, y, buttonWidth, buttonHeight).build();
                this.addDrawableChild(toggleButton);

                // Button für Einstellungen (falls vorhanden)
                int settingIndex = 0;
                for (Setting<?> setting : module.settings) {
                    int sX = rightX + (settingIndex * 110);
                    ButtonWidget settingButton = ButtonWidget.builder(
                            Text.literal(setting.name + ": " + setting.getValue()),
                            button -> {
                                setting.cycle();
                                button.setMessage(Text.literal(setting.name + ": " + setting.getValue()));
                            }
                    ).dimensions(sX, y, 100, buttonHeight).build();
                    this.addDrawableChild(settingButton);
                    settingIndex++;
                }

                index++;
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            this.renderBackground(context, mouseX, mouseY, delta);
            // Titel zeichnen
            context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 25, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("Nutze diese Module ausschließlich zum Testen deines Anti-Cheats!"), this.width / 2, 40, 0xAAAAAA);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean shouldPauseGame() {
            return false; // Verhindert, dass das Spiel im Singleplayer pausiert
        }
    }
}