package smilerryan.msl.mixin;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.ServerList;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {
    @Shadow private ServerList serverList;
    @Shadow private net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget serverListWidget;

    @Unique private TextFieldWidget msl$textBox;
    @Unique private String msl$lastValue = null;
    
    // Profiles list storage tracking
    @Unique private final List<String> msl$discoveredProfiles = new ArrayList<>();
    @Unique private int msl$currentProfileIndex = 0;

    protected MultiplayerScreenMixin(Text title) { super(title); }

    @Inject(method = "init", at = @At("TAIL"))
    private void addTextBox(CallbackInfo ci) {
        // Restored full width layout bounds since side buttons are removed
        msl$textBox = new TextFieldWidget(this.textRenderer, 4, this.height - 24, 150, 20, Text.literal(""));
        msl$textBox.setMaxLength(256);
        this.addDrawableChild(msl$textBox);

        // Scan files on layout preparation
        msl$scanForProfiles();
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void updateTextBoxPosition(CallbackInfo ci) {
        if (msl$textBox == null) return;

        msl$textBox.setDimensionsAndPosition(150, 20, 4, this.height - 24);

        String current = msl$textBox.getText();
        if (!current.equals(msl$lastValue)) {
            msl$lastValue = current;
            String sanitized = current.replaceAll("[^A-Za-z0-9]", "-");
            String fileName = sanitized.isEmpty() ? "servers.dat" : "servers-" + sanitized + ".dat";

            msl$reloadServerList(fileName);
        }
    }

    /**
     * Intercepts standard mouse scrolls on the screen level.
     * Horizontal scroll parameter is ignored for this approach.
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        // Verify text field exists and check if mouse bounds align with it
        if (msl$textBox != null && msl$textBox.isMouseOver(mouseX, mouseY)) {
            // Update list scan reactively in case files were altered out of game
            msl$scanForProfiles();

            if (!msl$discoveredProfiles.isEmpty()) {
                // If scrolling down (negative amount), advance index forward. Up (positive amount) goes backward.
                int direction = verticalAmount < 0 ? 1 : -1;
                
                msl$currentProfileIndex = (msl$currentProfileIndex + direction + msl$discoveredProfiles.size()) % msl$discoveredProfiles.size();
                msl$applySelectedProfile();
                return true; // Consume event so background widgets don't also scroll
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Unique
    private void msl$scanForProfiles() {
        if (this.client == null) return;
        
        msl$discoveredProfiles.clear();
        msl$discoveredProfiles.add("servers.dat");

        File runDir = this.client.runDirectory;
        if (runDir != null && runDir.exists()) {
            String[] files = runDir.list((dir, name) -> name.startsWith("servers-") && name.endsWith(".dat"));
            if (files != null) {
                for (String file : files) {
                    if (!msl$discoveredProfiles.contains(file)) {
                        msl$discoveredProfiles.add(file);
                    }
                }
            }
        }
        
        String currentText = msl$textBox != null ? msl$textBox.getText() : "";
        String currentFileName = currentText.isEmpty() ? "servers.dat" : "servers-" + currentText.replaceAll("[^A-Za-z0-9]", "-") + ".dat";
        int matchIdx = msl$discoveredProfiles.indexOf(currentFileName);
        msl$currentProfileIndex = (matchIdx != -1) ? matchIdx : 0;
    }

    @Unique
    private void msl$applySelectedProfile() {
        if (msl$currentProfileIndex >= msl$discoveredProfiles.size() || msl$currentProfileIndex < 0) return;
        
        String targetFile = msl$discoveredProfiles.get(msl$currentProfileIndex);
        String profileName = "";
        
        if (targetFile.startsWith("servers-") && targetFile.endsWith(".dat")) {
            profileName = targetFile.substring(8, targetFile.length() - 4);
        }
        
        if (msl$textBox != null) {
            msl$textBox.setText(profileName);
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private void msl$reloadServerList(String fileName) {
        if (this.client == null) return;

        final java.io.File customFile = new java.io.File(this.client.runDirectory, fileName);

        ServerList newList = new ServerList(this.client) {
            @Override
            public void loadFile() {
                try {
                    java.lang.reflect.Field serversField = ServerList.class.getDeclaredField("field_3749");
                    serversField.setAccessible(true);
                    java.util.List<net.minecraft.client.network.ServerInfo> internalServers = 
                        (java.util.List<net.minecraft.client.network.ServerInfo>) serversField.get(this);
                    
                    internalServers.clear();
                    
                    if (!customFile.exists()) {
                        return;
                    }

                    net.minecraft.nbt.NbtCompound nbtCompound = null;
                    try {
                        nbtCompound = net.minecraft.nbt.NbtIo.readCompressed(
                            customFile.toPath(), 
                            net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes()
                        );
                    } catch (java.util.zip.ZipException e) {
                        try {
                            nbtCompound = net.minecraft.nbt.NbtIo.read(customFile.toPath());
                        } catch (Exception uncompressedException) {
                            uncompressedException.printStackTrace();
                        }
                    }
                    
                    if (nbtCompound != null) {
                        net.minecraft.nbt.NbtList nbtList = nbtCompound.getList("servers").orElse(null);
                        
                        if (nbtList != null) {
                            for (int i = 0; i < nbtList.size(); ++i) {
                                net.minecraft.nbt.NbtElement element = nbtList.get(i);
                                
                                if (element instanceof net.minecraft.nbt.NbtCompound serverData) {
                                    net.minecraft.client.network.ServerInfo info = 
                                        net.minecraft.client.network.ServerInfo.fromNbt(serverData);
                                    if (info != null) {
                                        internalServers.add(info);
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void saveFile() {
                try {
                    java.lang.reflect.Field serversField = ServerList.class.getDeclaredField("field_3749");
                    serversField.setAccessible(true);
                    java.util.List<net.minecraft.client.network.ServerInfo> internalServers = 
                        (java.util.List<net.minecraft.client.network.ServerInfo>) serversField.get(this);

                    net.minecraft.nbt.NbtList nbtList = new net.minecraft.nbt.NbtList();
                    for (net.minecraft.client.network.ServerInfo serverInfo : internalServers) {
                        nbtList.add(serverInfo.toNbt());
                    }

                    net.minecraft.nbt.NbtCompound nbtCompound = new net.minecraft.nbt.NbtCompound();
                    nbtCompound.put("servers", nbtList);

                    net.minecraft.nbt.NbtIo.writeCompressed(nbtCompound, customFile.toPath());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };

        this.serverList = newList;
        this.serverList.loadFile();

        if (this.serverListWidget != null) {
            this.serverListWidget.setSelected(null); 
            this.serverListWidget.setServers(this.serverList);
        }
    }
}