package com.archetipe.microworld.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PendingMegablockRegistry {

    private static final Map<UUID, PendingMegablock> PENDING = new HashMap<>();

    private PendingMegablockRegistry() {}

    public static void set(UUID player, PendingMegablock data) { PENDING.put(player, data); }
    public static PendingMegablock get(UUID player) { return PENDING.get(player); }
    public static void clear(UUID player) { PENDING.remove(player); }
}