package net.nicguzzo.wands.wand;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.Vec3;
import net.nicguzzo.wands.WandsMod;
import net.nicguzzo.wands.compat.Compat;
import net.nicguzzo.wands.items.BlueprintItem;
import net.nicguzzo.wands.items.WandItem;
import net.nicguzzo.wands.networking.Networking;

public class BlueprintGameTests implements FabricGameTest {
    private ServerPlayer player(GameTestHelper test, GameType mode) {
        ServerPlayer player = test.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                new ItemStack(BuiltInRegistries.ITEM.get(WandsMod.diamond_wand_id.id())));
        if (PlayerWand.get(player) == null) {
            PlayerWand.add_player(player);
        }
        return player;
    }

    private ItemStack blank() {
        return new ItemStack(BuiltInRegistries.ITEM.get(WandsMod.blueprint_id.id()));
    }

    private void action(ServerPlayer player, int action, String first, String second) {
        Networking.ReceiveBlueprintAction(player, action, first, second);
    }

    private void copy(GameTestHelper test, ServerPlayer player, BlockPos start, BlockPos end) {
        WandProps.switchMode(player.getMainHandItem(), WandProps.Mode.COPY);
        BlockPos first = test.absolutePos(start);
        Networking.ReceivePosPacket(player, Direction.UP, true, true, first, test.absolutePos(end),
                Vec3.atCenterOf(first), 0);
    }

    private void paste(GameTestHelper test, ServerPlayer player, BlockPos origin) {
        paste(test, player, origin, WandProps.Action.PLACE);
    }

    private void paste(GameTestHelper test, ServerPlayer player, BlockPos origin, WandProps.Action action) {
        ItemStack wand = player.getMainHandItem();
        WandProps.switchMode(wand, WandProps.Mode.PASTE);
        WandProps.setAction(wand, action);
        BlockPos absolute = test.absolutePos(origin);
        Networking.ReceivePosPacket(player, Direction.UP, true, false, absolute, null,
                Vec3.atCenterOf(absolute), 0);
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void placeResumesPastTheWandLimit(GameTestHelper test) {
        checkTierLimitedPaste(test, WandProps.Action.PLACE, Blocks.AIR, Blocks.STONE);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void destroyResumesPastTheWandLimit(GameTestHelper test) {
        checkTierLimitedPaste(test, WandProps.Action.DESTROY, Blocks.STONE, Blocks.AIR);
        test.succeed();
    }

    private void checkTierLimitedPaste(GameTestHelper test, WandProps.Action operation, Block before, Block after) {
        ServerPlayer player = player(test, GameType.CREATIVE);
        BlockPos origin = new BlockPos(2, 2, 2);
        ItemStack blueprint = blank();
        List<CopyBuffer> cells = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            cells.add(new CopyBuffer(new BlockPos(index, 0, 0), Blocks.STONE.defaultBlockState()));
            test.setBlock(origin.east(index), before);
        }
        BlueprintItem.saveCopyBufferToBlueprint(blueprint, cells);
        player.getInventory().setItem(2, blueprint);
        action(player, 3, BlueprintItem.getId(blueprint), "");

        WandItem stoneWand = (WandItem) BuiltInRegistries.ITEM.get(WandsMod.stone_wand_id.id());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(stoneWand));
        int previousLimit = stoneWand.limit;
        try {
            stoneWand.limit = 2;
            paste(test, player, origin, operation);
            test.assertBlockPresent(after, origin);
            test.assertBlockPresent(after, origin.east());
            test.assertBlockPresent(before, origin.east(2));
            test.assertBlockPresent(before, origin.east(3));

            paste(test, player, origin, operation);
            int changed = 0;
            for (int index = 0; index < 4; index++) {
                if (test.getLevel().getBlockState(test.absolutePos(origin.east(index))).is(after)) {
                    changed++;
                }
            }
            test.assertTrue(changed == 4, operation.name() + " should change 4 cells after two clicks, changed " + changed);
        } finally {
            stoneWand.limit = previousLimit;
        }
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void savedLayoutSurvivesReloadAndNewCopy(GameTestHelper test) {
        ServerPlayer player = player(test, GameType.CREATIVE);
        ItemStack wand = player.getMainHandItem();
        BlockPos firstCorner = new BlockPos(4, 3, 3);
        BlockPos secondCorner = new BlockPos(2, 2, 2);
        BlockPos stairs = new BlockPos(3, 2, 2);
        test.setBlock(firstCorner, Blocks.GLASS);
        test.setBlock(secondCorner, Blocks.OAK_PLANKS);
        test.setBlock(stairs, Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH));
        copy(test, player, firstCorner, secondCorner);
        test.assertTrue(PlayerWand.get(player).copy_paste_buffer.size() == 3, "Copy must contain exactly three solid blocks");

        ItemStack blueprint = blank();
        player.setItemInHand(InteractionHand.MAIN_HAND, blueprint);
        action(player, 0, "", "");
        action(player, 1, "minecraft:oak_planks", "minecraft:spruce_planks");
        action(player, 1, "minecraft:oak_stairs", "minecraft:spruce_stairs");
        action(player, 2, "Gate corner", "");
        var registries = test.getLevel().registryAccess();
        ItemStack reloaded = ItemStack.parse(registries, blueprint.save(registries)).orElseThrow();
        String id = BlueprintItem.getId(blueprint);
        test.assertTrue(!id.isEmpty() && id.equals(BlueprintItem.getId(reloaded)), "Blueprint identity was lost during item serialization");
        test.assertTrue(BlueprintItem.getBlueprintName(reloaded).equals("Gate corner"), "Name was lost during item serialization");
        test.assertTrue(BlueprintItem.getTotalBlockCount(reloaded) == 3
                && Arrays.equals(BlueprintItem.getDimensions(reloaded), new int[]{3, 2, 2}), "Incorrect saved dimensions or block count");
        test.assertTrue(BlueprintItem.getReplacements(reloaded).get(Blocks.OAK_STAIRS) == Blocks.SPRUCE_STAIRS,
                "Material replacement was lost during item serialization");
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        player.getInventory().setItem(2, reloaded);

        BlockPos newerCopy = new BlockPos(2, 2, 5);
        test.setBlock(newerCopy, Blocks.DIAMOND_BLOCK);
        copy(test, player, newerCopy, newerCopy);
        test.assertTrue(PlayerWand.get(player).getActiveBlueprintId().isEmpty(), "New copy did not select the in-memory source");
        BlockPos memoryTarget = new BlockPos(4, 2, 5);
        test.setBlock(memoryTarget, Blocks.AIR);
        paste(test, player, memoryTarget);
        test.assertBlockPresent(Blocks.DIAMOND_BLOCK, memoryTarget);

        action(player, 3, id, "");
        WandProps.setRotation(wand, Rotation.CLOCKWISE_90);
        BlockPos origin = new BlockPos(6, 3, 5);
        BlockPos expectedPlanks = origin.offset(1, -1, -2);
        BlockPos expectedStairs = origin.offset(1, -1, -1);
        BlockPos preservedGap = origin.offset(0, -1, -1);
        test.setBlock(origin, Blocks.AIR);
        test.setBlock(expectedPlanks, Blocks.AIR);
        test.setBlock(expectedStairs, Blocks.AIR);
        test.setBlock(preservedGap, Blocks.GOLD_BLOCK);
        paste(test, player, origin);
        test.assertBlockPresent(Blocks.GLASS, origin);
        test.assertBlockPresent(Blocks.SPRUCE_PLANKS, expectedPlanks);
        test.assertTrue(test.getLevel().getBlockState(test.absolutePos(expectedStairs)).is(Blocks.SPRUCE_STAIRS)
                && test.getLevel().getBlockState(test.absolutePos(expectedStairs)).getValue(StairBlock.FACING) == Direction.EAST,
                "Rotated stair did not preserve its substituted material and facing");
        test.assertBlockPresent(Blocks.GOLD_BLOCK, preservedGap);
        test.assertBlockPresent(Blocks.OAK_PLANKS, secondCorner);
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void shulkerSaveUpdatesOnlyTheSelectedSlot(GameTestHelper test) {
        ServerPlayer player = player(test, GameType.CREATIVE);
        BlockPos source = new BlockPos(2, 2, 2);
        test.setBlock(source, Blocks.IRON_BLOCK);
        copy(test, player, source, source);
        ItemStack blueprint = blank();
        String id = BlueprintItem.getOrCreateId(blueprint);
        BlueprintItem.setBlueprintName(blueprint, "Shulker design");
        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        List<ItemStack> slots = new ArrayList<>(Compat.get_shulker_contents(shulker));
        slots.set(5, new ItemStack(Items.EMERALD, 3));
        slots.set(17, blueprint);
        Compat.set_shulker_contents(shulker, slots);
        player.getInventory().setItem(2, shulker);

        action(player, 4, id, "");
        List<ItemStack> actual = Compat.get_shulker_contents(shulker);
        ItemStack saved = actual.get(17);
        test.assertTrue(actual.get(5).is(Items.EMERALD) && actual.get(5).getCount() == 3, "Adjacent shulker contents changed");
        test.assertTrue(id.equals(BlueprintItem.getId(saved)) && BlueprintItem.getBlueprintName(saved).equals("Shulker design")
                && BlueprintItem.getTotalBlockCount(saved) == 1 && saved.getCount() == 1, "Saved blueprint or its item metadata was lost");
        test.assertTrue(PlayerWand.get(player).getActiveBlueprintId().equals(id), "Shulker blueprint was not selected after save");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void survivalResumesWithoutChargingForExistingOrBlockedBlocks(GameTestHelper test) {
        ServerPlayer player = player(test, GameType.SURVIVAL);
        ItemStack blueprint = blank();
        BlueprintItem.saveCopyBufferToBlueprint(blueprint, List.of(
                new CopyBuffer(BlockPos.ZERO, Blocks.OAK_PLANKS.defaultBlockState()),
                new CopyBuffer(new BlockPos(1, 0, 0), Blocks.OAK_PLANKS.defaultBlockState()),
                new CopyBuffer(new BlockPos(2, 0, 0), Blocks.OAK_PLANKS.defaultBlockState()),
                new CopyBuffer(new BlockPos(3, 0, 0), Blocks.GLASS.defaultBlockState())));
        BlueprintItem.setReplacement(blueprint, Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS);
        player.getInventory().setItem(2, blueprint);
        player.getInventory().setItem(3, new ItemStack(Items.SPRUCE_PLANKS, 2));
        player.getInventory().setItem(4, new ItemStack(Items.OAK_PLANKS, 4));
        action(player, 3, BlueprintItem.getId(blueprint), "");

        BlockPos origin = new BlockPos(2, 2, 2);
        test.setBlock(origin, Blocks.SPRUCE_PLANKS);
        test.setBlock(origin.east(), Blocks.AIR);
        test.setBlock(origin.east(2), Blocks.GOLD_BLOCK);
        test.setBlock(origin.east(3), Blocks.AIR);
        paste(test, player, origin);
        test.assertBlockPresent(Blocks.SPRUCE_PLANKS, origin);
        test.assertBlockPresent(Blocks.SPRUCE_PLANKS, origin.east());
        test.assertBlockPresent(Blocks.GOLD_BLOCK, origin.east(2));
        test.assertBlockPresent(Blocks.AIR, origin.east(3));
        test.assertTrue(player.getInventory().getItem(3).getCount() == 1, "First paste should consume exactly one spruce plank");

        player.getInventory().setItem(5, new ItemStack(Items.GLASS, 2));
        paste(test, player, origin);
        paste(test, player, origin);
        test.assertBlockPresent(Blocks.GLASS, origin.east(3));
        test.assertBlockPresent(Blocks.GOLD_BLOCK, origin.east(2));
        test.assertTrue(player.getInventory().getItem(3).getCount() == 1 && player.getInventory().getItem(4).getCount() == 4
                && player.getInventory().getItem(5).getCount() == 1, "Repeated paste charged for blocks it did not place");
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void blueprintPreservesOffsetsBeyond511Blocks(GameTestHelper test) {
        ItemStack blueprint = blank();
        BlockPos edge = new BlockPos(511, 0, 0);
        BlockPos beyond = new BlockPos(512, 0, 0);
        BlueprintItem.saveCopyBufferToBlueprint(blueprint, List.of(
                new CopyBuffer(edge, Blocks.STONE.defaultBlockState()),
                new CopyBuffer(beyond, Blocks.GLASS.defaultBlockState())));
        var restored = BlueprintItem.getCopyBuffer(blueprint);
        test.assertTrue(restored.size() == 2 && restored.get(0).pos.equals(edge), "Offset +511 failed to round-trip");
        test.assertTrue(restored.get(1).pos.equals(beyond), "Expected +512 after save, got " + restored.get(1).pos.getX());
        test.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE)
    public void selectorCanSaveToAPristineBlankBlueprint(GameTestHelper test) {
        ServerPlayer player = player(test, GameType.CREATIVE);
        BlockPos source = new BlockPos(2, 2, 2);
        test.setBlock(source, Blocks.STONE);
        copy(test, player, source, source);
        ItemStack blueprint = blank();
        player.getInventory().setItem(2, blueprint);
        ItemStack clientSynchronizedCopy = blueprint.copy();
        // The selector discovers an unassigned ID on its local inventory copy before sending the action.
        String selected = BlueprintItem.getOrCreateId(clientSynchronizedCopy);
        action(player, 4, selected, "");
        test.assertTrue(BlueprintItem.getTotalBlockCount(blueprint) == 1,
                "Saving by the client-assigned ID left the pristine server-side blueprint empty");
        test.succeed();
    }
}
