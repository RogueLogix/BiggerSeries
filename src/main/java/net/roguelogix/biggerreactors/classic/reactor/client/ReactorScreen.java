package net.roguelogix.biggerreactors.classic.reactor.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.IHasContainer;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.roguelogix.biggerreactors.BiggerReactors;
import net.roguelogix.biggerreactors.classic.reactor.ReactorContainer;
import net.roguelogix.biggerreactors.classic.reactor.ReactorDatapack;
import net.roguelogix.biggerreactors.client.gui.GuiEnergyTank;
import net.roguelogix.biggerreactors.client.gui.GuiReactorBar;
import net.roguelogix.phosphophyllite.gui.GuiPartSymbol;

@OnlyIn(Dist.CLIENT)
public class ReactorScreen extends ContainerScreen<ReactorContainer> implements IHasContainer<ReactorContainer> {

  private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(BiggerReactors.modid, "textures/screen/reactor_terminal.png");

  // String status symbols.
  private GuiPartSymbol<ReactorContainer> coreHeatSymbol;
  private GuiPartSymbol<ReactorContainer> outputSymbol;
  private GuiPartSymbol<ReactorContainer> fuelRateSymbol;
  private GuiPartSymbol<ReactorContainer> reactivitySymbol;

  // Upper gauges.
  // Needs modification/generalization to work with fuel tank, will do later.
  private GuiPartSymbol<ReactorContainer> fuelSymbol;
  //private GuiReactorBar<ReactorContainer> fuelTank;
  private GuiPartSymbol<ReactorContainer> caseHeatSymbol;
  private GuiReactorBar<ReactorContainer> caseHeatTank;
  private GuiPartSymbol<ReactorContainer> fuelHeatSymbol;
  private GuiReactorBar<ReactorContainer> fuelHeatTank;
  private GuiPartSymbol<ReactorContainer> energySymbol;
  private GuiEnergyTank<ReactorContainer> energyTank;

  // Lower gauges.

  public ReactorScreen(ReactorContainer container, PlayerInventory inventory, ITextComponent title) {
    super(container, inventory, title);
    this.xSize = 176;
    this.ySize = 186;

    // String status symbols.
    this.coreHeatSymbol = new GuiPartSymbol<>(this, 6, (this.ySize - 170), 0, new TranslationTextComponent("").getFormattedText());
    this.outputSymbol = new GuiPartSymbol<>(this, 6, (this.ySize - 149), 3, new TranslationTextComponent("").getFormattedText());
    this.fuelRateSymbol = new GuiPartSymbol<>(this, 6, (this.ySize - 128), 4, new TranslationTextComponent("").getFormattedText());
    this.reactivitySymbol = new GuiPartSymbol<>(this, 6, (this.ySize - 107), 5, new TranslationTextComponent("").getFormattedText());

    // Upper gauges.
    this.fuelSymbol = new GuiPartSymbol<>(this, 88, 5, 6, new TranslationTextComponent("").getFormattedText());
    //this.fuelTank = new GuiReactorBar<>(this, 88, 22, 3);
    this.caseHeatSymbol = new GuiPartSymbol<>(this, 110, 5, 7, new TranslationTextComponent("").getFormattedText());
    this.caseHeatTank = new GuiReactorBar<>(this, 110, 22, 2);
    this.fuelHeatSymbol = new GuiPartSymbol<>(this, 132, 5, 8, new TranslationTextComponent("").getFormattedText());
    this.fuelHeatTank = new GuiReactorBar<>(this, 132, 22, 2);
    this.energySymbol = new GuiPartSymbol<>(this, 154, 5, 9, new TranslationTextComponent("").getFormattedText());
    this.energyTank = new GuiEnergyTank<>(this, 154, 22);
  }

