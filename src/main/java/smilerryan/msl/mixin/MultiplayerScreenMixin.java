package smilerryan.msl.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import smilerryan.msl.MultipleServerLists;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Shadow private ServerList serverList;
    @Shadow private net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget serverListWidget;

    @Unique private TextFieldWidget textBox;

    @Unique private final List<String> profiles = new ArrayList<>();
    @Unique private final List<ButtonWidget> dropdownButtons = new ArrayList<>();

    @Unique private float dropdownProgress = 0f;
    @Unique private boolean dropdownTargetOpen = false;

    @Unique private String lastHoveredProfile = "";

    @Unique private double lastMouseX = -1;
    @Unique private double lastMouseY = -1;
    @Unique private long lastMouseMoveTime = 0;
    @Unique private static final long MOUSE_IDLE_MS = 150;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        textBox = new TextFieldWidget(this.textRenderer, 10, 10, 100, 16, Text.literal(""));
        addDrawableChild(textBox);
        addSelectableChild(textBox);
        rebuildDropdown();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (textBox == null || client == null) return;

        textBox.setDimensionsAndPosition(100, 16, 10, 10);

        String current = textBox.getText();
        if (!current.equals(MultipleServerLists.currentServerListFile) && !current.isEmpty()) {
            MultipleServerLists.currentServerListFile = current;
            loadProfile(current);
        }

        if (textBox.isFocused() &&
            org.lwjgl.glfw.GLFW.glfwGetKey(client.getWindow().getHandle(),
            org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        ) {
            String name = textBox.getText();
            if (name == null || name.isEmpty()) return;
            File dir = new File(client.runDirectory, "servers");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, name);
            try {if (!file.exists()) {file.createNewFile();}} catch (Exception ignored) {}
            rebuildDropdown();
        }

        double mouseX = client.mouse.getX() * (double) width / (double) client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * (double) height / (double) client.getWindow().getHeight();

        // detect mouse movement
        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseMoveTime = System.currentTimeMillis();
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        boolean mouseIdle = (System.currentTimeMillis() - lastMouseMoveTime) > MOUSE_IDLE_MS;

        boolean hoverText = textBox.isMouseOver(mouseX, mouseY);
        boolean hoverDropdown = false;
        String hoveredProfile = null;

        for (ButtonWidget btn : dropdownButtons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                hoverDropdown = true;
                hoveredProfile = btn.getMessage().getString();
                break;
            }
        }

        dropdownTargetOpen = hoverText || hoverDropdown;

        // ONLY trigger when mouse is idle
        if (mouseIdle && hoveredProfile != null && !hoveredProfile.equals(lastHoveredProfile)) {
            lastHoveredProfile = hoveredProfile;
            textBox.setText(hoveredProfile);
            loadProfile(hoveredProfile);
            rebuildDropdown();
        }

        if (hoveredProfile == null) {
            lastHoveredProfile = "";
        }

        updateAnimation();
    }

    @Unique
    private void updateAnimation() {
        float speed = 0.18f;

        dropdownProgress += dropdownTargetOpen ? speed : -speed;
        dropdownProgress = Math.max(0f, Math.min(1f, dropdownProgress));

        layoutButtons();
    }

    @Unique
    private void layoutButtons() {
        int baseX = 10;
        int baseYHidden = 10;
        int baseY = 26;
        int h = 16;

        float t = easeOut(dropdownProgress);
        boolean visible = dropdownProgress > 0.01f;

        for (int i = 0; i < dropdownButtons.size(); i++) {
            ButtonWidget btn = dropdownButtons.get(i);

            int targetY = baseY + (i * h);
            int y = (int) (baseYHidden + (targetY - baseYHidden) * t);

            btn.setX(baseX);
            btn.setY(y);

            btn.visible = visible;
            btn.active = dropdownProgress > 0.5f;
        }
    }

    @Unique
    private float easeOut(float t) {
        return (float) (1 - Math.pow(1 - t, 3));
    }

    @Unique
    private void rebuildDropdown() {
        for (ButtonWidget b : dropdownButtons) {
            remove(b);
        }
        dropdownButtons.clear();
        profiles.clear();
        File serversDir = new File(client.runDirectory, "servers");
        if (!serversDir.exists()) {
            serversDir.mkdirs();
        }
        if (serversDir.exists() && serversDir.isDirectory()) {
            for (File f : serversDir.listFiles()) {
                if (!f.getName().endsWith(".old")) {
                    profiles.add(f.getName());
                }
            }
        }
        int y = 26;
        for (String profile : profiles) {
            ButtonWidget btn = ButtonWidget.builder(Text.literal(profile), b -> {
                textBox.setText(profile);
                loadProfile(profile);
                dropdownTargetOpen = false;
            }).dimensions(10, y, 100, 16).build();
            dropdownButtons.add(btn);
            addDrawableChild(btn);
            addSelectableChild(btn);
            y += 16;
        }
        layoutButtons();
    }

    @Unique
    private void loadProfile(String profile) {
        if (client == null) return;
        if (profile == null || profile.isEmpty()) {
            profile = "General";
        }
        MultipleServerLists.currentServerListFile = profile;
        serverList.loadFile();
        if (serverListWidget != null) {
            serverListWidget.setSelected(null);
            serverListWidget.setServers(serverList);
        }
    }
}