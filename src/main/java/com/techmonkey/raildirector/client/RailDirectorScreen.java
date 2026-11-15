package com.techmonkey.raildirector.client;

import com.techmonkey.raildirector.RailDirections;
import com.techmonkey.raildirector.RailShapes;
import com.techmonkey.raildirector.network.OpenEditorPayload;
import com.techmonkey.raildirector.network.SaveConfigPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.properties.RailShape;

public final class RailDirectorScreen extends Screen {
    private static final int PANEL_WIDTH = 470;
    private static final int PANEL_HEIGHT = 310;
    private static final int TRACK = 0xFF777777;
    private static final int TRACK_TIE = 0xFF4B3424;
    private static final int SELECTED_TRACK = 0xFF42E7F5;

    private final OpenEditorPayload payload;
    private int inactiveMask;
    private int activeMask;
    private boolean editingActive;
    private int panelLeft;
    private int panelTop;
    private int diagramX;
    private int diagramY;

    public RailDirectorScreen(OpenEditorPayload payload) {
        super(Component.translatable("screen.raildirector.title"));
        this.payload = payload;
        this.inactiveMask = normalizePair(shapeMask(RailShapes.fromOrdinal(payload.absentShape())));
        this.activeMask = normalizePair(shapeMask(RailShapes.fromOrdinal(payload.presentShape())));
    }

    @Override
    protected void init() {
        panelLeft = (width - PANEL_WIDTH) / 2;
        panelTop = (height - PANEL_HEIGHT) / 2;
        diagramX = panelLeft + 145;
        diagramY = panelTop + 167;

        addStateButton(false, panelLeft + 278, panelTop + 72);
        addStateButton(true, panelLeft + 368, panelTop + 72);

        addDirectionButton(0, diagramX - 24, diagramY - 112);
        addDirectionButton(1, diagramX + 81, diagramY - 10);
        addDirectionButton(2, diagramX - 24, diagramY + 92);
        addDirectionButton(3, diagramX - 129, diagramY - 10);

        addRenderableWidget(Button.builder(Component.translatable("screen.raildirector.clear"), button -> {
            send(true);
            onClose();
        }).bounds(panelLeft + 15, panelTop + 268, 170, 22).build());

        Button save = Button.builder(Component.translatable("screen.raildirector.save"), button -> {
            send(false);
            onClose();
        }).bounds(panelLeft + 285, panelTop + 268, 170, 22).build();
        save.active = Integer.bitCount(inactiveMask) == 2 && Integer.bitCount(activeMask) == 2;
        addRenderableWidget(save);
    }

    private void addStateButton(boolean active, int x, int y) {
        boolean selected = editingActive == active;
        Component label = Component.literal((selected ? "Editing: " : "") + (active ? "Active" : "Inactive"));
        addRenderableWidget(Button.builder(label, button -> {
            editingActive = active;
            rebuildWidgets();
        }).bounds(x, y, 82, 22).build());
    }

    private void addDirectionButton(int index, int x, int y) {
        Direction direction = RailDirections.CARDINALS[index];
        String name = direction.getName().substring(0, 1).toUpperCase();
        boolean detected = (payload.connectionMask() & (1 << index)) != 0;
        boolean selected = (currentMask() & (1 << index)) != 0;
        Component label = Component.literal(selected ? name + " ON" : detected ? name : name + " —");
        Button button = Button.builder(label, ignored -> toggleDirection(index)).bounds(x, y, 48, 20).build();
        button.active = detected;
        addRenderableWidget(button);
    }

    private void toggleDirection(int index) {
        int bit = 1 << index;
        int mask = currentMask();
        if ((mask & bit) != 0) {
            mask &= ~bit;
        } else if (Integer.bitCount(mask) < 2) {
            mask |= bit;
        } else {
            int first = Integer.numberOfTrailingZeros(mask);
            mask = (mask & ~(1 << first)) | bit;
        }
        setCurrentMask(mask);
        rebuildWidgets();
    }

    private int currentMask() {
        return editingActive ? activeMask : inactiveMask;
    }

    private void setCurrentMask(int mask) {
        if (editingActive) activeMask = mask;
        else inactiveMask = mask;
    }

    private int normalizePair(int requested) {
        int detected = payload.connectionMask();
        int valid = requested & detected;
        if (Integer.bitCount(valid) == 2) return valid;
        int pair = 0;
        for (int i = 0; i < RailDirections.CARDINALS.length && Integer.bitCount(pair) < 2; i++) {
            if ((detected & (1 << i)) != 0) pair |= 1 << i;
        }
        return pair;
    }

