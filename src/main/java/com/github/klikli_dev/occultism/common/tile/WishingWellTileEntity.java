/*
 * MIT License
 *
 * Copyright 2020 klikli-dev
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.github.klikli_dev.occultism.common.tile;

import com.github.klikli_dev.occultism.Occultism;
import com.github.klikli_dev.occultism.crafting.recipe.ItemStackFakeInventory;
import com.github.klikli_dev.occultism.crafting.recipe.WishingWellSacrificeRecipe;
import com.github.klikli_dev.occultism.registry.OccultismBlocks;
import com.github.klikli_dev.occultism.registry.OccultismRecipes;
import com.github.klikli_dev.occultism.registry.OccultismTiles;
import com.github.klikli_dev.occultism.util.Math3DUtil;
import net.minecraft.entity.item.ItemEntity;
import net.minecraft.fluid.Fluids;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.tileentity.ITickableTileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class WishingWellTileEntity extends NetworkedTileEntity implements ITickableTileEntity {
    //region Fields
    /**
     * The offset for the block to check for items to grow.
     */
    protected static final BlockPos GROWING_BLOCK_OFFSET = new BlockPos(0, 1, 0);

    /**
     * Can be outdated, is updated every 40-60 seconds.
     */
    public boolean hasValidMultiblock;
    /**
     * Init flag for water spawning
     */
    protected boolean hasEverSpawnedWater;
    /**
     * Init flag for multiblock check.
     */
    protected boolean hasEverValidatedMultiblock;
    /**
     * the ticks between multiblock check ticks.
     */
    protected int multiBlockTickTime;

    /**
     * The queue of item stacks to dissolve over time.
     */
    protected Queue<ItemStack> itemsToDissolve = new ArrayDeque<>();

    protected ItemStack currentItemToDissolve = ItemStack.EMPTY;
    protected WishingWellSacrificeRecipe currentSacrificeRecipe;
    protected int currentItemDissolveTicks;
    protected ItemStackFakeInventory sacrificeFakeInventory = new ItemStackFakeInventory(ItemStack.EMPTY);
    protected ItemStackFakeInventory growingFakeInventory = new ItemStackFakeInventory(ItemStack.EMPTY);

    protected float earthEssence;
    protected float airEssence;
    protected float fireEssence;
    protected float waterEssence;

    //endregion Fields

    //region Initialization
    public WishingWellTileEntity() {
        super(OccultismTiles.WISHING_WELL.get());
        //randomize the tick time between 40-60 seconds to avoid all TEs checking in sync.
        this.multiBlockTickTime = ThreadLocalRandom.current().nextInt(40, 60) * 20;
    }
    //endregion Initialization

    //region Overrides
    @Override
    public void tick() {
        if (!this.hasEverValidatedMultiblock) {
            this.hasEverValidatedMultiblock = true;
            this.hasValidMultiblock = this.hasValidMultiblock();
        }
        if (!this.hasEverSpawnedWater && this.hasValidMultiblock) {
            this.hasEverSpawnedWater = true;
            this.spawnFluidColumn();
        }

        //check multiblock and water every 40-60 seconds.
        if (this.world.getGameTime() % this.multiBlockTickTime == 0) {
            this.hasValidMultiblock = this.hasValidMultiblock();
            if (this.hasValidMultiblock) {
                this.spawnFluidColumn();
            }
            else {
                this.removeFluidColumn();
            }
        }

        if (this.hasValidMultiblock) {

            //Item dissolving
            //get an item to dissolve
            if (this.currentItemToDissolve.isEmpty() && !this.itemsToDissolve.isEmpty()) {
                this.currentItemToDissolve = this.itemsToDissolve.poll();
            }

            //if we have an item, continuie dissolving it
            if (!this.currentItemToDissolve.isEmpty()) {
                if (this.currentSacrificeRecipe == null) {
                    this.findRecipe(this.currentItemToDissolve)
                            .ifPresent(recipe -> this.currentSacrificeRecipe = recipe);
                }

                if (this.currentSacrificeRecipe != null) {

                    //Dissolve item until used up.
                    this.earthEssence += this.currentSacrificeRecipe.getEarthEssencePerTick();
                    this.airEssence += this.currentSacrificeRecipe.getAirEssencePerTick();
                    this.fireEssence += this.currentSacrificeRecipe.getFireEssencePerTick();
                    this.waterEssence += this.currentSacrificeRecipe.getWaterEssencePerTick();

                    this.currentItemDissolveTicks++;
                    if (this.currentItemDissolveTicks >= this.currentSacrificeRecipe.getDissolveTicks()) {
                        //Done with dissolving item, prepare for next
                        this.resetCurrentItemToDissolve();
                    }
                }
                else {
                    //If still null: should never happen
                    Occultism.LOGGER.warn(
                            "No recipe found during tick in wishing well for Item {}, should never happen.",
                            this.currentItemToDissolve.getItem().getRegistryName());
                }
            }

            //Grow items
            //TODO: every X ticks, query for current growables
            Vec3d growingBlockCenter = Math3DUtil.center(pos.add(GROWING_BLOCK_OFFSET));
            AxisAlignedBB growingBox = new AxisAlignedBB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5).offset(growingBlockCenter);
            List<ItemEntity> growingBoxEntities = world.getEntitiesWithinAABB(ItemEntity.class, growingBox);
            //TODO: query growing recipes for the growables, and cache recpies
            //TODO: apply growing recipes over time -> probalby need a struct for recipe + current time and resources
            //TODO: actually, definitely store in nbt
        }
    }

    @Override
    public void read(CompoundNBT compound) {
        super.read(compound);

        //read dissolve queue
        ListNBT nbtList = compound.getList("itemsToDissolve", Constants.NBT.TAG_COMPOUND);
        this.itemsToDissolve = new ArrayDeque<>(
                nbtList.stream().map(CompoundNBT.class::cast).map(ItemStack::read).collect(Collectors.toList()));

        //read current item to dissolve
        this.currentItemToDissolve = ItemStack.EMPTY;
        if (compound.contains("currentItemToDissolve")) {
            this.currentItemToDissolve = ItemStack.read(compound.getCompound("currentItemToDissolve"));
            this.currentItemDissolveTicks = compound.getInt("currentItemDissolveTicks");
        }
    }

    @Override
    public CompoundNBT write(CompoundNBT compound) {
        //Store the items to dissolve
        ListNBT nbtList = new ListNBT();
        this.itemsToDissolve.forEach(itemStack -> nbtList.add(itemStack.serializeNBT()));
        compound.put("itemsToDissolve", nbtList);

        //Store current item to dissolve
        if (!this.currentItemToDissolve.isEmpty()) {
            compound.put("currentItemToDissolve", this.currentItemToDissolve.serializeNBT());
            compound.putInt("currentItemDissolveTicks", this.currentItemDissolveTicks);
        }

        return super.write(compound);

    }

    @Override
    public void readNetwork(CompoundNBT compound) {
        super.readNetwork(compound);
        //read current essence level
        this.earthEssence = compound.getFloat("earthEssence");
        this.airEssence = compound.getFloat("airEssence");
        this.fireEssence = compound.getFloat("fireEssence");
        this.waterEssence = compound.getFloat("waterEssence");
    }

    @Override
    public CompoundNBT writeNetwork(CompoundNBT compound) {
        //Store current essence level
        compound.putFloat("earthEssence", this.earthEssence);
        compound.putFloat("airEssence", this.airEssence);
        compound.putFloat("fireEssence", this.fireEssence);
        compound.putFloat("waterEssence", this.waterEssence);
        return super.writeNetwork(compound);
    }

    @Override
    public void remove() {
        this.removeFluidColumn();
        super.remove();
    }
    //endregion Overrides

    //region Methods
    public void resetCurrentItemToDissolve() {
        this.currentItemDissolveTicks = 0;
        this.currentItemToDissolve = ItemStack.EMPTY;
        this.currentSacrificeRecipe = null;
    }

    public void sacrificeItem(ItemEntity item) {
        if (this.hasValidMultiblock && item.isAlive()) {
            this.findRecipe(item.getItem()).ifPresent(recipe -> {
                this.itemsToDissolve.add(item.getItem());
                item.remove(false);
            });
        }
    }

    public boolean hasValidMultiblock() {
        return OccultismBlocks.WISTHING_WELL.get().blockMatcher.validate(this.world, this.pos) != null;
    }

    protected Optional<WishingWellSacrificeRecipe> findRecipe(ItemStack itemStack) {
        this.sacrificeFakeInventory.setInput(itemStack);
        return this.world.getRecipeManager().getRecipe(OccultismRecipes.WISHING_WELL_SACRIFICE_TYPE.get(),
                this.sacrificeFakeInventory, this.world);
    }

    protected void spawnFluidColumn() {
        BlockPos fluidPos = this.pos.add(0, 4, 0);
        if (this.world.getBlockState(fluidPos).getFluidState().getFluid() != Fluids.WATER) {
            this.world.setBlockState(fluidPos, Fluids.WATER.getDefaultState().getBlockState(), 11);
        }

        BlockPos sacrificePos = this.pos.add(0, 5, 0);
        if (this.world.getBlockState(sacrificePos).getBlock() != OccultismBlocks.WISHING_WELL_SACRIFICE.get()) {
            this.world.setBlockState(sacrificePos, OccultismBlocks.WISHING_WELL_SACRIFICE.get().getDefaultState(), 2);
        }
    }

    protected void removeFluidColumn() {
        BlockPos fluidPos = this.pos.add(0, 4, 0);
        if (this.world.getBlockState(fluidPos).getFluidState().getFluid() == Fluids.WATER) {
            this.world.removeBlock(fluidPos, false);
        }

        BlockPos sacrificePos = this.pos.add(0, 5, 0);
        if (this.world.getBlockState(sacrificePos).getBlock() == OccultismBlocks.WISHING_WELL_SACRIFICE.get()) {
            this.world.removeBlock(sacrificePos, false);
        }
    }
    //endregion Methods
}
