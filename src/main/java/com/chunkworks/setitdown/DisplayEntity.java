/*
 * Set It Down - put any item down on any block face.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.setitdown;

import com.chunkworks.carried.api.Carried;
import com.chunkworks.setitdown.domain.Clicks;
import com.chunkworks.setitdown.domain.Face;
import com.chunkworks.setitdown.domain.FacePlacement;
import com.chunkworks.setitdown.domain.Hitbox;
import com.chunkworks.setitdown.domain.Orientation;
import com.chunkworks.setitdown.domain.StackLayout;
import com.mojang.logging.LogUtils;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * An item set down on a block face: one item, or a pile or cluster of the same, or a piece of
 * armour standing as the worn piece. It rests on its support, the block it was set on, and when
 * that goes (broken, blown up, pushed, flooded) it drops what it holds, as an item frame does.
 *
 * <p>Its point is on the face, {@link #OFF_FACE} out of it, so its block is the open one and its
 * light the open block's. Its face never changes; its turn and tip do, and with a turn its point
 * may move in so it stays wholly on the face (D-0002). The face rides in the spawn packet, as an
 * item frame's direction does, so the client knows the box from the first frame.
 *
 * <p>Right-click turns it, a sneaking right-click tips it (armour on a wall ignores that), a
 * left-click takes the top item back into the hitter's inventory or carried bag (Carried), on the
 * second hit within the window when the server requires two. Projectiles pass through it.
 */
public final class DisplayEntity extends Entity {
    private static final Logger LOG = LogUtils.getLogger();
    private static final EntityDataAccessor<ItemStack> DATA_ITEM = SynchedEntityData.defineId(DisplayEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Byte> DATA_COUNT = SynchedEntityData.defineId(DisplayEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_TURN = SynchedEntityData.defineId(DisplayEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_TIP = SynchedEntityData.defineId(DisplayEntity.class, EntityDataSerializers.BYTE);

    /** How far off its face a display's point sits, blocks. */
    public static final double OFF_FACE = 0.001;
    /** Ticks between checks that the support is still there. */
    private static final int CHECK_EVERY = 10;

    private Face face = Face.UP;
    private BlockPos support = BlockPos.ZERO;
    private boolean constructed;
    @Nullable
    private Clicks.Mark mark;

    /** The client renderer's own state for this display, kept with it so it goes when it goes; null on a server. */
    @Nullable
    public Object renderState;

    public DisplayEntity(EntityType<? extends DisplayEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.constructed = true;
        refreshBox();
    }

    /**
     * requires: {@code one} not empty
     * effects: returns a new display of {@code one} (one of it) resting on the block at
     * {@code support}, on its face {@code face}, its point at {@code at}, turned {@code turn}; not
     * yet in the level
     */
    public static DisplayEntity create(Level level, BlockPos support, Face face, Vec3 at, ItemStack one, int turn) {
        DisplayEntity d = new DisplayEntity(SetItDownContent.DISPLAY.get(), level);
        d.face = face;
        d.support = support.immutable();
        d.setItemRaw(one.copyWithCount(1));
        d.entityData.set(DATA_TURN, (byte) Math.floorMod(turn, Orientation.TURNS));
        d.setPos(at.x, at.y, at.z);
        return d;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_COUNT, (byte) 1);
        builder.define(DATA_TURN, (byte) 0);
        builder.define(DATA_TIP, (byte) 0);
    }

    // --- what it is ---------------------------------------------------------

    /** effects: the item shown, one of it (never to be changed in place) */
    public ItemStack item() {
        return entityData.get(DATA_ITEM);
    }

    /** effects: how many of the item it holds, 1 or more */
    public int count() {
        return Math.max(1, entityData.get(DATA_COUNT));
    }

    /** effects: its turn, 0 to 7 */
    public int turn() {
        return Math.floorMod(entityData.get(DATA_TURN), Orientation.TURNS);
    }

    /** effects: its tip, 0 to 2 */
    public int tip() {
        return Math.floorMod(entityData.get(DATA_TIP), Orientation.TIPS);
    }

    /** effects: the face of its support it rests on */
    public Face face() {
        return face;
    }

    /** effects: the block it rests on */
    public BlockPos support() {
        return support;
    }

    /** effects: how its item shares a spot */
    public StackLayout.Kind kind() {
        return Displayable.kind(item());
    }

    /** effects: its point in its support's face's own axes */
    public FacePlacement.Spot spot() {
        return spotFrom(support);
    }

    /** effects: its point in its face's own axes, measured from the least corner of the block at {@code origin} */
    public FacePlacement.Spot spotFrom(BlockPos origin) {
        Vec3 p = position();
        double x = p.x - origin.getX(), y = p.y - origin.getY(), z = p.z - origin.getZ();
        return new FacePlacement.Spot(face.u(x, y, z), face.v(x, y, z));
    }

    // --- changing it (server) ----------------------------------------------

    /** requires: 1 <= count + n <= most for its item. effects: adds {@code n} of its item */
    public void add(int n) {
        setCount(count() + n);
    }

    private void setCount(int count) {
        entityData.set(DATA_COUNT, (byte) Math.max(1, Math.min(count, Displayable.most(item()))));
    }

    private void setItemRaw(ItemStack one) {
        entityData.set(DATA_ITEM, one);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_TIP) || key.equals(DATA_ITEM)) {
            refreshBox();
        }
    }

