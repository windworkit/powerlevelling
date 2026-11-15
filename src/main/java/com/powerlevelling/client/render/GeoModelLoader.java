package com.powerlevelling.client.render;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

/**
 * Client reload listener: scans every file ending in .geo.json below assets/&lt;ns&gt;/powerlevelling/geo (Bedrock geometry subset, as
 * written by Blockbench), converts each file to a baked {@link ModelPart} tree and keeps them by id. The id of
 * {@code assets/powerlevelling/powerlevelling/geo/hair/flare.geo.json} is {@code powerlevelling:hair/flare} (this is what a
 * cosmetic_part's "model" field refers to). Registered in ClientEvents (RegisterClientReloadListenersEvent), so F3+T rebuilds
 * the map; renderers look models up by id every frame ({@link #get}).
 *
 * <h2>README for art authors (C7)</h2>
 * <ul>
 *   <li>Format: {@code format_version 1.12.0} style, {@code "minecraft:geometry"[0]} with {@code description{identifier,
 *       texture_width, texture_height}} and {@code bones[]{name, parent?, pivot[3], rotation[3]?, mirror?, cubes[]{origin[3],
 *       size[3], uv[2], inflate?, mirror?}}}. Only BOX UV ({@code "uv": [u, v]}) is supported; cubes with per-face UV, and
 *       per-cube rotation/pivot, are skipped with a warning. Units are pixels (16 px = 1 block).</li>
 *   <li>ANCHOR: author as if the model sits on a standard Steve whose feet are at y=0. The anchor point is Bedrock
 *       <b>(0, 24, 0)</b> = the neck/head pivot (head-anchored parts) = the body pivot (body-anchored parts). The loader
 *       positions every ROOT bone relative to (0,24,0); the layer then applies head.translateAndRotate (anchor "head") or
 *       body.translateAndRotate (anchor "body"), so the part follows head turns, nodding and sneaking. The head cube is
 *       (-4,24,-4)..(4,32,4); the vanilla hat layer is that +0.5.</li>
 *   <li>A bone's {@code pivot} is its rotation centre, in the same absolute Bedrock coordinates as the cube origins (the
 *       loader converts to parent-relative). A root bone with no rotation may use any pivot.</li>
 *   <li>Bone names must be unique within a file. Names are what {@code helmet_visible_bones} refers to.</li>
 *   <li>Texture: grayscale, size = description texture_width x texture_height (64x32 is typical); it is tinted per player.</li>
 *   <li>Coordinate conversion is documented at {@link #toJavaRotation}, {@link #poseOf} and {@link #addBone}.</li>
 * </ul>
 */
public final class GeoModelLoader extends SimplePreparableReloadListener<Map<ResourceLocation, GeoModelLoader.GeoModel>> {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DIR = "powerlevelling/geo";
    private static final String SUFFIX = ".geo.json";
    /** Bedrock-space anchor all root bones are positioned against (the neck / body pivot of a Steve). */
    private static final float[] ANCHOR = {0f, 24f, 0f};

    private static volatile Map<ResourceLocation, GeoModel> models = Map.of();

    /**
     * One baked file. {@code bones} maps every bone name to its ModelPart; {@code parents} maps a bone name to its parent
     * bone name (absent for root bones).
     */
    public record GeoModel(ModelPart root, Map<String, ModelPart> bones, Map<String, String> parents) {
        /**
         * Shows only the named bones (plus everything below them, plus their ancestors so the chain stays posed).
         * Ancestors of a named bone render their own cubes too, so authors should keep group bones cube-less or accept that.
         * Pair with {@link #showAll()} in a finally block.
         */
        public void showOnly(Set<String> names) {
            Set<String> shown = new HashSet<>();
            for (String name : names) {
                if (!bones.containsKey(name)) continue;
                shown.add(name);
                for (String p = parents.get(name); p != null; p = parents.get(p)) shown.add(p);
            }
            for (Map.Entry<String, ModelPart> e : bones.entrySet()) {
                boolean show = shown.contains(e.getKey());
                if (!show) { // a descendant of a named bone also shows
                    for (String p = parents.get(e.getKey()); p != null && !show; p = parents.get(p)) show = names.contains(p);
                }
                e.getValue().visible = show;
            }
        }

        public void showAll() {
            for (ModelPart part : bones.values()) part.visible = true;
        }
    }

    /** The model for a cosmetic_part's model id, or null if it is unknown / failed to load. */
    public static GeoModel get(ResourceLocation id) {
        return models.get(id);
    }

    // ---- reload ---------------------------------------------------------------------------------------------------

