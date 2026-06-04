package pers.XiaoShadiao.skydiao.mixin.client.blivemode;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import pers.XiaoShadiao.skydiao.config.ConfigManager;
import pers.XiaoShadiao.skydiao.utils.StatusManager;
import pers.XiaoShadiao.skydiao.utils.blivesensitiveword.ServerIdSpoofer;

import java.util.function.Consumer;

@Mixin(PlayerTeam.class)
public class MixinScoreboardRender {

    @WrapMethod(method = "formatNameForTeam")
    private static MutableComponent format(Team team, Component component, Operation<MutableComponent> original) {
        MutableComponent call = original.call(team, component);

        if(!ConfigManager.blivemodehideserverid.getValue()) return call;
        if(call.getSiblings().size() != 3 || call.getContents() != PlainTextContents.EMPTY) return call;

        MutableComponent empty = Component.empty();
        empty.setStyle(call.getStyle());
        call.getSiblings().forEach(new Consumer<>() {

            public final String smallServerId = String.valueOf(StatusManager.get().getSmallServerID());
            public boolean replacedServerId = false;
            public int i;

            @Override
            public void accept(Component ele) {
                ComponentContents contents = replacedServerId ? PlainTextContents.EMPTY : ele.getContents();
                ComponentContents original = contents;
                if(i == 0) {
                    if (contents instanceof PlainTextContents plainTextContents) {
                        String text = plainTextContents.text();
                        int lastSpace = text.lastIndexOf(' ') + 3;
                        if(lastSpace < text.length()) {
                            String subString = text.substring(lastSpace);
                            if(smallServerId.startsWith(subString)) {
                                if(StatusManager.get().isMiniServer()) {
                                    contents = PlainTextContents.create(text.substring(0, lastSpace) + ServerIdSpoofer.getSmallMiniServerId());
                                } else if(StatusManager.get().isMegaServer()) {
                                    contents = PlainTextContents.create(text.substring(0, lastSpace) + ServerIdSpoofer.getSmallMegaServerId());
                                }
                            }
                        }
                    }
                }
                if(original != contents) {
                    replacedServerId = true;
                }
                MutableComponent component1 = MutableComponent.create(contents);
                component1.setStyle(ele.getStyle());
                empty.append(component1);
                i++;
            }

        });

        return empty;
    }

}