  @Override
  // 1.16: func_230430_a_
  public void render(int mouseX, int mouseY, float partialTicks) {
    this.renderBackground(); // 1.16: this.func_230446_a_

    // Normally, we'd call super.render(), but we don't use the inventory in this screen..
    this.drawGuiContainerBackgroundLayer(partialTicks, mouseX, mouseY);
    RenderSystem.pushMatrix();
    RenderSystem.translatef(this.guiLeft, this.guiTop, 0);
    this.drawGuiContainerForegroundLayer(mouseX, mouseY);
    RenderSystem.popMatrix();

    this.renderHoveredToolTip(mouseX, mouseY);  // 1.16: this.func_230459_a_

    ReactorDatapack reactorData = this.getContainer().getReactorData();

    // String status symbols.
    this.coreHeatSymbol.drawTooltip(mouseX, mouseY);
    this.outputSymbol.drawTooltip(mouseX, mouseY);
    this.fuelRateSymbol.drawTooltip(mouseX, mouseY);
    this.reactivitySymbol.drawTooltip(mouseX, mouseY);

    // Upper gauges.
    this.fuelSymbol.drawTooltip(mouseX, mouseY);
    //this.fuelTank.drawTooltip(mouseX, mouseY, _, _);
    this.caseHeatSymbol.drawTooltip(mouseX, mouseY);
    this.caseHeatTank.drawTooltip(mouseX, mouseY, reactorData.caseHeatStored, reactorData.caseHeatCapacity);
    this.fuelHeatSymbol.drawTooltip(mouseX, mouseY);
    this.fuelHeatTank.drawTooltip(mouseX, mouseY, reactorData.fuelHeatStored, reactorData.fuelHeatCapacity);
    this.energySymbol.drawTooltip(mouseX, mouseY);
    this.energyTank.drawTooltip(mouseX, mouseY, reactorData.energyStored, reactorData.energyCapacity);
  }

  private void drawCoreHeat(ReactorDatapack reactorData) {
    this.coreHeatSymbol.drawPart();
    this.font.drawString(String.format("%d C", reactorData.coreHeatStored), 25.0F, (float) (this.ySize - 165), 4210752);
  }

  private void drawReactorOutput(ReactorDatapack reactorData) {
    if(reactorData.reactorType) {
      // Active reactor, display as steam.
      this.outputSymbol.updateTextureIndex(2);
      this.outputSymbol.drawPart();
      this.font.drawString(String.format("%d mB/t", reactorData.reactorOutputRate), 25.0F, (float) (this.ySize - 144), 4210752);
    } else {
      // Passive reactor, display as energy.
      this.outputSymbol.updateTextureIndex(3);
      this.outputSymbol.drawPart();
      this.font.drawString(String.format("%d RF/t", reactorData.reactorOutputRate), 25.0F, (float) (this.ySize - 144), 4210752);
    }
  }

  private void drawFuelRate(ReactorDatapack reactorData) {
    this.fuelRateSymbol.drawPart();
    this.font.drawString(String.format("%.1f mB/t", reactorData.fuelUsageRate), 25.0F, (float) (this.ySize - 123), 4210752);
  }

  private void drawReactivity(ReactorDatapack reactorData) {
    this.reactivitySymbol.drawPart();
    this.font.drawString(String.format("%.1f%%", reactorData.reactivityRate), 25.0F, (float) (this.ySize - 103), 4210752);
  }

  private void drawReactorStatus(ReactorDatapack reactorData) {
    if(reactorData.reactorStatus) {
      // Reactor is online.
      this.font.drawString("Status: \u00A72Online", 6.0F, (float) (this.ySize - 84), 4210752);
    } else {
      // Reactor is offline.
      this.font.drawString("Status: \u00A74Offline", 6.0F, (float) (this.ySize - 84), 4210752);
    }
  }

  private void drawReactorGauges(ReactorDatapack reactorData) {
    this.fuelSymbol.drawPart();
    //this.fuelTank.drawPart(_, _);
    this.caseHeatSymbol.drawPart();
    this.caseHeatTank.drawPart(5000, reactorData.caseHeatCapacity);
    this.fuelHeatSymbol.drawPart();
    this.fuelHeatTank.updateTextureIndex(1);
    this.fuelHeatTank.drawPart(reactorData.fuelHeatStored, reactorData.fuelHeatCapacity);
    this.energySymbol.drawPart();
    this.energyTank.drawPart(reactorData.energyStored, reactorData.energyCapacity);
  }

  @Override
  // 1.16: func_230451_b_
  protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
    ReactorDatapack reactorData = this.getContainer().getReactorData();
    // TODO: This label doesn't quite fit on screen correctly, may need to modify texture a little.
    //this.font.drawString(this.title.getFormattedText(), 8.0F, (float) (this.ySize - 168), 4210752);

    this.drawCoreHeat(reactorData);
    this.drawReactorOutput(reactorData);
    this.drawFuelRate(reactorData);
    this.drawReactivity(reactorData);
    this.drawReactorStatus(reactorData);
    this.drawReactorGauges(reactorData);
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
