/*
 * MIT License
 *
 * Copyright 2021 vemerion
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

package com.github.klikli_dev.occultism.common.entity;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.github.klikli_dev.occultism.common.item.tool.FamiliarRingItem;

import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.potion.EffectInstance;

/***
 * An interface representing a familiar entity. See {@link OtherworldBirdEntity}
 * for an example of an entity that implements this interface. Used by
 * {@link FamiliarRingItem}.
 *
 */
public interface IFamiliar {

    /***
     * Gets the entity that is the owner of this familiar, i.e. the player that
     * summoned the familiar via a ritual.
     * 
     * @return The owner of this familiar
     */
    @Nullable
    LivingEntity getFamiliarOwner();

    /**
     * Sets the owner entity of this familiar.
     * @param owner the new owner of this familiar
     */
     void setFamiliarOwner(LivingEntity owner);

    /***
     * Gets the actual familiar as an entity.
     * 
     * @return The familiar.
     */
    @Nonnull
    Entity getEntity();

    /***
     * Gets fresh instances of effects that this familiar should apply to the owner.
     * 
     * @return The effects to apply to the owner.
     */
    @Nonnull
    Iterable<EffectInstance> getFamiliarEffects();

    /***
     * This method is called every tick when this familiar is captured in a
     * {@link FamiliarRingItem}.
     * 
     * @param wearer The wearer of the curio
     */
    default void curioTick(LivingEntity wearer) {

    }
}
