package net.roguelogix.biggerreactors.classic.reactor.simulation;

import net.roguelogix.biggerreactors.Config;
import net.roguelogix.biggerreactors.classic.reactor.ReactorModeratorRegistry;
import org.joml.Vector2i;

import java.util.ArrayList;

public class ClassicReactorSimulation {
    
    private int x, y, z;
    
    float reactorVolume() {
        return x * y * z;
    }
    
    private ReactorModeratorRegistry.ModeratorProperties[][][] moderatorProperties;
    
    private class ControlRod {
        final int x;
        final int z;
        float insertion = 0;
        
        private ControlRod(int x, int z) {
            this.x = x;
            this.z = z;
        }
    }
    
    private final ArrayList<ControlRod> controlRods = new ArrayList<>();
    private ControlRod[][] controlRodsXZ;
    
    float fuelRodVolume() {
        return controlRods.size() * y;
    }
    
    public void resize(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
        moderatorProperties = new ReactorModeratorRegistry.ModeratorProperties[x][][];
        for (int i = 0; i < moderatorProperties.length; i++) {
            moderatorProperties[i] = new ReactorModeratorRegistry.ModeratorProperties[y][];
            for (int j = 0; j < moderatorProperties[i].length; j++) {
                moderatorProperties[i][j] = new ReactorModeratorRegistry.ModeratorProperties[z];
            }
        }
        controlRodsXZ = new ControlRod[x][];
        for (int i = 0; i < controlRodsXZ.length; i++) {
            controlRodsXZ[i] = new ControlRod[z];
        }
        controlRods.clear();
    }
    
    void setModeratorProperties(int x, int y, int z, ReactorModeratorRegistry.ModeratorProperties properties) {
        moderatorProperties[x][y][z] = properties;
    }
    
    int setControlRod(int x, int z) {
        controlRods.add(new ControlRod(x, z));
        return controlRods.size() - 1;
    }
    
    int controlRodCount() {
        return controlRods.size();
    }
    
    void setControlRodInsertion(int x, int z, float insertion) {
        controlRodsXZ[x][z].insertion = insertion;
    }
    
    public final FuelTank fuelTank = new FuelTank();
    
    private float fuelToReactorHeatTransferCoefficient = 0;
    private float reactorToCoolantSystemHeatTransferCoefficient = 0;
    private float reactorHeatLossCoefficient = 0;
    
    public void updateInternalValues() {
        fuelTank.setCapacity(Config.ReactorPerFuelRodCapacity * controlRods.size() * y);
        
        fuelToReactorHeatTransferCoefficient = 0;
        for (ControlRod controlRod : controlRods) {
            for (int i = 0; i < y; i++) {
                for (Vector2i direction : directions) {
                    fuelToReactorHeatTransferCoefficient += moderatorProperties[controlRod.x + direction.x][i][controlRod.z + direction.y].heatConductivity;
                }
            }
        }
        fuelToReactorHeatTransferCoefficient *= Config.ReactorFuelToCasingTransferCoefficientMultiplier;
        
        reactorToCoolantSystemHeatTransferCoefficient = 2 * (x * y + x * z + z * y) * Config.ReactorCasingToCoolantSystemCoefficientMultiplier;
        
        reactorHeatLossCoefficient = 2 * ((x + 2) * (y + 2) + (x + 2) * (z + 2) + (z + 2) * (y + 2)) * Config.ReactorHeatLossCoefficientMultiplier;
        
        
    }
    
    private boolean active = false;
    
    public void setActive(boolean active) {
        this.active = active;
    }
    
    private float fuelFertility = 0f;
    private float fuelHeat = 0f;
    private float reactorHeat = 0f;
    
    public float fuelConsumedLastTick = 0f;
    public float FEProducedLastTick = 0f;
    
