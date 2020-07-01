package net.roguelogix.phosphophyllite.registry;

import com.google.common.collect.ImmutableSet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Supplier;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.FlowingFluidBlock;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.model.ModelResourceLocation;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.fluid.FlowingFluid;
import net.minecraft.fluid.Fluid;
import net.minecraft.inventory.container.Container;
import net.minecraft.inventory.container.ContainerType;
import net.minecraft.item.*;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.gen.GenerationStage.Decoration;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.OreFeatureConfig;
import net.minecraft.world.gen.feature.OreFeatureConfig.FillerBlockType;
import net.minecraft.world.gen.placement.CountRangeConfig;
import net.minecraft.world.gen.placement.Placement;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.extensions.IForgeContainerType;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import net.roguelogix.phosphophyllite.config.PhosphophylliteConfig;
import net.roguelogix.phosphophyllite.multiblock.generic.MultiblockBakedModel;
import org.reflections.Reflections;

import javax.annotation.Nonnull;

public class Registry {
    
    private static final HashMap<String, HashSet<Block>> blocksRegistered = new HashMap<>();
    private static final HashMap<Class<?>, HashSet<Block>> tileEntityBlocksToRegister = new HashMap<>();
    
    @SuppressWarnings("unchecked")
    public static synchronized void onModLoad() {
        String callerClass = new Exception().getStackTrace()[1].getClassName();
        String callerPackage = callerClass.substring(0, callerClass.lastIndexOf("."));
        String modNamespace = callerPackage.substring(callerPackage.lastIndexOf(".") + 1);
        Reflections ref = new Reflections(callerPackage);
        FMLJavaModLoadingContext.get().getModEventBus().addListener((RegistryEvent.Register<?> e) -> {
            switch (e.getName().getPath()) {
                case "block": {
                    registerBlocks((RegistryEvent.Register<Block>) e, modNamespace, ref);
                    break;
                }
                case "item": {
                    registerItems((RegistryEvent.Register<Item>) e, modNamespace, ref);
                    break;
                }
                case "menu": {
                    registerContainers((RegistryEvent.Register<ContainerType<?>>) e, modNamespace, ref);
                    break;
                }
                case "block_entity_type": {
                    registerTileEntities((RegistryEvent.Register<TileEntityType<?>>) e, modNamespace, ref);
                    break;
                }
                case "fluid": {
                    registerFluids((RegistryEvent.Register<Fluid>) e, modNamespace, ref);
                    break;
                }
            }
        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLClientSetupEvent e) -> onClientSetup(e, modNamespace, ref));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((ModelBakeEvent e) -> onModelBake(e, modNamespace, ref));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((TextureStitchEvent.Pre e) -> onTextureStitch(e, modNamespace, ref));
        FMLJavaModLoadingContext.get().getModEventBus().addListener((FMLLoadCompleteEvent e) -> onLoadComplete(e, modNamespace, ref));
    }
    