    @Override
    protected Map<ResourceLocation, GeoModel> prepare(ResourceManager resources, ProfilerFiller profiler) {
        Map<ResourceLocation, GeoModel> out = new HashMap<>();
        Map<ResourceLocation, Resource> found = resources.listResources(DIR, rl -> rl.getPath().endsWith(SUFFIX));
        for (Map.Entry<ResourceLocation, Resource> e : found.entrySet()) {
            ResourceLocation file = e.getKey();
            String path = file.getPath();
            String idPath = path.substring(DIR.length() + 1, path.length() - SUFFIX.length());
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(file.getNamespace(), idPath);
            try (Reader reader = e.getValue().openAsReader()) {
                GeoModel model = parse(id, JsonParser.parseReader(reader));
                if (model != null) out.put(id, model);
            } catch (Exception ex) {
                LOGGER.warn("[geo] {}: failed to load ({}), the part will not render", file, ex.toString());
            }
        }
        LOGGER.info("[geo] loaded {} geometry file(s)", out.size());
        return out;
    }

    @Override
    protected void apply(Map<ResourceLocation, GeoModel> loaded, ResourceManager resources, ProfilerFiller profiler) {
        models = Map.copyOf(loaded);
    }

    // ---- parsing --------------------------------------------------------------------------------------------------

    private static final class Cube {
        float[] origin;
        float[] size;
        float u;
        float v;
        float inflate;
        boolean mirror;
    }

    private static final class Bone {
        String name;
        String parent;
        float[] pivot = {0f, 0f, 0f};
        float[] rotation = {0f, 0f, 0f};
        boolean mirror;
        List<Cube> cubes = new ArrayList<>();
        List<Bone> children = new ArrayList<>();
    }