    /** effects: moves its point, if its turn now reaches past its region's edge, back onto the region (D-0002) */
    private void reclamp() {
        Vec3 p = position();
        double out = face.n(p.x - support.getX(), p.y - support.getY(), p.z - support.getZ());
        FacePlacement.Spot here = spot();
        Optional<FacePlacement.Region> region = Displayable.region(level(), support, face, here, plane());
        if (region.isEmpty()) {
            return;
        }
        FacePlacement.Spot there = FacePlacement.clamp(here, region.get(), turn(), Displayable.size(item()).side());
        if (FacePlacement.distance(here, there) > 1e-6) {
            double[] q = face.point(there.u(), there.v(), out);
            setPos(support.getX() + q[0], support.getY() + q[1], support.getZ() + q[2]);
        }
    }

    // --- its box --------------------------------------------------------------

    private void refreshBox() {
        if (constructed) {
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    protected AABB makeBoundingBox() {
        if (!constructed) {
            return super.makeBoundingBox();
        }
        Hitbox.Size s = Hitbox.of(Displayable.piece(item()), face, tip(), Displayable.size(item()).side());
        int sign = face.stepX() + face.stepY() + face.stepZ();
        double[] a = face.point(-s.u() / 2, -s.v() / 2, 0.0);
        double[] b = face.point(s.u() / 2, s.v() / 2, sign * s.out());
        Vec3 p = position();
        return new AABB(p.x + a[0], p.y + a[1], p.z + a[2], p.x + b[0], p.y + b[1], p.z + b[2]);
    }

    @Override
    public void refreshDimensions() {
    }

    // --- living on its support --------------------------------------------------

    @Override
    public void tick() {
        if (level().isClientSide) {
            return;
        }
        checkBelowWorld();
        if (item().isEmpty()) {
            discard();
            return;
        }
        if ((tickCount + getId()) % CHECK_EVERY == 0 && !survives()) {
            dropAll();
        }
    }

    /** effects: whether its support still holds it up (see {@link Displayable#holds}); a support in an unloaded chunk is taken to */
    public boolean survives() {
        return !level().isLoaded(support) || Displayable.holds(level(), support, face, spot(), plane());
    }

    /** effects: the plane of the face it rests on, along the face's normal, in fractions of its support's block */
    public double plane() {
        Vec3 p = position();
        int sign = face.stepX() + face.stepY() + face.stepZ();
        return face.n(p.x - support.getX(), p.y - support.getY(), p.z - support.getZ()) - sign * OFF_FACE;
    }

    /** effects: drops everything it holds where it is (unless entity drops are off), then removes it */
    public void dropAll() {
        if (level().isClientSide || isRemoved()) {
            return;
        }
        if (level().getGameRules().getBoolean(GameRules.RULE_DOENTITYDROPS)) {
            ItemStack item = item();
            int left = count();
            while (left > 0 && !item.isEmpty()) {
                int n = Math.min(left, item.getMaxStackSize());
                spawnAtLocation(item.copyWithCount(n));
                left -= n;
            }
        }
        gameEvent(GameEvent.BLOCK_CHANGE);
        discard();
    }

    @Override
    public void move(MoverType type, Vec3 delta) {
        if (!level().isClientSide && !isRemoved() && delta.lengthSqr() > 0.0) {
            dropAll();
        }
    }

    @Override
    public void push(double x, double y, double z) {
        if (!level().isClientSide && !isRemoved() && x * x + y * y + z * z > 0.0) {
            dropAll();
        }
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {
    }

    @Override
    protected boolean repositionEntityAfterLoad() {
        return false;
    }

    // --- clicks -------------------------------------------------------------------

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeHitByProjectile() {
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return item().copyWithCount(1);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!level().mayInteract(player, support)) {
            return InteractionResult.CONSUME;
        }
        switch (Clicks.use(player.isSecondaryUseActive(), Displayable.armour(item()), face)) {
            case TURN -> {
                entityData.set(DATA_TURN, (byte) Orientation.nextTurn(turn()));
                reclamp();
                playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F, 1.0F);
            }
            case TIP -> {
                entityData.set(DATA_TIP, (byte) Orientation.nextTip(tip()));
                playSound(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 1.0F, 0.8F);
            }
            case NOTHING -> {
            }
        }
        gameEvent(GameEvent.BLOCK_CHANGE, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        if (attacker instanceof Player player) {
            return !level().mayInteract(player, support) || hurt(damageSources().playerAttack(player), 0.0F);
        }
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (isInvulnerableTo(source)) {
            return false;
        }
        if (level().isClientSide || isRemoved()) {
            return true;
        }
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            dropAll();
            return true;
        }
        if (source.getDirectEntity() instanceof Player player) {
            take(player);
            return true;
        }
        return false;
    }

    /** effects: on a hit by {@code player}: takes the top item (or all, by the server's config) back into what they carry, or marks the display for a second hit */
    private void take(Player player) {
        long now = level().getGameTime();
        if (!Clicks.takes(SetItDownConfig.REQUIRE_TWO_HITS.get(), mark, player.getUUID(), now, SetItDownConfig.BREAK_CLICK_WINDOW_TICKS.get())) {
            mark = new Clicks.Mark(player.getUUID(), now);
            player.displayClientMessage(Component.translatable("setitdown.message.hit_again"), true);
            return;
        }
        mark = null;
        int n = SetItDownConfig.DROP_ENTIRE_PILE.get() ? count() : 1;
        ItemStack out = item().copyWithCount(n);
        int left = count() - n;
        playSound(SoundEvents.ITEM_FRAME_REMOVE_ITEM, 1.0F, 1.0F);
        gameEvent(GameEvent.BLOCK_CHANGE, player);
        if (left <= 0) {
            discard();
        } else {
            setCount(left);
        }
        if (!player.hasInfiniteMaterials()) {
            Carried.giveOrDrop(player, out);
        }
    }

    // --- saving and the spawn packet ----------------------------------------------

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ItemStack item = item();
        if (!item.isEmpty()) {
            tag.put("Item", item.save(registryAccess()));
        }
        tag.putByte("Count", (byte) count());
        tag.putByte("Face", (byte) face.ordinal());
        tag.putByte("Turn", (byte) turn());
        tag.putByte("Tip", (byte) tip());
        tag.put("Support", NbtUtils.writeBlockPos(support));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ItemStack item = tag.contains("Item") ? ItemStack.parse(registryAccess(), tag.getCompound("Item")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        face = Face.values()[Math.max(0, Math.min(Face.values().length - 1, tag.getByte("Face")))];
        setItemRaw(item.isEmpty() ? ItemStack.EMPTY : item.copyWithCount(1));
        entityData.set(DATA_TURN, (byte) Math.floorMod(tag.getByte("Turn"), Orientation.TURNS));
        entityData.set(DATA_TIP, (byte) Math.floorMod(tag.getByte("Tip"), Orientation.TIPS));
        int count = tag.contains("Count") ? tag.getByte("Count") : 1;
        if (!item.isEmpty() && count > Displayable.most(item)) {
            LOG.warn("Set It Down: a display of {} held {}, more than a spot holds; keeping {}", item, count, Displayable.most(item));
        }
        entityData.set(DATA_COUNT, (byte) Math.max(1, Math.min(count, item.isEmpty() ? 1 : Displayable.most(item))));
        Vec3 p = position();
        support = NbtUtils.readBlockPos(tag, "Support").orElse(BlockPos.containing(p.x - face.stepX() * 0.01,
                p.y - face.stepY() * 0.01, p.z - face.stepZ() * 0.01));
        refreshBox();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity, face.ordinal());
    }

    @Override
    public void recreateFromPacket(ClientboundAddEntityPacket packet) {
        super.recreateFromPacket(packet);
        int data = packet.getData();
        face = Face.values()[Math.max(0, Math.min(Face.values().length - 1, data))];
        refreshBox();
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        double range = 16.0 * 64.0 * getViewScale();
        return distance < range * range;
    }
}
