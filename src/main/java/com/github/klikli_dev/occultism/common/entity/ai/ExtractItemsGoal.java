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

package com.github.klikli_dev.occultism.common.entity.ai;

import com.github.klikli_dev.occultism.common.entity.spirit.SpiritEntity;
import com.github.klikli_dev.occultism.exceptions.ItemHandlerMissingException;
import com.github.klikli_dev.occultism.util.Math3DUtil;
import com.github.klikli_dev.occultism.util.StorageUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.BlockEntity.ChestBlockEntity;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.util.math.RayTraceContext;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.EnumSet;
import java.util.Optional;

public class ExtractItemsGoal extends PausableGoal {
    //region Fields
    protected final SpiritEntity entity;
    protected final BlockSorter targetSorter;
    protected BlockPos targetBlock = null;
    //endregion Fields

    //region Initialization
    public ExtractItemsGoal(SpiritEntity entity) {
        this.entity = entity;
        this.targetSorter = new BlockSorter(entity);
        this.setFlags(EnumSet.of(Flag.TARGET));
    }
    //endregion Initialization

    //region Getter / Setter

    /**
     * @return the position to move to to take from the target block.
     */
    private BlockPos getMoveTarget() {
        double angle = Math3DUtil.yaw(this.entity.getPositionVec(), Math3DUtil.center(this.targetBlock));
        return this.targetBlock.offset(Direction.fromAngle(angle).getOpposite());
    }
    //endregion Getter / Setter

    //region Overrides
    @Override
    public boolean canUse() {
        //do not use if there is a target to attack
        if (this.entity.getAttackTarget() != null) {
            return false;
        }

        //hand already full, cannot pick up anythings
        if (!this.entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty()) {
            return false;
        }
        this.resetTarget();
        return !this.isPaused() && this.targetBlock != null;
    }

    @Override
    public boolean canContinueToUse() {
        return !this.isPaused() && this.targetBlock != null && !this.entity.getItemInHand(InteractionHand.MAIN_HAND).isEmpty();
    }

    public void stop() {
        this.entity.getNavigator().stop();
        this.resetTarget();
    }

    @Override
    public void tick() {
        if (this.targetBlock != null) {
            if (this.entity.level.getBlockEntity(this.targetBlock) != null) {
                BlockEntity blockEntity = this.entity.level.getBlockEntity(this.targetBlock);

                float accessDistance = 1.86f;

                //when approaching a chest, open it visually
                double distance = this.entity.getPositionVec().distanceTo(Math3DUtil.center(this.targetBlock));

                //briefly before reaching the target, open chest, if it is one.
                if (distance < 2.5 && distance >= accessDistance && this.canSeeTarget() &&
                    BlockEntity instanceof Container) {
                    this.toggleChest((Container) BlockEntity, true);
                }

                if (distance < accessDistance) {
                    //stop moving while taking out
                    this.entity.getNavigator().stop();
                }
                else {
                    //continue moving
                    BlockPos moveTarget = this.getMoveTarget();
                    this.entity.getNavigator().setPath(this.entity.getNavigator().getPathToPos(moveTarget, 0), 1.0f);
                }

                //when close enough extract item
                if (distance < 1.86 && this.canSeeTarget()) {

                    LazyOptional<IItemHandler> handlerCapability = blockEntity.getCapability(
                            CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.entity.getExtractFacing());
                    if (!handlerCapability
                                 .isPresent()) { //worst case scenario if tile entity changes since last target reset.
                        this.resetTarget();
                        return;
                    }
                    IItemHandler tileHandler = handlerCapability.orElseThrow(ItemHandlerMissingException::new);
                    IItemHandler entityHandler =
                            this.entity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, Direction.DOWN)
                                    .orElseThrow(ItemHandlerMissingException::new);

                    int slot = StorageUtil.getFirstMatchingSlot(tileHandler,
                            this.entity.getFilterItems().orElseThrow(ItemHandlerMissingException::new), this.entity.getTagFilter(), this.entity.isFilterBlacklist());
                    if (slot >= 0) {
                        //simulate extraction
                        ItemStack toExtract = tileHandler.extractItem(slot, Integer.MAX_VALUE, true).copy();
                        if (!toExtract.isEmpty()) {
                            ItemStack remaining = ItemHandlerHelper.insertItem(entityHandler, toExtract, true);
                            if (remaining.getCount() < toExtract.getCount()) {
                                //if simulation went well, do for real
                                ItemStack extracted = tileHandler.extractItem(slot, toExtract.getCount() - remaining.getCount(), false);
                                ItemHandlerHelper.insertItem(entityHandler, extracted, false);
                            }
                        }
                    }

                    //after extracting, close chest
                    if (BlockEntity instanceof Container) {
                        this.toggleChest((Container) BlockEntity, false);
                    }
                }
            }
            else {
                this.resetTarget(); //if there is no tile entity, recheck
            }
        }
    }
    //endregion Overrides

    //region Methods
    public boolean canSeeTarget() {

        RayTraceContext context = new RayTraceContext(this.entity.getPositionVec(),
                Math3DUtil.center(this.targetBlock), RayTraceContext.BlockMode.COLLIDER, RayTraceContext.FluidMode.NONE,
                this.entity);
        BlockHitResult result = this.entity.level.rayTraceBlocks(context);

        if (result.getType() != BlockHitResult.Type.MISS) {
            BlockPos sidePos = result.getPos();
            BlockPos pos = new BlockPos(result.getHitVec());
            return this.entity.level.isAirBlock(sidePos) || this.entity.level.isAirBlock(pos) ||
                   this.entity.level.getBlockEntity(pos) == this.entity.level.getBlockEntity(this.targetBlock);
        }

        return true;
    }

    /**
     * Opens or closes a chest
     *
     * @param BlockEntity the chest tile entity
     * @param open       true to open the chest, false to close it.
     */
    public void toggleChest(Container BlockEntity, boolean open) {
        if (BlockEntity instanceof ChestBlockEntity) {
            ChestBlockEntity chest = (ChestBlockEntity) BlockEntity;
            if (open) {
                this.entity.level.addBlockEvent(this.targetBlock, chest.getBlockState().getBlock(), 1, 1);
            }
            else {
                this.entity.level.addBlockEvent(this.targetBlock, chest.getBlockState().getBlock(), 1, 0);
            }
        }
    }

    private void resetTarget() {
        Optional<BlockPos> targetPos = this.entity.getExtractPosition();
        targetPos.ifPresent((pos) -> {
            this.targetBlock = pos;
            BlockEntity blockEntity = this.entity.level.getBlockEntity(this.targetBlock);
            if (BlockEntity == null ||
                !blockEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, this.entity.getExtractFacing())
                         .isPresent()) {
                //the extract tile is not valid for extracting, so we disable this to allow exiting this task.
                this.entity.setExtractPosition(null);
            }
            });
    }
    //endregion Methods
}
