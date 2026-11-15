package com.techmonkey.raildirector;

import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RailDirectorData extends SavedData {
    private static final Codec<RailDirectorData> CODEC = DetectorConfig.CODEC.listOf().xmap(
        RailDirectorData::new,
        data -> new ArrayList<>(data.detectors.values())
    );

    private static final SavedDataType<RailDirectorData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(RailDirector.MOD_ID, "detectors"),
        RailDirectorData::new,
        CODEC,
        DataFixTypes.LEVEL
    );

    private final Map<Long, DetectorConfig> detectors = new HashMap<>();
    private final Set<Long> controlledRails = new HashSet<>();

    public RailDirectorData() {
    }

    private RailDirectorData(List<DetectorConfig> configs) {
        for (DetectorConfig config : configs) {
            detectors.put(config.detector().asLong(), config);
        }
        rebuildControlledRails();
    }

    public static RailDirectorData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public DetectorConfig getConfig(long detectorPos) {
        return detectors.get(detectorPos);
    }

    public boolean isControlled(long railPos) {
        return controlledRails.contains(railPos);
    }

    public void put(DetectorConfig config) {
        detectors.put(config.detector().asLong(), config);
        rebuildControlledRails();
        setDirty();
    }

    public void remove(long detectorPos) {
        if (detectors.remove(detectorPos) != null) {
            rebuildControlledRails();
            setDirty();
        }
    }

    private void rebuildControlledRails() {
        controlledRails.clear();
        for (DetectorConfig config : detectors.values()) {
            for (RailCommand command : config.commands()) {
                controlledRails.add(command.target().asLong());
            }
        }
    }
}