    private static int shapeMask(RailShape shape) {
        return switch (shape) {
            case NORTH_SOUTH -> bit(Direction.NORTH) | bit(Direction.SOUTH);
            case EAST_WEST -> bit(Direction.EAST) | bit(Direction.WEST);
            case SOUTH_EAST -> bit(Direction.SOUTH) | bit(Direction.EAST);
            case SOUTH_WEST -> bit(Direction.SOUTH) | bit(Direction.WEST);
            case NORTH_WEST -> bit(Direction.NORTH) | bit(Direction.WEST);
            case NORTH_EAST -> bit(Direction.NORTH) | bit(Direction.EAST);
            default -> bit(Direction.NORTH) | bit(Direction.SOUTH);
        };
    }

    private static int bit(Direction direction) {
        for (int i = 0; i < RailDirections.CARDINALS.length; i++) {
            if (RailDirections.CARDINALS[i] == direction) return 1 << i;
        }
        return 0;
    }

    private static RailShape maskShape(int mask) {
        for (RailShape shape : RailShapes.FLAT) {
            if (shapeMask(shape) == mask) return shape;
        }
        return RailShape.NORTH_SOUTH;
    }

    private void send(boolean clear) {
        if (ClientPlayNetworking.canSend(SaveConfigPayload.TYPE)) {
            ClientPlayNetworking.send(new SaveConfigPayload(
                payload.detector(), clear,
                RailShapes.ordinal(maskShape(inactiveMask)),
                RailShapes.ordinal(maskShape(activeMask))));
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        extractTransparentBackground(graphics);
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xEE101010);
        graphics.outline(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT, 0xFFCC3333);
        graphics.centeredText(font, title, width / 2, panelTop + 10, 0xFFFFFFFF);
        graphics.centeredText(font, Component.translatable("screen.raildirector.help"),
            width / 2, panelTop + 29, 0xFFB8B8B8);
        graphics.centeredText(font, Component.literal("Top-down view (world directions)"),
            diagramX, panelTop + 49, 0xFFAAAAAA);

        drawTracks(graphics);

        graphics.centeredText(font, Component.literal("Detector"), diagramX, diagramY - 4, 0xFFFFFFFF);
        graphics.centeredText(font, Component.literal("Configure"), panelLeft + 365, panelTop + 49, 0xFFAAAAAA);
        graphics.text(font, Component.literal("Inactive: " + routeName(inactiveMask)),
            panelLeft + 285, panelTop + 119, 0xFFFFFFFF, false);
        graphics.text(font, Component.literal("Active:   " + routeName(activeMask)),
            panelLeft + 285, panelTop + 142, 0xFFFFFFFF, false);
        graphics.text(font, Component.literal("Click two rail ends to connect."),
            panelLeft + 285, panelTop + 180, 0xFFB8B8B8, false);
        graphics.text(font, Component.literal(editingActive ? "Editing ACTIVE route" : "Editing INACTIVE route"),
            panelLeft + 285, panelTop + 202, SELECTED_TRACK, false);
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    private void drawTracks(GuiGraphicsExtractor graphics) {
        int selected = currentMask();
        for (int i = 0; i < RailDirections.CARDINALS.length; i++) {
            if ((payload.connectionMask() & (1 << i)) != 0) {
                drawArm(graphics, RailDirections.CARDINALS[i], (selected & (1 << i)) != 0);
            }
        }
        graphics.fill(diagramX - 27, diagramY - 27, diagramX + 28, diagramY + 28, 0xFF351515);
        graphics.outline(diagramX - 27, diagramY - 27, 55, 55, 0xFFFF5555);
    }

    private void drawArm(GuiGraphicsExtractor graphics, Direction direction, boolean selected) {
        int rail = selected ? SELECTED_TRACK : TRACK;
        if (direction == Direction.NORTH || direction == Direction.SOUTH) {
            int y1 = direction == Direction.NORTH ? diagramY - 92 : diagramY + 28;
            int y2 = direction == Direction.NORTH ? diagramY - 27 : diagramY + 93;
            graphics.fill(diagramX - 9, y1, diagramX - 6, y2, rail);
            graphics.fill(diagramX + 6, y1, diagramX + 9, y2, rail);
            for (int y = y1 + 4; y < y2; y += 12) graphics.fill(diagramX - 13, y, diagramX + 14, y + 3, TRACK_TIE);
        } else {
            int x1 = direction == Direction.WEST ? diagramX - 109 : diagramX + 28;
            int x2 = direction == Direction.WEST ? diagramX - 27 : diagramX + 110;
            graphics.fill(x1, diagramY - 9, x2, diagramY - 6, rail);
            graphics.fill(x1, diagramY + 6, x2, diagramY + 9, rail);
            for (int x = x1 + 4; x < x2; x += 12) graphics.fill(x, diagramY - 13, x + 3, diagramY + 14, TRACK_TIE);
        }
    }

    private static String routeName(int mask) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < RailDirections.CARDINALS.length; i++) {
            if ((mask & (1 << i)) != 0) {
                if (!result.isEmpty()) result.append(" ↔ ");
                result.append(RailDirections.CARDINALS[i].getName().substring(0, 1).toUpperCase());
            }
        }
        return result.isEmpty() ? "Choose two" : result.toString();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }
}
