package smilerryan.msl.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
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

    @Unique private static final int BOX_X = 10;
    @Unique private static final int BOX_Y = 10;
    @Unique private static final int BOX_W = 100;
    @Unique private static final int BOX_H = 16;

    @Unique private static final int DROPDOWN_Y = 26;

    @Unique private static final float ANIM_SPEED = 0.18f;
    @Unique private static final long MOUSE_IDLE_MS = 150L;
    @Unique private static final long SCROLL_DELAY_MS = 40L;

    @Unique private TextFieldWidget textBox;

    @Unique private final List<String> profiles = new ArrayList<>();
    @Unique private final List<ButtonWidget> dropdownButtons = new ArrayList<>();

    @Unique private float dropdownProgress;
    @Unique private boolean dropdownTargetOpen;

    @Unique private String lastHoveredProfile = "";

    @Unique private double lastMouseX = -1;
    @Unique private double lastMouseY = -1;
    @Unique private long lastMouseMoveTime;

    @Unique private int scrollIndex = -1;
    @Unique private long lastScrollTime;

    @Unique private File serversDir;

    protected MultiplayerScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void onInit(CallbackInfo ci) {
        textBox = new TextFieldWidget(
            this.textRenderer,
            BOX_X,
            BOX_Y,
            BOX_W,
            BOX_H,
            Text.empty()
        );

        addDrawableChild(textBox);
        addSelectableChild(textBox);

        serversDir = new File(client.runDirectory, "servers");

        if (!serversDir.exists()) {
            serversDir.mkdirs();
        }

        rebuildDropdown();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        if (client == null || textBox == null) {
            return;
        }

        textBox.setDimensionsAndPosition(BOX_W, BOX_H, BOX_X, BOX_Y);

        handleProfileTyping();
        handleEnterCreate();
        handleHoverSelection();
        updateAnimation();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (textBox == null || profiles.isEmpty()) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        if (!(textBox.isFocused() || textBox.isMouseOver(mouseX, mouseY))) {
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }

        long now = System.currentTimeMillis();

        if (now - lastScrollTime < SCROLL_DELAY_MS) {
            return true;
        }

        lastScrollTime = now;

        if (scrollIndex == -1) {
            scrollIndex = profiles.indexOf(textBox.getText());

            if (scrollIndex < 0) {
                scrollIndex = 0;
            }
        }

        if (verticalAmount > 0) {
            scrollIndex--;
        } else if (verticalAmount < 0) {
            scrollIndex++;
        }

        if (scrollIndex < 0) {
            scrollIndex = profiles.size() - 1;
        } else if (scrollIndex >= profiles.size()) {
            scrollIndex = 0;
        }

        selectProfile(profiles.get(scrollIndex));

        return true;
    }

    @Unique
    private void handleProfileTyping() {
        String current = textBox.getText();

        if (!current.isEmpty() && !current.equals(MultipleServerLists.currentServerListFile)) {
            loadProfile(current);
        }
    }

    @Unique
    private void handleEnterCreate() {
        if (!textBox.isFocused()) {
            return;
        }

        if (GLFW.glfwGetKey(
            client.getWindow().getHandle(),
            GLFW.GLFW_KEY_ENTER
        ) != GLFW.GLFW_PRESS) {
            return;
        }

        String name = textBox.getText();

        if (name == null || name.isEmpty()) {
            return;
        }

        File file = new File(serversDir, name);

        try {
            if (!file.exists()) {
                file.createNewFile();
                rebuildDropdown();
            }
        } catch (Exception ignored) {
        }
    }

    @Unique
    private void handleHoverSelection() {
        double mouseX = client.mouse.getX() * width / client.getWindow().getWidth();
        double mouseY = client.mouse.getY() * height / client.getWindow().getHeight();

        if (mouseX != lastMouseX || mouseY != lastMouseY) {
            lastMouseMoveTime = System.currentTimeMillis();
            lastMouseX = mouseX;
            lastMouseY = mouseY;
        }

        boolean mouseIdle = System.currentTimeMillis() - lastMouseMoveTime > MOUSE_IDLE_MS;

        boolean hoverText = textBox.isMouseOver(mouseX, mouseY);

        String hoveredProfile = null;

        for (ButtonWidget btn : dropdownButtons) {
            if (btn.isMouseOver(mouseX, mouseY)) {
                hoveredProfile = btn.getMessage().getString();
                break;
            }
        }

        dropdownTargetOpen = hoverText || hoveredProfile != null;

        if (
            mouseIdle &&
            hoveredProfile != null &&
            !hoveredProfile.equals(lastHoveredProfile)
        ) {
            lastHoveredProfile = hoveredProfile;
            selectProfile(hoveredProfile);
        }

        if (hoveredProfile == null) {
            lastHoveredProfile = "";
        }
    }

    @Unique
    private void updateAnimation() {

        textBox.setSuggestion(
            textBox.getText().isEmpty() ? "General" : ""
        );

        dropdownProgress += dropdownTargetOpen ? ANIM_SPEED : -ANIM_SPEED;

        if (dropdownProgress < 0f) {
            dropdownProgress = 0f;
        } else if (dropdownProgress > 1f) {
            dropdownProgress = 1f;
        }

        layoutButtons();
    }

    @Unique
    private void layoutButtons() {
        float t = easeOut(dropdownProgress);

        boolean visible = dropdownProgress > 0.01f;
        boolean active = dropdownProgress > 0.5f;

        for (int i = 0; i < dropdownButtons.size(); i++) {
            ButtonWidget btn = dropdownButtons.get(i);

            int targetY = DROPDOWN_Y + (i * BOX_H);

            int y = (int) (BOX_Y + ((targetY - BOX_Y) * t));

            btn.setX(BOX_X);
            btn.setY(y);

            btn.visible = visible;
            btn.active = active;
        }
    }

    @Unique
    private float easeOut(float t) {
        return 1f - (float) Math.pow(1f - t, 3);
    }

    @Unique
    private void rebuildDropdown() {
        for (ButtonWidget button : dropdownButtons) {
            remove(button);
        }

        dropdownButtons.clear();
        profiles.clear();

        scrollIndex = -1;

        File[] files = serversDir.listFiles();

        if (files != null) {
            for (File file : files) {
                String name = file.getName();

                if (!name.endsWith(".old")) {
                    profiles.add(name);
                }
            }
        }

        int y = DROPDOWN_Y;

        for (String profile : profiles) {
            ButtonWidget btn = ButtonWidget.builder(
                Text.literal(profile),
                b -> {
                    selectProfile(profile);
                    dropdownTargetOpen = false;
                }
            ).dimensions(
                BOX_X,
                y,
                BOX_W,
                BOX_H
            ).build();

            dropdownButtons.add(btn);

            addDrawableChild(btn);
            addSelectableChild(btn);

            y += BOX_H;
        }

        layoutButtons();
    }

    @Unique
    private void selectProfile(String profile) {
        textBox.setText(profile);
        loadProfile(profile);

        scrollIndex = profiles.indexOf(profile);
    }

    @Unique
    private void loadProfile(String profile) {
        if (client == null) {
            return;
        }

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