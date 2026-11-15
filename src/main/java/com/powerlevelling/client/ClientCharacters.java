package com.powerlevelling.client;

import java.util.HashMap;
import java.util.Map;

import com.powerlevelling.character.CharacterSnapshot;
import com.powerlevelling.network.CharacterResultPayload;
import com.powerlevelling.network.CharacterSyncPayload;

/**
 * Client cache of every visible player's character (from CharacterSyncPayload), plus the local player's creation state
 * (K routing, HUD badge) and the last submit result for the character screen. Plain data, client thread only; deliberately
 * imports no client-only Minecraft classes (referenced from the payload registration).
 */
public final class ClientCharacters {
    private static final Map<Integer, CharacterSnapshot> SNAPSHOTS = new HashMap<>();

    /**
     * What the character screen is previewing for the local player (null = nothing). {@link #effective} returns it
     * instead of the real snapshot for the local player, so renderers show the preview without anything being sent.
     */
    public static CharacterSnapshot previewOverride;

    /** Entity id of the local player, learned from the sync payload's context (-1 = unknown). */
    private static int selfId = -1;
    private static CharacterSnapshot self;

    // Last CharacterResultPayload; the screen polls resultSerial for a change (it increments on every result).
    public static int resultSerial;
    public static boolean resultOk;
    public static String resultReason = "";

    private ClientCharacters() {
    }

    // ---- server updates ------------------------------------------------------------------------------------------

    /** @param selfEntityId the local player's entity id (payload context), used to recognise our own snapshot. */
    public static void apply(CharacterSyncPayload p, int selfEntityId) {
        selfId = selfEntityId;
        SNAPSHOTS.put(p.entityId(), p.snapshot());
        if (p.entityId() == selfEntityId) self = p.snapshot();
    }

    public static void onResult(CharacterResultPayload p) {
        resultOk = p.ok();
        resultReason = p.reasonKey();
        resultSerial++;
    }

    // ---- queries -------------------------------------------------------------------------------------------------

    /** The synced snapshot of an entity, or null if none arrived. */
    public static CharacterSnapshot get(int entityId) {
        return SNAPSHOTS.get(entityId);
    }

    /** What to render: the screen's preview for the local player while one is set, otherwise the synced snapshot. */
    public static CharacterSnapshot effective(int entityId) {
        if (previewOverride != null && entityId == selfId) return previewOverride;
        return SNAPSHOTS.get(entityId);
    }

    /** True once the server has told us about our own character. */
    public static boolean selfKnown() {
        return self != null;
    }

    public static CharacterSnapshot selfSnapshot() {
        return self;
    }

    /** Falls back to the progression sync's flag until our own snapshot arrives. */
    public static boolean selfCreated() {
        return self != null ? self.created() : ClientPlayerData.characterCreated;
    }

    /** Race or nature went missing: K opens a free re-creation. */
    public static boolean selfFreeRecreate() {
        return self != null && self.freeRecreate();
    }

    // ---- lifecycle -----------------------------------------------------------------------------------------------

    /** An entity left the client level. Our own snapshot is kept (the local player is removed and re-added on respawn / dimension change). */
    public static void remove(int entityId) {
        if (entityId == selfId) return;
        SNAPSHOTS.remove(entityId);
    }

    public static void reset() {
        SNAPSHOTS.clear();
        previewOverride = null;
        selfId = -1;
        self = null;
        resultSerial = 0;
        resultOk = false;
        resultReason = "";
    }
}
