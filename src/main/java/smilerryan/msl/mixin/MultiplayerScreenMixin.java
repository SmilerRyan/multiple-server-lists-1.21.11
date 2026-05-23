package smilerryan.msl.mixin;

import com.google.gson.*;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.option.ServerList;
import net.minecraft.nbt.*;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Mixin(MultiplayerScreen.class)
public abstract class MultiplayerScreenMixin extends Screen {

    @Shadow private ServerList serverList;
    @Shadow private net.minecraft.client.gui.screen.multiplayer.MultiplayerServerListWidget serverListWidget;

    @Unique private TextFieldWidget msl$textBox;
    @Unique private ButtonWidget msl$btn;

    @Unique private final List<String> msl$profiles = new ArrayList<>();
    @Unique private int msl$idx = 0;

    @Unique private String msl$last = null;
    @Unique private boolean msl$json = true;

    protected MultiplayerScreenMixin(Text t) { super(t); }

    @Inject(method="init", at=@At("TAIL"))
    private void init(CallbackInfo ci) {

        msl$btn = ButtonWidget.builder(Text.literal("J"), b -> {
            String p = msl$p(msl$textBox.getText());
            msl$convert(p);
            msl$load(p);
            msl$scan();
        }).dimensions(4, this.height - 24, 16, 16).build();

        msl$textBox = new TextFieldWidget(this.textRenderer, 22, this.height - 24, 140, 16, Text.literal(""));
        msl$textBox.setSuggestion("All Servers");

        this.addDrawableChild(msl$btn);
        this.addDrawableChild(msl$textBox);

        msl$scan();
    }