    /** Null (after a warning) if the file is unusable. */
    private static GeoModel parse(ResourceLocation id, JsonElement json) {
        if (!json.isJsonObject()) {
            LOGGER.warn("[geo] {}: not a JSON object", id);
            return null;
        }
        JsonObject root = json.getAsJsonObject();
        JsonArray geos = root.has("minecraft:geometry") && root.get("minecraft:geometry").isJsonArray()
                ? root.getAsJsonArray("minecraft:geometry") : null;
        if (geos == null || geos.isEmpty() || !geos.get(0).isJsonObject()) {
            LOGGER.warn("[geo] {}: missing \"minecraft:geometry\"[0] (only the 1.12.0 style is supported)", id);
            return null;
        }
        JsonObject geo = geos.get(0).getAsJsonObject();
        JsonObject desc = geo.has("description") && geo.get("description").isJsonObject() ? geo.getAsJsonObject("description") : new JsonObject();
        int texW = desc.has("texture_width") ? desc.get("texture_width").getAsInt() : 64;
        int texH = desc.has("texture_height") ? desc.get("texture_height").getAsInt() : 64;
        if (!desc.has("texture_width") || !desc.has("texture_height")) {
            LOGGER.warn("[geo] {}: description has no texture_width/texture_height, assuming 64x64", id);
        }
        if (texW <= 0 || texH <= 0) {
            LOGGER.warn("[geo] {}: bad texture size {}x{}", id, texW, texH);
            return null;
        }
        if (!geo.has("bones") || !geo.get("bones").isJsonArray()) {
            LOGGER.warn("[geo] {}: no \"bones\" array", id);
            return null;
        }

        // 1) read bones
        Map<String, Bone> byName = new LinkedHashMap<>();
        int skippedCubes = 0;
        for (JsonElement be : geo.getAsJsonArray("bones")) {
            if (!be.isJsonObject()) continue;
            JsonObject bj = be.getAsJsonObject();
            if (!bj.has("name")) {
                LOGGER.warn("[geo] {}: a bone has no name, skipped", id);
                continue;
            }
            Bone b = new Bone();
            b.name = bj.get("name").getAsString();
            if (byName.containsKey(b.name)) {
                LOGGER.warn("[geo] {}: duplicate bone name '{}', the later one is skipped", id, b.name);
                continue;
            }
            if (bj.has("parent")) b.parent = bj.get("parent").getAsString();
            if (bj.has("pivot")) b.pivot = vec3(bj.get("pivot"));
            if (bj.has("rotation")) b.rotation = vec3(bj.get("rotation"));
            b.mirror = bj.has("mirror") && bj.get("mirror").getAsBoolean();
            if (bj.has("cubes") && bj.get("cubes").isJsonArray()) {
                for (JsonElement ce : bj.getAsJsonArray("cubes")) {
                    JsonObject cj = ce.getAsJsonObject();
                    if (!cj.has("uv") || !cj.get("uv").isJsonArray()) {
                        LOGGER.warn("[geo] {}: bone '{}' has a cube with per-face UV (unsupported, box UV only), cube skipped", id, b.name);
                        skippedCubes++;
                        continue;
                    }
                    if (cj.has("rotation") || cj.has("pivot")) {
                        LOGGER.warn("[geo] {}: bone '{}' has a cube with its own rotation/pivot (unsupported), the rotation is ignored; use a child bone", id, b.name);
                    }
                    Cube c = new Cube();
                    c.origin = vec3(cj.get("origin"));
                    c.size = vec3(cj.get("size"));
                    JsonArray uv = cj.getAsJsonArray("uv");
                    c.u = uv.get(0).getAsFloat();
                    c.v = uv.get(1).getAsFloat();
                    c.inflate = cj.has("inflate") ? cj.get("inflate").getAsFloat() : 0f;
                    c.mirror = cj.has("mirror") ? cj.get("mirror").getAsBoolean() : b.mirror;
                    b.cubes.add(c);
                }
            }
            byName.put(b.name, b);
        }
        if (byName.isEmpty()) {
            LOGGER.warn("[geo] {}: no usable bones", id);
            return null;
        }

        // 2) link the tree (an unknown parent makes the bone a root; cycles are unreachable from any root)
        List<Bone> roots = new ArrayList<>();
        for (Bone b : byName.values()) {
            Bone parent = b.parent == null ? null : byName.get(b.parent);
            if (b.parent != null && parent == null) {
                LOGGER.warn("[geo] {}: bone '{}' has unknown parent '{}', treated as a root bone", id, b.name, b.parent);
                b.parent = null;
            }
            if (parent == b) b.parent = null; // self-parent: root
            if (parent == null || parent == b) roots.add(b);
            else parent.children.add(b);
        }

        // 3) build the mesh
        MeshDefinition mesh = new MeshDefinition();
        Map<String, List<String>> paths = new LinkedHashMap<>();
        Map<String, String> parents = new HashMap<>();
        for (Bone r : roots) addBone(mesh.getRoot(), r, ANCHOR, new ArrayList<>(), paths, parents);
        if (paths.size() < byName.size()) {
            LOGGER.warn("[geo] {}: {} bone(s) unreachable (parent cycle?) and skipped", id, byName.size() - paths.size());
        }
        ModelPart baked = LayerDefinition.create(mesh, texW, texH).bakeRoot();

        // 4) name -> ModelPart (walk the child chain recorded during the build)
        Map<String, ModelPart> bones = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : paths.entrySet()) {
            ModelPart p = baked;
            for (String seg : e.getValue()) p = p.getChild(seg);
            bones.put(e.getKey(), p);
        }
        if (skippedCubes > 0) LOGGER.warn("[geo] {}: {} cube(s) skipped", id, skippedCubes);
        return new GeoModel(baked, bones, parents);
    }

    /**
     * Recursively adds a bone (and its children) to the mesh.
     * <p>
     * Bedrock: Y up, pivots/origins absolute in pixels with the feet at y=0. Java ModelPart: Y down, a part's origin is its
     * pivot, child poses are relative to the parent's pivot. With P = this bone's pivot and PP = parent pivot (root bones use
     * the ANCHOR (0,24,0)):
     * <pre>
     *   PartPose offset = (P.x - PP.x, -(P.y - PP.y), P.z - PP.z)
     *   cube:  addBox(O.x - P.x, -(O.y + S.y - P.y), O.z - P.z, S.x, S.y, S.z, CubeDeformation(inflate))
     * </pre>
     * (the cube's Y flips because Java's box origin is the corner with the smallest Java-y, i.e. the TOP in Bedrock).
     */
    private static void addBone(PartDefinition parentDef, Bone b, float[] parentPivot, List<String> parentPath,
                                Map<String, List<String>> paths, Map<String, String> parents) {
        CubeListBuilder cubes = CubeListBuilder.create();
        for (Cube c : b.cubes) {
            cubes.texOffs((int) c.u, (int) c.v).mirror(c.mirror).addBox(
                    c.origin[0] - b.pivot[0],
                    -(c.origin[1] + c.size[1] - b.pivot[1]),
                    c.origin[2] - b.pivot[2],
                    c.size[0], c.size[1], c.size[2], new CubeDeformation(c.inflate));
        }
        PartDefinition def = parentDef.addOrReplaceChild(b.name, cubes, poseOf(b, parentPivot));
        List<String> path = new ArrayList<>(parentPath);
        path.add(b.name);
        paths.put(b.name, path);
        if (b.parent != null) parents.put(b.name, b.parent);
        for (Bone child : b.children) addBone(def, child, b.pivot, path, paths, parents);
    }

    private static PartPose poseOf(Bone b, float[] parentPivot) {
        float[] rot = toJavaRotation(b.rotation);
        return PartPose.offsetAndRotation(
                b.pivot[0] - parentPivot[0],
                -(b.pivot[1] - parentPivot[1]),
                b.pivot[2] - parentPivot[2],
                rot[0], rot[1], rot[2]);
    }

    /**
     * THE ONE PLACE the Bedrock -> Java rotation convention lives. Input: Bedrock degrees (x, y, z). Output: Java radians
     * (rotX, rotY, rotZ) = (-rx, -ry, rz). If hair/tails turn out mirrored or tilt the wrong way in game, flip signs here
     * (e.g. also negate z) and nowhere else.
     */
    static float[] toJavaRotation(float[] deg) {
        return new float[]{
                (float) Math.toRadians(-deg[0]),
                (float) Math.toRadians(-deg[1]),
                (float) Math.toRadians(deg[2])};
    }

    private static float[] vec3(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        if (a.size() < 3) throw new IllegalArgumentException("expected a 3-number array, got " + a);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }
}
