package net.roguelogix.biggerreactors.client.turbine;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.inventory.ContainerScreen;
import net.minecraft.inventory.container.Container;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundCategory;
import net.minecraft.util.SoundEvents;
import net.minecraft.util.text.TranslationTextComponent;
import net.roguelogix.biggerreactors.BiggerReactors;
import net.roguelogix.biggerreactors.classic.turbine.VentState;
import net.roguelogix.biggerreactors.classic.turbine.containers.TurbineContainer;
import net.roguelogix.phosphophyllite.gui.GuiPartBase;
import net.roguelogix.phosphophyllite.gui.GuiRenderHelper;
import net.roguelogix.phosphophyllite.gui.api.IHasButton;
import net.roguelogix.phosphophyllite.gui.api.IHasTooltip;

import java.util.Arrays;

import static org.lwjgl.glfw.GLFW.*;

public class GuiTurbineVentStateToggle<T extends Container> extends GuiPartBase<T> implements IHasTooltip, IHasButton {
    
    private final ResourceLocation texture = new ResourceLocation(BiggerReactors.modid, "textures/screen/parts/gui_symbols.png");
    private boolean debounce = false;
    private VentState ventState;
    
    /**
     * @param screen The screen this instance belongs to.
     * @param xPos   The X position of the part.
     * @param yPos   The Y position of the part.
     * @param xSize  The width of the part.
     * @param ySize  The height of the part.
     */
    public GuiTurbineVentStateToggle(ContainerScreen<T> screen, int xPos, int yPos, int xSize, int ySize) {
        super(screen, xPos, yPos, xSize, ySize);
    }
    
    public void updateState(VentState ventState) {
        this.ventState = ventState;
    }
    
    /**
     * Render this element.
     */
    @Override
    public void drawPart() {
        // Reset and bind texture.
        super.drawPart();
        GuiRenderHelper.setTexture(this.texture);
        
        // Draw button.
        if (this.ventState == VentState.OVERFLOW) {
            GuiRenderHelper.setTextureOffset(32, 48);
        } else if (this.ventState == VentState.ALL) {
            GuiRenderHelper.setTextureOffset(16, 48);
        } else {
            GuiRenderHelper.setTextureOffset(0, 48);
        }
        GuiRenderHelper.draw(this.xPos, this.yPos, this.screen.getBlitOffset(), this.xSize, this.ySize);
    }
    
    @Override
    public void drawTooltip(int mouseX, int mouseY) {
        if (this.isHovering(mouseX, mouseY)) {
            if (this.ventState == VentState.OVERFLOW) {
                this.screen.renderTooltip(Arrays.asList(new TranslationTextComponent("tooltip.biggerreactors.buttons.turbine.vent_state.overflow").getFormattedText().split("\\n")), mouseX, mouseY);
            } else if (this.ventState == VentState.ALL) {
                this.screen.renderTooltip(Arrays.asList(new TranslationTextComponent("tooltip.biggerreactors.buttons.turbine.vent_state.all").getFormattedText().split("\\n")), mouseX, mouseY);
            } else {
                this.screen.renderTooltip(Arrays.asList(new TranslationTextComponent("tooltip.biggerreactors.buttons.turbine.vent_state.closed").getFormattedText().split("\\n")), mouseX, mouseY);
            }
        }
    }
    
    @Override
    public void doClick(int mouseX, int mouseY, int mouseButton) {
        if (!isHovering(mouseX, mouseY)) {
            return;
        }
        
        // Check for a click (and enable debounce).
        if (glfwGetMouseButton(Minecraft.getInstance().getMainWindow().getHandle(), GLFW_MOUSE_BUTTON_1) == GLFW_PRESS
                && !debounce) {
            
            // Do click logic.
            if (ventState == VentState.OVERFLOW) {
                ((TurbineContainer) this.screen.getContainer()).runRequest("setVentState", VentState.valueOf(VentState.ALL));
            } else if (ventState == VentState.ALL) {
                ((TurbineContainer) this.screen.getContainer()).runRequest("setVentState", VentState.valueOf(VentState.CLOSED));
            } else {
                ((TurbineContainer) this.screen.getContainer()).runRequest("setVentState", VentState.valueOf(VentState.OVERFLOW));
            }
            assert this.screen.getMinecraft().player != null;
            this.screen.getMinecraft().player.playSound(SoundEvents.UI_BUTTON_CLICK, this.screen.getMinecraft().gameSettings.getSoundLevel(SoundCategory.MASTER), 1.0F);
            debounce = true;
        }
        
        // Check for release (and disable debounce).
        if (glfwGetMouseButton(Minecraft.getInstance().getMainWindow().getHandle(), GLFW_MOUSE_BUTTON_1) == GLFW_RELEASE
                && debounce) {
            debounce = false;
        }
    }
}