    @Unique
    private void msl$convert(String profile) {

        try {
            File jsonFile = msl$j(profile);
            File datFile = msl$d(profile);

            if (jsonFile.exists()) {

                JsonObject root;
                try (FileReader r = new FileReader(jsonFile)) {
                    root = JsonParser.parseReader(r).getAsJsonObject();
                }

                NbtList list = new NbtList();

                if (root.has("servers")) {
                    for (JsonElement el : root.getAsJsonArray("servers")) {
                        list.add(msl$jn(el.getAsJsonObject()));
                    }
                }

                NbtCompound out = new NbtCompound();
                out.put("servers", list);

                NbtIo.writeCompressed(out, datFile.toPath());
                jsonFile.delete();

                msl$json = true;
                return;
            }

            if (datFile.exists()) {

                NbtCompound root = null;

                try {
                    root = NbtIo.readCompressed(
                            datFile.toPath(),
                            NbtSizeTracker.ofUnlimitedBytes()
                    );
                } catch (Exception ignored) {}

                if (root == null) {
                    try {
                        root = NbtIo.read(datFile.toPath());
                    } catch (Exception ignored) {}
                }

                if (root == null) return;

                JsonObject out = new JsonObject();
                JsonArray arr = new JsonArray();

                NbtList list = root.getList("servers").orElse(null);

                if (list != null) {
                    for (int i = 0; i < list.size(); i++) {
                        if (list.get(i) instanceof NbtCompound c) {
                            arr.add(msl$nbtToJson(c));
                        }
                    }
                }

                out.add("servers", arr);

                try (FileWriter w = new FileWriter(jsonFile)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(out, w);
                }

                datFile.delete();

                msl$json = false;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Inject(method="tick", at=@At("TAIL"))
    private void tick(CallbackInfo ci) {

        if (msl$textBox == null || msl$btn == null) return;

        String cur = msl$textBox.getText();

        if (cur.isEmpty()) {
            msl$textBox.setSuggestion("All Servers");
        } else {
            msl$textBox.setSuggestion(null);
        }

        int textW = this.textRenderer.getWidth(cur.isEmpty() ? "All Servers" : cur);
        int width = Math.max(60, textW + 10);

        msl$textBox.setDimensionsAndPosition(width, 16, 22, this.height - 24);
        msl$btn.setPosition(4, this.height - 24);

        String p = msl$p(cur);

        if (msl$last == null || !msl$last.equals(p)) {
            msl$last = p;
            msl$load(p);
        }

        // button color update (JSON = green, DAT/NBT = red)
        if (msl$btn != null) {
            msl$btn.active = true;
            msl$btn.setMessage(Text.literal("J").styled(style -> {
                return style.withColor(msl$json ? 0x00FF00 : 0xFF0000);
            }));
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double h, double v) {

        if (msl$textBox == null || !msl$textBox.isMouseOver(x, y))
            return super.mouseScrolled(x, y, h, v);

        msl$scan();
        if (msl$profiles.isEmpty()) return true;

        int d = v < 0 ? 1 : -1;
        msl$idx = (msl$idx + d + msl$profiles.size()) % msl$profiles.size();

        msl$apply();
        return true;
    }

    @Unique
    private void msl$apply() {

        if (msl$profiles.isEmpty()) return;

        String p = msl$profiles.get(msl$idx);
        String v = p.equals("servers") ? "" : p.replace("servers-", "");

        msl$textBox.setText(v);

        msl$last = null;
        msl$load(msl$p(v));
    }

    @Unique
    private String msl$p(String s) {
        s = s.replaceAll("[^A-Za-z0-9]", "-");
        return s.isEmpty() ? "servers" : "servers-" + s;
    }

    @Unique private File msl$j(String p){ return new File(this.client.runDirectory, p+".json"); }
    @Unique private File msl$d(String p){ return new File(this.client.runDirectory, p+".dat"); }

    @Unique
    private void msl$load(String p) {

        if (this.client == null) return;

        File j = msl$j(p);
        File d = msl$d(p);

        ServerList l = new ServerList(this.client) {

            @Override
            public void loadFile() {
                try {

                    List<ServerInfo> s = msl$list(this);
                    s.clear();

                    if (j.exists()) {

                        try (FileReader r = new FileReader(j)) {

                            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();

                            for (JsonElement e : root.getAsJsonArray("servers")) {
                                ServerInfo info = ServerInfo.fromNbt(msl$jn(e.getAsJsonObject()));
                                if (info != null) s.add(info);
                            }
                        }

                        msl$json = true;
                        return;
                    }

                    if (d.exists()) {

                        NbtCompound root;
                        try {
                            root = NbtIo.readCompressed(d.toPath(), NbtSizeTracker.ofUnlimitedBytes());
                        } catch (Exception e) {
                            root = NbtIo.read(d.toPath());
                        }

                        NbtList list = root.getList("servers").orElse(null);
                        if (list == null) return;

                        for (int i=0;i<list.size();i++)
                            if (list.get(i) instanceof NbtCompound c) {
                                ServerInfo info = ServerInfo.fromNbt(c);
                                if (info != null) s.add(info);
                            }

                        msl$json = false;
                    }

                } catch (Exception ignored) {}
            }

            @Override
            public void saveFile() {
                try {

                    List<ServerInfo> s = msl$list(this);

                    if (msl$json) {

                        JsonObject root = new JsonObject();
                        JsonArray arr = new JsonArray();

                        for (ServerInfo i : s)
                            arr.add(msl$nbtToJson(i.toNbt()));

                        root.add("servers", arr);

                        try (FileWriter w = new FileWriter(j)) {
                            new Gson().toJson(root, w);
                        }
                        return;
                    }

                    NbtList list = new NbtList();
                    for (ServerInfo i : s) list.add(i.toNbt());

                    NbtCompound root = new NbtCompound();
                    root.put("servers", list);

                    NbtIo.writeCompressed(root, d.toPath());

                } catch (Exception ignored) {}
            }
        };

        this.serverList = l;
        this.serverList.loadFile();

        if (this.serverListWidget != null) {
            this.serverListWidget.setSelected(null);
            this.serverListWidget.setServers(this.serverList);
        }
    }

    @Unique
    private void msl$scan() {

        msl$profiles.clear();

        File[] f = this.client.runDirectory.listFiles();
        if (f == null) return;

        for (File x : f) {
            String n = x.getName();
            if (n.startsWith("servers-") && (n.endsWith(".json") || n.endsWith(".dat")))
                msl$profiles.add(n.replace(".json","").replace(".dat",""));
        }

        if (!msl$profiles.contains("servers"))
            msl$profiles.add(0, "servers");

        String cur = msl$p(msl$textBox != null ? msl$textBox.getText() : "");
        msl$idx = Math.max(0, msl$profiles.indexOf(cur));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private List<ServerInfo> msl$list(ServerList l) throws Exception {
        Field f = ServerList.class.getDeclaredField("field_3749");
        f.setAccessible(true);
        return (List<ServerInfo>) f.get(l);
    }

    @Unique
    private JsonObject msl$nbtToJson(NbtCompound n) {
        JsonObject o = new JsonObject();
        for (String k : n.getKeys()) {
            NbtElement e = n.get(k);
            if (e == null) continue;
            o.addProperty(k, e.asString().orElse(""));
        }
        return o;
    }

    @Unique
    private NbtCompound msl$jn(JsonObject o) {
        NbtCompound n = new NbtCompound();
        for (String k : o.keySet())
            n.putString(k, o.get(k).getAsString());
        return n;
    }
}