    public void tick() {
        if (active) {
            radiate();
        }
        
        {
            // decay fertility, RadiationHelper.tick in old BR, this is copied, mostly
            float denominator = Config.ReactorFuelFertilityDecayDenominator;
            if (!active) {
                // Much slower decay when off
                denominator *= Config.ReactorFuelFertilityDecayDenominatorInactiveMultiplier;
            }
            
            // Fertility decay, at least 0.1 rad/t, otherwise halve it every 10 ticks
            fuelFertility = Math.max(0f, fuelFertility - Math.max(Config.ReactorFuelFertilityMinimumDecay, fuelFertility / denominator));
        }
        
        // Heat Transfer: Fuel Pool <> Reactor Environment
        float tempDiff = fuelHeat - reactorHeat;
        if (tempDiff > 0.01f) {
            float rfTransferred = tempDiff * fuelToReactorHeatTransferCoefficient;
            float fuelRf = getRFFromVolumeAndTemp(fuelRodVolume(), fuelHeat);
            
            fuelRf -= rfTransferred;
            fuelHeat = getTempFromVolumeAndRF(fuelRodVolume(), fuelRf);
            
            // Now see how much the reactor's temp has increased
            float reactorRf = getRFFromVolumeAndTemp(reactorVolume(), reactorHeat);
            reactorRf += rfTransferred;
            reactorHeat = getTempFromVolumeAndRF(reactorVolume(), reactorRf);
        }
        
        // If we have a temperature differential between environment and coolant system, move heat between them.
        tempDiff = reactorHeat - getCoolantTemperature();
        if (tempDiff > 0.01f) {
            float rfTransferred = tempDiff * reactorToCoolantSystemHeatTransferCoefficient;
            float reactorRf = getRFFromVolumeAndTemp(reactorVolume(), reactorHeat);
            
            if (isPassivelyCooled()) {
                rfTransferred *= Config.ReactorPassiveCoolingTransferEfficiency;
                FEProducedLastTick = rfTransferred * Config.ReactorPowerOutputMultiplier;
            } else {
//                rfTransferred -= coolantContainer.onAbsorbHeat(rfTransferred);
//                energyGeneratedLastTick = coolantContainer.getFluidVaporizedLastTick(); // Piggyback so we don't have useless stuff in the update packet
            }
            
            reactorRf -= rfTransferred;
            reactorHeat = getTempFromVolumeAndRF(reactorVolume(), reactorRf);
        }
        
        // Do passive heat loss - this is always versus external environment
        tempDiff = reactorHeat - Config.ReactorAmbientTemperature;
        if (tempDiff > 0.000001f) {
            float rfLost = Math.max(1f, tempDiff * reactorHeatLossCoefficient); // Lose at least 1RF/t
            float reactorNewRf = Math.max(0f, getRFFromVolumeAndTemp(reactorVolume(), reactorHeat) - rfLost);
            reactorHeat = getTempFromVolumeAndRF(reactorVolume(), reactorNewRf);
        }
        
        // Prevent cryogenics
        if (reactorHeat < 0f) {
            reactorHeat = 0;
        }
        if (fuelHeat < 0f) {
            fuelHeat = 0;
        }
    }
    
    private boolean isPassivelyCooled() {
        return true;
    }
    
    private float getCoolantTemperature() {
        // TODO: 6/26/20 active reactor
        return Config.ReactorAmbientTemperature;
    }
    
    private int rodToIrradiate = 0;
    private int yLevelToIrradiate = 0;
    
    private final Vector2i[] directions = new Vector2i[]{
            new Vector2i(1, 0),
            new Vector2i(-1, 0),
            new Vector2i(0, 1),
            new Vector2i(0, -1),
    };
    
