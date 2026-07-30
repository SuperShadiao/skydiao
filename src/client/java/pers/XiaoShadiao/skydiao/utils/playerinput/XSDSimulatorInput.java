package pers.XiaoShadiao.skydiao.utils.playerinput;

import net.minecraft.client.Options;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import pers.XiaoShadiao.skydiao.eventbuslistener.AbstractListener;
import pers.XiaoShadiao.skydiao.eventbuslistener.FreecamAndFreelook;

public class XSDSimulatorInput extends KeyboardInput {

    public XSDSimulatorInput(Options options) {
        super(options);
        InputSimulator.forward = false;
        InputSimulator.backward = false;
        InputSimulator.left = false;
        InputSimulator.right = false;
        InputSimulator.jump = false;
        InputSimulator.shift = false;
        InputSimulator.sprint = false;
    }

    private static float calculateImpulse(boolean bl, boolean bl2) {
        if (bl == bl2) {
            return 0.0F;
        } else {
            return bl ? 1.0F : -1.0F;
        }
    }

    @Override
    public void tick() {
        super.tick();
        boolean flag = !InputSimulator.isInventoryOpen();
        FreecamAndFreelook.CameraEntity cameraEntity = AbstractListener.freecamAndFreelook.getCameraEntity();
        boolean disableMove = cameraEntity != null && cameraEntity.getCameraType() == FreecamAndFreelook.CameraEntity.CameraType.FREECAM;
        this.keyPresses = new Input(
                (!disableMove && this.keyPresses.forward()) || (flag && InputSimulator.forward),
                (!disableMove && this.keyPresses.backward()) || (flag && InputSimulator.backward),
                (!disableMove && this.keyPresses.left()) || (flag && InputSimulator.left),
                (!disableMove && this.keyPresses.right()) || (flag && InputSimulator.right),
                (!disableMove && this.keyPresses.jump()) || (flag && InputSimulator.jump),
                (!disableMove && this.keyPresses.shift()) || (flag && InputSimulator.shift),
                (!disableMove && this.keyPresses.sprint()) || (flag && InputSimulator.sprint)
        );
        float f = calculateImpulse(this.keyPresses.forward(), this.keyPresses.backward());
        float g = calculateImpulse(this.keyPresses.left(), this.keyPresses.right());
        this.moveVector = new Vec2(g, f).normalized();
    }

    @Override
    public void makeJump() {
        super.makeJump();
        boolean flag = !InputSimulator.isInventoryOpen();
        this.keyPresses = new Input(
                this.keyPresses.forward() || (flag && InputSimulator.forward),
                this.keyPresses.backward() || (flag && InputSimulator.backward),
                this.keyPresses.left() || (flag && InputSimulator.left),
                this.keyPresses.right() || (flag && InputSimulator.right),
                this.keyPresses.jump() || (flag && InputSimulator.jump),
                this.keyPresses.shift() || (flag && InputSimulator.shift),
                this.keyPresses.sprint() || (flag && InputSimulator.sprint)
        );
    }
}