    private static synchronized void registerBlocks(final RegistryEvent.Register<Block> blockRegistryEvent, String modNamespace, Reflections ref) {
        Set<Class<?>> blocks = ref.getTypesAnnotatedWith(RegisterBlock.class);
        HashSet<Block> blocksRegistered = Registry.blocksRegistered
                .computeIfAbsent(modNamespace, k -> new HashSet<>());
        for (Class<?> block : blocks) {
            try {
                Constructor<?> constructor = block.getConstructor();
                constructor.setAccessible(true);
                Object newObject = constructor.newInstance();
                if (!(newObject instanceof Block)) {
                    // the fuck you doing
                    //todo print error
                    continue;
                }
                
                Block newBlock = (Block) newObject;
                RegisterBlock blockAnnotation = block.getAnnotation(RegisterBlock.class);
                newBlock.setRegistryName(modNamespace + ":" + blockAnnotation.name());
                blockRegistryEvent.getRegistry().register(newBlock);
                
                for (Field declaredField : block.getDeclaredFields()) {
                    if (declaredField.isAnnotationPresent(RegisterBlock.Instance.class)) {
                        declaredField.set(null, newBlock);
                    }
                }
                
                if (blockAnnotation.registerItem()) {
                    blocksRegistered.add(newBlock);
                }
                
                if (blockAnnotation.tileEntityClass() != RegisterBlock.class) {
                    tileEntityBlocksToRegister
                            .computeIfAbsent(blockAnnotation.tileEntityClass(), k -> new HashSet<>())
                            .add(newBlock);
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
        
        Set<Class<?>> fluids = ref.getTypesAnnotatedWith(RegisterFluid.class);
        for (Class<?> fluid : fluids) {
            RegisterFluid annotation = fluid.getAnnotation(RegisterFluid.class);
            for (Field declaredField : fluid.getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(RegisterFluid.Instance.class)) {
                    FlowingFluidBlock block = new FlowingFluidBlock(() -> {
                        try {
                            return (FlowingFluid) declaredField.get(null);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }, Block.Properties.create(net.minecraft.block.material.Material.WATER).
                            doesNotBlockMovement().hardnessAndResistance(100.0F).noDrops()
                    );
                    block.setRegistryName(modNamespace + ":" + annotation.name() + "_block");
                    blockRegistryEvent.getRegistry().register(block);
                    break;
                }
            }
        }
    }
    
    
    private static synchronized void registerItems(final RegistryEvent.Register<Item> itemRegistryEvent, String modNamespace, Reflections ref) {
        Set<Class<?>> items = ref.getTypesAnnotatedWith(RegisterItem.class);
        HashSet<Block> blocksRegistered = Registry.blocksRegistered.get(modNamespace);
        
        Set<Class<?>> modClass = ref.getTypesAnnotatedWith(Mod.class);
        
        String groupName = modNamespace;
        if (modClass.size() == 1) {
            groupName = modClass.iterator().next().getName();
        }
        
        // TODO: 6/28/20 check annotations for creative tab registration
        ItemGroup group;
        {
            Block block;
            Set<Class<?>> createiveTabBlock = ref.getTypesAnnotatedWith(CreativeTabBlock.class);
            Iterator<Class<?>> iterator = createiveTabBlock.iterator();
            Block b1 = Blocks.STONE;
            if (iterator.hasNext()) {
                Class<?> blockClass = iterator.next();
                for (Field declaredField : blockClass.getDeclaredFields()) {
                    if (declaredField.isAnnotationPresent(RegisterBlock.Instance.class)) {
                        try {
                            b1 = (Block) declaredField.get(null);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        break;
                    }
                }
            }
            block = b1;
            group = new ItemGroup(groupName) {
                @Nonnull
                @Override
                public ItemStack createIcon() {
                    return new ItemStack(ForgeRegistries.ITEMS.getValue(block.getRegistryName()));
                }
            };
        }
        
        Set<Class<?>> fluids = ref.getTypesAnnotatedWith(RegisterFluid.class);
        for (Class<?> fluid : fluids) {
            RegisterFluid annotation = fluid.getAnnotation(RegisterFluid.class);
            if (!annotation.registerBucket()) {
                continue;
            }
            for (Field declaredField : fluid.getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(RegisterFluid.Instance.class)) {
                    BucketItem item = new BucketItem(() -> {
                        try {
                            return (FlowingFluid) declaredField.get(null);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return null;
                    }, new Item.Properties().containerItem(Items.BUCKET).maxStackSize(1).group(group));
                    item.setRegistryName(modNamespace + ":" + annotation.name() + "_bucket");
                    itemRegistryEvent.getRegistry().register(item);
                    break;
                }
            }
        }
        
        for (Block block : blocksRegistered) {
            assert block.getClass().isAnnotationPresent(RegisterBlock.class);
//            RegisterBlock blockAnnotation = block.getClass().getAnnotation(RegisterBlock.class);
            //noinspection ConstantConditions
            itemRegistryEvent.getRegistry().register(
                    new BlockItem(block, new Item.Properties().group(group)).setRegistryName(block.getRegistryName()));
        }
        
        for (Class<?> item : items) {
            try {
                Constructor<?> constructor = item.getConstructor(Item.Properties.class);
                constructor.setAccessible(true);
                Object newObject = constructor.newInstance(new Item.Properties().group(group));
                if (!(newObject instanceof Item)) {
                    // the fuck you doing
                    //todo print error
                    continue;
                }
                
                Item newItem = (Item) newObject;
                RegisterItem itemAnnotation = item.getAnnotation(RegisterItem.class);
                newItem.setRegistryName(modNamespace + ":" + itemAnnotation.name());
                itemRegistryEvent.getRegistry().register(newItem);
                
                for (Field declaredField : item.getDeclaredFields()) {
                    if (declaredField.isAnnotationPresent(RegisterItem.Instance.class)) {
                        declaredField.set(null, newItem);
                    }
                }
            } catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static synchronized void registerFluids(final RegistryEvent.Register<Fluid> fluidRegistryEvent, String modNamespace, Reflections ref) {
        Set<Class<?>> fluids = ref.getTypesAnnotatedWith(RegisterFluid.class);
        for (Class<?> fluid : fluids) {
            RegisterFluid annotation = fluid.getAnnotation(RegisterFluid.class);
            for (Field declaredField : fluid.getDeclaredFields()) {
                if (declaredField.isAnnotationPresent(RegisterFluid.Instance.class)) {
                    Supplier<? extends Fluid> stillSupplier = () -> {
                        try {
                            return (Fluid) declaredField.get(null);
                        } catch (IllegalAccessException e) {
                            e.printStackTrace();
                        }
                        return null;
                    };
                    Supplier<? extends Fluid> flowingSupplier = () -> {
                        Fluid stillFluid = stillSupplier.get();
                        assert stillFluid instanceof PhosphophylliteFluid;
                        return ((PhosphophylliteFluid) stillFluid).flowingVariant;
                    };
                    FluidAttributes.Builder attributes =
                            FluidAttributes.builder(new ResourceLocation(modNamespace, "fluid/" + annotation.name() + "_flowing"), new ResourceLocation(modNamespace, "fluid/" + annotation.name() + "_still"))
                                    .overlay(new ResourceLocation(modNamespace, "fluid/" + annotation.name() + "_overlay")).color(annotation.color());
                    ForgeFlowingFluid.Properties properties = new ForgeFlowingFluid.Properties(stillSupplier, flowingSupplier, attributes);
                    properties.bucket(() -> ForgeRegistries.ITEMS.getValue(new ResourceLocation(modNamespace, annotation.name() + "_bucket")));
                    properties.block(() -> (FlowingFluidBlock) ForgeRegistries.BLOCKS.getValue(new ResourceLocation(modNamespace, annotation.name() + "_block")));
                    try {
                        Constructor<?> constructor = fluid.getDeclaredConstructor(ForgeFlowingFluid.Properties.class);
                        
                        Fluid stillInstance = (Fluid) constructor.newInstance(properties);
                        Fluid flowingInstance = (Fluid) constructor.newInstance(properties);
                        
                        assert stillInstance instanceof PhosphophylliteFluid;
                        
                        ((PhosphophylliteFluid) stillInstance).isSource = true;
                        ((PhosphophylliteFluid) stillInstance).flowingVariant = (PhosphophylliteFluid) flowingInstance;
                        
                        stillInstance.setRegistryName(new ResourceLocation(modNamespace, annotation.name()));
                        flowingInstance.setRegistryName(new ResourceLocation(modNamespace, annotation.name() + "_flowing"));
                        
                        declaredField.set(null, stillInstance);
                        
                        fluidRegistryEvent.getRegistry().register(stillInstance);
                        fluidRegistryEvent.getRegistry().register(flowingInstance);
                    } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                    break;
                }
            }
        }
    }
    
    private static synchronized void registerContainers(RegistryEvent.Register<ContainerType<?>> containerTypeRegistryEvent, String modNamespace, Reflections ref) {
        Set<Class<?>> containers = ref.getTypesAnnotatedWith(RegisterContainer.class);
        
        for (Class<?> container : containers) {
            try {
                Constructor<?> constructor = container.getConstructor(int.class, BlockPos.class, PlayerEntity.class);
                constructor.setAccessible(true);
                
                RegisterContainer containerAnnotation = container.getAnnotation(RegisterContainer.class);
                ContainerType<?> containerType = IForgeContainerType.create(((windowId, playerInventory, data) -> {
                    try {
                        return (Container) constructor.newInstance(windowId, data.readBlockPos(), playerInventory.player);
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        throw new NullPointerException();
                    }
                }));
                
                containerType.setRegistryName(modNamespace + ":" + containerAnnotation.name());
                containerTypeRegistryEvent.getRegistry().register(containerType);
                
                for (Field declaredField : container.getDeclaredFields()) {
                    if (declaredField.isAnnotationPresent(RegisterContainer.Instance.class)) {
                        declaredField.set(null, containerType);
                    }
                }
            } catch (NullPointerException | NoSuchMethodException | IllegalAccessException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static synchronized void registerTileEntities(RegistryEvent.Register<TileEntityType<?>> tileEntityTypeRegistryEvent, String modNamespace, Reflections ref) {
        Set<Class<?>> tileEntities = ref.getTypesAnnotatedWith(RegisterTileEntity.class);
        
        for (Class<?> tileEntity : tileEntities) {
            
            RegisterTileEntity tileEntityAnnotation = tileEntity.getAnnotation(RegisterTileEntity.class);
            
            HashSet<Block> blocks = tileEntityBlocksToRegister
                    .computeIfAbsent(tileEntity, k -> new HashSet<>());
            if (blocks.isEmpty()) {
                continue;
            }
            try {
                Constructor<?> tileConstructor = tileEntity.getConstructor();
                tileConstructor.setAccessible(true);
                Supplier<? extends TileEntity> supplier = () -> {
                    try {
                        return (TileEntity) tileConstructor.newInstance();
                    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                    return null;
                };
                @SuppressWarnings("rawtypes") Constructor<TileEntityType.Builder> constructor = TileEntityType.Builder.class
                        .getDeclaredConstructor(Supplier.class, Set.class);
                constructor.setAccessible(true);
                @SuppressWarnings({"unchecked",
                        "ConstantConditions"}) TileEntityType<?> tileEntityType = constructor
                        .newInstance(supplier, ImmutableSet.copyOf(blocks)).build(null);
                tileEntityType.setRegistryName(modNamespace + ":" + tileEntityAnnotation.name());
                tileEntityTypeRegistryEvent.getRegistry().register(tileEntityType);
                for (Field field : tileEntity.getFields()) {
                    if (field.isAnnotationPresent(RegisterTileEntity.Type.class)) {
                        field.set(null, tileEntityType);
                    }
                }
            } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
                e.printStackTrace();
            }
            
        }
    }
    
    private static synchronized void onClientSetup(FMLClientSetupEvent clientSetupEvent, String modNamespace, Reflections ref) {
        HashSet<Block> blocksRegistered = Registry.blocksRegistered.get(modNamespace);
        for (Block block : blocksRegistered) {
            if (!block.getDefaultState().isSolid()) {
                for (Method declaredMethod : block.getClass().getDeclaredMethods()) {
                    if (declaredMethod.isAnnotationPresent(RegisterBlock.RenderLayer.class)) {
                        try {
                            declaredMethod.setAccessible(true);
                            Object returned = declaredMethod.invoke(block);
                            if (returned instanceof RenderType) {
                                RenderTypeLookup.setRenderLayer(block, (RenderType) returned);
                            }
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }
    }
    
    private static final HashMap<Block, IBakedModel> bakedModelsToRegister = new HashMap<>();
    
    public static synchronized void registerBakedModel(Block block, IBakedModel model) {
        bakedModelsToRegister.put(block, model);
    }
    
    private static synchronized void onModelBake(ModelBakeEvent event, String modNamespace, Reflections ref) {
        HashSet<Block> blocksRegistered = Registry.blocksRegistered.get(modNamespace);
        if (blocksRegistered == null) {
            return;
        }
        for (Block block : blocksRegistered) {
            IBakedModel model = bakedModelsToRegister.get(block);
            if (model != null) {
                event.getModelRegistry().put(new ModelResourceLocation(Objects.requireNonNull(block.getRegistryName()), ""), model);
            }
        }
    }
    
    private static synchronized void onTextureStitch(TextureStitchEvent.Pre event, String modNamespace, Reflections ref) {
        if (!event.getMap().getTextureLocation().equals(AtlasTexture.LOCATION_BLOCKS_TEXTURE)) {
            return;
        }
        HashSet<Block> blocksRegistered = Registry.blocksRegistered.get(modNamespace);
        if (blocksRegistered == null) {
            return;
        }
        HashSet<ResourceLocation> sprites = new HashSet<>();
        for (Block block : blocksRegistered) {
            IBakedModel model = bakedModelsToRegister.get(block);
            if (model instanceof MultiblockBakedModel) {
                Stack<MultiblockBakedModel.TextureMap> mapStack = new Stack<>();
                mapStack.push(((MultiblockBakedModel) model).map);
                while (!mapStack.empty()) {
                    MultiblockBakedModel.TextureMap map = mapStack.pop();
                    sprites.add(map.spriteLocation);
                    for (MultiblockBakedModel.TextureMap value : map.map.values()) {
                        mapStack.push(value);
                    }
                }
            }
        }
        for (ResourceLocation sprite : sprites) {
            event.addSprite(sprite);
        }
    }
    
    private static void registerConfig() {
        String callerClass = new Exception().getStackTrace()[1].getClassName();
        String callerPackage = callerClass.substring(0, callerClass.lastIndexOf("."));
        Reflections ref = new Reflections(callerPackage);
        Set<Class<?>> configs = ref.getTypesAnnotatedWith(RegisterConfig.class);
        for (Class<?> config : configs) {
//            ConfigLoader.registerConfig(config);
        }
    }
    
    private static void onLoadComplete(final FMLLoadCompleteEvent e, String modNamespace, Reflections ref) {
        Registry.registerWorldGen(modNamespace, ref);
    }
    
    private static synchronized void registerWorldGen(String modNamespace, Reflections ref) {
        Set<Class<?>> ores = ref.getTypesAnnotatedWith(RegisterOre.class);
        HashSet<Block> blocksRegistered = Registry.blocksRegistered
                .computeIfAbsent(modNamespace, k -> new HashSet<>());
        
        for (Class<?> ore : ores) {
            try {
                RegisterBlock blockAnnotation = ore.getAnnotation(RegisterBlock.class);
                
                Block oreInstance = null;
                for (Block block : blocksRegistered) {
                    if (Objects.requireNonNull(block.getRegistryName())
                            .toString().equals(modNamespace + ":" + blockAnnotation.name())) {
                        oreInstance = block;
                    }
                }
                
                assert oreInstance instanceof IPhosphophylliteOre;
                IPhosphophylliteOre oreInfo = (IPhosphophylliteOre) oreInstance;
                
                for (Biome biome : ForgeRegistries.BIOMES) {
                    if (oreInfo.spawnBiomes().length > 0) {
                        if (!Arrays.asList(oreInfo.spawnBiomes()).contains(
                                Objects.requireNonNull(biome.getRegistryName()).toString())) {
                            continue;
                        }
                    }
                    
                    FillerBlockType fillerBlock = oreInfo.isNetherOre() ? FillerBlockType.NETHERRACK : FillerBlockType.NATURAL_STONE;
                    biome.addFeature(Decoration.UNDERGROUND_ORES, Feature.ORE.withConfiguration(
                            new OreFeatureConfig(fillerBlock, oreInstance.getDefaultState(), oreInfo.size()))
                            .withPlacement(Placement.COUNT_RANGE.configure(new CountRangeConfig(oreInfo.count(), oreInfo.minLevel(), oreInfo.offset(), oreInfo.maxLevel()))));
                }
            } catch (NullPointerException e) {
                e.printStackTrace();
            }
        }
    }
    
    private static void loadConfig() {
        String callerClass = new Exception().getStackTrace()[1].getClassName();
        String callerPackage = callerClass.substring(0, callerClass.lastIndexOf("."));
        String modNamespace = callerPackage.substring(callerPackage.lastIndexOf(".") + 1);
        Reflections ref = new Reflections(callerPackage);
        Set<Class<?>> configs = ref.getTypesAnnotatedWith(RegisterConfig.class);
        for (Class<?> config : configs) {
            for (Method declaredMethod : config.getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(PhosphophylliteConfig.OnLoad.class)) {
                    try {
                        declaredMethod.invoke(null);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