    private void radiate() {
        // ok, so, this is doing basically the same thing as what old BR did, marked with where the functionality is in old BR
        
        // MultiblockReactor.updateServer
        // this is a different method, but im just picking a fuel rod to radiate from
        if (rodToIrradiate == controlRods.size()) {
            rodToIrradiate = 0;
            yLevelToIrradiate++;
        }
        
        if (yLevelToIrradiate == y) {
            yLevelToIrradiate = 0;
        }
        
        
        // RadiationHelper.radiate
        // mostly copied
        
        // No fuel? No radiation!
        if (fuelTank.getFuelAmount() <= 0) {
            return;
        }
        
        // Determine radiation amount & intensity, heat amount, determine fuel usage
        
        // Base value for radiation production penalties. 0-1, caps at about 3000C;
        double radiationPenaltyBase = Math.exp(-15 * Math.exp(-0.0025 * fuelHeat));
        
        // Raw amount - what's actually in the tanks
        // Effective amount - how
        long baseFuelAmount = fuelTank.getFuelAmount() + (fuelTank.getWasteAmount() / 100);
        
        // Intensity = how strong the radiation is, hardness = how energetic the radiation is (penetration)
        float rawRadIntensity = (float) baseFuelAmount * Config.ReactorFissionEventsPerFuelUnit;
        
        // Scale up the "effective" intensity of radiation, to provide an incentive for bigger reactors in general.
        float scaledRadIntensity = (float) Math.pow((rawRadIntensity), Config.ReactorFuelReactivity);
        
        // Scale up a second time based on scaled amount in each fuel rod. Provides an incentive for making reactors that aren't just pancakes.
        scaledRadIntensity = (float) Math.pow((scaledRadIntensity / controlRods.size()), Config.ReactorFuelReactivity) * controlRods.size();
        
        // Apply control rod moderation of radiation to the quantity of produced radiation. 100% insertion = 100% reduction.
        float controlRodModifier = (100 - controlRods.get(rodToIrradiate).insertion) / 100f;
        scaledRadIntensity = scaledRadIntensity * controlRodModifier;
        rawRadIntensity = rawRadIntensity * controlRodModifier;
        
        // Now nerf actual radiation production based on heat.
        float effectiveRadIntensity = scaledRadIntensity * (1f + (float) (-0.95f * Math.exp(-10f * Math.exp(-0.0012f * fuelHeat))));
        
        // Radiation hardness starts at 20% and asymptotically approaches 100% as heat rises.
        // This will make radiation harder and harder to capture.
        float radHardness = 0.2f + (float) (0.8 * radiationPenaltyBase);
        
        // Calculate based on propagation-to-self
        float rawFuelUsage = (Config.ReactorFuelPerRadiationUnit * rawRadIntensity / (fuelFertility <= 1 ? 1f : (float) Math.log10(fuelFertility) + 1f)) * Config.ReactorFuelUsageMultiplier; // Not a typo. Fuel usage is thus penalized at high heats.
        float fuelRfChange = Config.ReactorFEPerRadiationUnit * effectiveRadIntensity;
        float environmentRfChange = 0f;
        
        effectiveRadIntensity *= 0.25f; // We're going to do this four times, no need to repeat
        
        float fuelAbsorbedRadiation = 0f;
        
        Vector2i position = new Vector2i();
        
        for (Vector2i direction : directions) {
            position.set(controlRods.get(rodToIrradiate).x, controlRods.get(rodToIrradiate).z);
            
            float hardness = radHardness;
            float intensity = effectiveRadIntensity;
            
            for (int i = 0; i < Config.ReactorIrradiationDistance && intensity > 0.0001f; i++) {
                position.add(direction);
                // out of bounds
                if (position.x < 0 || position.x > x || position.y < 0 || position.y > z) {
                    break;
                }
                
                ReactorModeratorRegistry.ModeratorProperties properties = moderatorProperties[position.x][yLevelToIrradiate][position.y];
                if (properties != null) {
                    // this is the simple part, which is why its first
                    
                    float radiationAbsorbed = intensity * properties.absorption * (1f - hardness);
                    intensity = Math.max(0f, intensity - radiationAbsorbed);
                    hardness /= properties.moderation;
                    environmentRfChange += properties.heatEfficiency * radiationAbsorbed * Config.ReactorFEPerRadiationUnit;
                    
                } else {
                    // oh, oh ok, its a fuel rod
                    
                    // Scale control rod insertion 0..1
                    float controlRodInsertion = Math.min(1f, Math.max(0f, controlRodsXZ[position.x][position.y].insertion / 100f));
                    
                    // Fuel absorptiveness is determined by control rod + a heat modifier.
                    // Starts at 1 and decays towards 0.05, reaching 0.6 at 1000 and just under 0.2 at 2000. Inflection point at about 500-600.
                    // Harder radiation makes absorption more difficult.
                    float baseAbsorption = (float) (1.0 - (0.95 * Math.exp(-10 * Math.exp(-0.0022 * fuelHeat)))) * (1f - (hardness / Config.ReactorFuelHardnessDivisor));
                    
                    // Some fuels are better at absorbing radiation than others
                    float scaledAbsorption = Math.min(1f, baseAbsorption * Config.ReactorFuelAbsorptionCoefficient);
                    
                    // Control rods increase total neutron absorption, but decrease the total neutrons which fertilize the fuel
                    // Absorb up to 50% better with control rods inserted.
                    float controlRodBonus = (1f - scaledAbsorption) * controlRodInsertion * 0.5f;
                    float controlRodPenalty = scaledAbsorption * controlRodInsertion * 0.5f;
                    
                    float radiationAbsorbed = (scaledAbsorption + controlRodBonus) * intensity;
                    float fertilityAbsorbed = (scaledAbsorption - controlRodPenalty) * intensity;
                    
                    float fuelModerationFactor = Config.ReactorFuelModerationFactor;
                    fuelModerationFactor += fuelModerationFactor * controlRodInsertion + controlRodInsertion; // Full insertion doubles the moderation factor of the fuel as well as adding its own level
                    
                    intensity = Math.max(0f, intensity - radiationAbsorbed);
                    hardness /= fuelModerationFactor;
                    
                    // Being irradiated both heats up the fuel and also enhances its fertility
                    fuelRfChange += radiationAbsorbed * Config.ReactorFuelPerRadiationUnit;
                    fuelAbsorbedRadiation += fertilityAbsorbed;
                }
            }
            
            fuelFertility += fuelAbsorbedRadiation;
            fuelTank.burn(rawFuelUsage);
        }
        
        // back to MultiblockReactor.updateServer, after it calls RadiationHelper.radiate
        addFuelHeat(getTempFromVolumeAndRF(fuelRodVolume(), fuelRfChange));
        addReactorHeat(getTempFromVolumeAndRF(reactorVolume(), environmentRfChange));
        fuelConsumedLastTick = rawFuelUsage;
    }
    
    // these two are copied from MultiblockReactor
    protected void addReactorHeat(float newCasingHeat) {
        if (Float.isNaN(newCasingHeat)) {
            return;
        }
        
        reactorHeat += newCasingHeat;
        // Clamp to zero to prevent floating point issues
        if (-0.00001f < reactorHeat && reactorHeat < 0.00001f) {
            reactorHeat = 0.0f;
        }
    }
    
    protected void addFuelHeat(float additionalHeat) {
        if (Float.isNaN(additionalHeat)) {
            return;
        }
        
        fuelHeat += additionalHeat;
        // Clamp to zero to prevent floating point issues
        if (-0.00001f < fuelHeat & fuelHeat < 0.00001f) {
            fuelHeat = 0f;
        }
    }
    
    public static float getRFFromVolumeAndTemp(float volume, float temperature) {
        return temperature * volume * Config.ReactorFEPerCentigradePerUnitVolume;
    }
    
    public static float getTempFromVolumeAndRF(float volume, float rf) {
        return rf / (volume * Config.ReactorFEPerCentigradePerUnitVolume);
    }
}