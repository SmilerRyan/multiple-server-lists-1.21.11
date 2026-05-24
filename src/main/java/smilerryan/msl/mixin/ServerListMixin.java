package smilerryan.msl.mixin;

import smilerryan.msl.MultipleServerLists;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Constant;

@Mixin(net.minecraft.client.option.ServerList.class)
public class ServerListMixin {

    @ModifyConstant(
        method = {"loadFile", "saveFile"},
        constant = @Constant(stringValue = "servers.dat")
    )
    private String redirect_1(String original) {
        return MultipleServerLists.currentServerListFile;
    }

    @ModifyConstant(
        method = {"loadFile", "saveFile"},
        constant = @Constant(stringValue = "servers.dat_old")
    )
    private String redirect_2(String original) {
        return MultipleServerLists.currentServerListFile + ".old";
    }

}