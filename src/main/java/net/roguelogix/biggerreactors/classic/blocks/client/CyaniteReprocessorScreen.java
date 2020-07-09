package net.roguelogix.biggerreactors.classic.blocks.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.fluid.Fluids;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.roguelogix.biggerreactors.BiggerReactors;
import net.roguelogix.biggerreactors.classic.blocks.CyaniteReprocessorContainer;
import net.roguelogix.biggerreactors.client.gui.GuiEnergyTank;
import net.roguelogix.biggerreactors.client.gui.GuiFluidTank;

@OnlyIn(Dist.CLIENT)
public class CyaniteReprocessorScreen extends ContainerScreen<CyaniteReprocessorContainer> implements IHasContainer<CyaniteReprocessorContainer> {
    
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(BiggerReactors.modid, "textures/screen/cyanite_reprocessor.png");

    private GuiEnergyTank<CyaniteReprocessorContainer> energyTank;
    private GuiFluidTank<CyaniteReprocessorContainer> fluidTank;

    public CyaniteReprocessorScreen(CyaniteReprocessorContainer container, PlayerInventory inventory, ITextComponent title) {
        super(container, inventory, title);
        this.xSize = 246;
        this.ySize = 175;

        this.energyTank = new GuiEnergyTank<>(this, 148, 16, true);
        this.fluidTank = new GuiFluidTank<>(this, 8, 16, Fluids.WATER.getFluid(), true);
    }

    @Override
    // 1.16: func_230430_a_
    public void render(int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(); // 1.16: this.func_230446_a_
        // This really bothers me how the super is sandwiched here but it has to be here so ugh.
        super.render(mouseX, mouseY, partialTicks); // 1.16: super.func_230430_a_
        this.renderHoveredToolTip(mouseX, mouseY);  // 1.16: this.func_230459_a_

        this.energyTank.drawTooltip(mouseX, mouseY, this.getContainer().getEnergyStored(), this.getContainer().getEnergyCapacity());
        this.fluidTank.drawTooltip(mouseX, mouseY, this.getContainer().getFluidStored(), this.getContainer(). getFluidCapacity());
    }

    @Override
    // 1.16: func_230451_b_
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        this.font.drawString(this.title.getFormattedText(), 8.0F, (float) (this.ySize - 168), 4210752);
          // 1.16: this.field_230712_o_.func_238422_b_
        this.font.drawString(this.playerInventory.getDisplayName().getFormattedText(), 8.0F, (float) (this.ySize - 94), 4210752);
          // 1.16: this.field_230712_o_.func_238422_b_

        this.energyTank.drawPart(this.getContainer().getEnergyStored(), this.getContainer().getEnergyCapacity());
        this.fluidTank.drawPart(this.getContainer().getFluidStored(), this.getContainer().getFluidCapacity());
    }

    @Override
    // 1.16: func_230450_a_
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);

        assert this.minecraft != null;
        this.minecraft.getTextureManager().bindTexture(GUI_TEXTURE); // 1.16: field_230706_i_

        int relativeX = (this.width - this.xSize) / 2; // 1.16: field_230708_k_
        int relativeY = (this.height - this.ySize) / 2; // 1.16: field_230709_l_

        this.blit(relativeX, relativeY, 0, 0, this.xSize, this.ySize); // 1.16: func_238474_b_
    }
}