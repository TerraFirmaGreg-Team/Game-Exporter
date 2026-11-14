package team.terrafirmagreg.exporter.modern.model;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import team.terrafirmagreg.exporter.contract.text.DumpText;
import team.terrafirmagreg.exporter.modern.text.DumpTexts;
import team.terrafirmagreg.exporter.modern.texture.CaptureScissor;
import team.terrafirmagreg.exporter.util.ExportLog;

@UtilityClass
public class SceneCapture {

    private static final Pattern PROGRESS_TEXT = Pattern.compile("compil", Pattern.CASE_INSENSITIVE);
    private static final ExportLog.WarnCap ENTITY_WARN = ExportLog.warnCapped(25);
    private static final Set<String> BAKE_LOGGED = new HashSet<>();
    private static final float ENTITY_BAKE_SPAN = 20f;

    private static void bakeLog(String id, String fmt, Object... args) {
        if (BAKE_LOGGED.add(id + fmt)) {
            Object[] merged = new Object[args.length + 1];
            merged[0] = id;
            System.arraycopy(args, 0, merged, 1, args.length);
            ExportLog.warn("entity {} " + fmt, merged);
        }
    }

    public record Rect(int x, int y, int w, int h) {

        public static Rect union(Rect a, Rect b) {
            if (a == null) {
                return b;
            }
            if (b == null) {
                return a;
            }
            int x = Math.min(a.x(), b.x());
            int y = Math.min(a.y(), b.y());
            int r = Math.max(a.x() + a.w(), b.x() + b.w());
            int btm = Math.max(a.y() + a.h(), b.y() + b.h());
            return new Rect(x, y, r - x, btm - y);
        }
    }

    public record Result(
            Map<BlockPos, BlockState> blocks,
            List<DrawnItem> items,
            List<DrawnEntity> entities,
            List<DrawnText> texts,
            Rect bounds,
            BlockAndTintGetter level) {
        public boolean isEmpty() {
            return blocks.isEmpty() && items.isEmpty() && entities.isEmpty();
        }

        public boolean hasScene() {
            return !blocks.isEmpty() || !entities.isEmpty();
        }

        public boolean hasProgressText() {
            return texts.stream().anyMatch(text -> progressText(text.payload()));
        }
    }

    public record DrawnItem(ItemStack stack, int x, int y) {}

    public record DrawnEntity(String id, int x, int y, int w, int h, Rect clip, boolean rendered) {}

    public record DrawnText(
            int x, int y, int w, int h, DumpText payload, int color, boolean shadow, FormattedCharSequence seq) {}

    private record DrawnKey(int x, int y, Item item, CompoundTag tag) {}

    private record DrawnTextKey(int x, int y, int color, DumpText payload) {}

    private static final Object LOCK = new Object();
    private static Map<BlockAndTintGetter, Map<BlockPos, BlockState>> blocks;
    private static List<DrawnItem> items;
    private static List<DrawnEntity> entities;
    private static List<Entity> entityObjects;
    private static List<DrawnText> texts;
    private static Rect bounds;
    private static int widgetX;
    private static int widgetY;
    private static int widgetW;
    private static int widgetH;
    private static boolean widgetActive;
    private static boolean widgetTextCovered;
    private static int widgetScissorDepth;
    private static Deque<List<CaptureScissor.Entry>> suspendedScissors;
    private static int bakeDepth;
    private static int originX;
    private static int originY;
    private static boolean suppressed;
    private static int decorations;

    public static void begin(int guiOriginX, int guiOriginY) {
        begin(guiOriginX, guiOriginY, false);
    }

    public static void begin(int guiOriginX, int guiOriginY, boolean suppressPixels) {
        synchronized (LOCK) {
            blocks = new IdentityHashMap<>();
            items = new ArrayList<>();
            entities = new ArrayList<>();
            entityObjects = new ArrayList<>();
            texts = new ArrayList<>();
            bounds = null;
            widgetActive = false;
            widgetTextCovered = false;
            originX = guiOriginX;
            originY = guiOriginY;
            suppressed = suppressPixels;
            decorations = 0;
            widgetScissorDepth = 0;
            suspendedScissors = null;
            bakeDepth = 0;
        }
    }

    public static boolean suppressed() {
        return suppressed;
    }

    public static boolean suppressEntityDraw(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return false;
        }
        synchronized (LOCK) {
            return bakeDepth == 0 && widgetActive;
        }
    }

    public static void decorationScope() {
        synchronized (LOCK) {
            decorations += 1;
        }
    }

    public static void decorationScopeEnd() {
        synchronized (LOCK) {
            decorations = Math.max(0, decorations - 1);
        }
    }

    public static boolean insideDecorations() {
        return decorations > 0;
    }

    public static void widgetScope(int x, int y, int w, int h) {
        widgetScope(x, y, w, h, false);
    }

    public static void widgetScope(int x, int y, int w, int h, boolean textCovered) {
        synchronized (LOCK) {
            widgetX = x;
            widgetY = y;
            widgetW = w;
            widgetH = h;
            widgetActive = true;
            widgetTextCovered = textCovered;
            widgetScissorDepth = CaptureScissor.active() ? CaptureScissor.depth() : 0;
        }
    }

    public static void widgetScopeClear() {
        synchronized (LOCK) {
            widgetActive = false;
            widgetTextCovered = false;
            restoreSuspended();
        }
    }

    private static void restoreSuspended() {
        if (suspendedScissors == null) {
            return;
        }
        while (!suspendedScissors.isEmpty()) {
            CaptureScissor.restoreSuspended(suspendedScissors.pop());
        }
    }

    private static void markScene() {
        if (widgetActive && widgetW > 0 && widgetH > 0) {
            bounds = Rect.union(bounds, new Rect(widgetX - originX, widgetY - originY, widgetW, widgetH));
        }
    }

    public static void record(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        if (level == null || pos == null || state == null || state.isAir()) {
            return;
        }
        if (level == Minecraft.getInstance().level) {
            return;
        }
        synchronized (LOCK) {
            if (blocks == null) {
                return;
            }
            blocks.computeIfAbsent(level, ignored -> new LinkedHashMap<>()).put(pos.immutable(), state);
            markScene();
        }
    }

    public static void recordItem(ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        synchronized (LOCK) {
            if (items == null) {
                return;
            }
            items.add(new DrawnItem(stack.copy(), x - originX, y - originY));
        }
    }

    public static boolean progressText(DumpText payload) {
        return payload != null
                && PROGRESS_TEXT.matcher(DumpTexts.plain(payload)).find();
    }

    public static void recordText(
            DumpText payload, FormattedCharSequence seq, int x, int y, int w, int h, int color, boolean shadow) {
        if (payload == null) {
            return;
        }
        synchronized (LOCK) {
            if (texts == null || !suppressed || widgetTextCovered) {
                return;
            }
            texts.add(new DrawnText(x - originX, y - originY, w, h, payload, color, shadow, seq));
        }
    }

    public static void recordEntity(Entity entity) {
        recordEntity(entity, null);
    }

    public static void recordEntity(Entity entity, PoseStack pose) {
        if (entity == null) {
            return;
        }
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return;
        }
        Rect drawRect = entityDrawRect(entity, pose);
        synchronized (LOCK) {
            if (entities == null) {
                return;
            }
            entity.noCulling = true;
            markScene();
            Rect rect = widgetActive ? new Rect(widgetX - originX, widgetY - originY, widgetW, widgetH) : null;
            Rect clip = drawRect != null ? drawRect : currentClip();
            if (clip != null && rect != null && !overlaps(clip, rect)) {
                clip = null;
            }
            String id = key.toString();
            boolean suppressedNow = bakeDepth == 0 && entity instanceof LivingEntity;
            if (widgetActive && !suppressedNow) {
                if (suspendedScissors == null) {
                    suspendedScissors = new ArrayDeque<>();
                }
                suspendedScissors.push(
                        CaptureScissor.active() ? CaptureScissor.suspendAbove(widgetScissorDepth) : List.of());
            }
            for (int i = entities.size() - 1; i >= 0; i--) {
                DrawnEntity drawn = entities.get(i);
                if (!drawn.id().equals(id) || !drawn.rendered()) {
                    continue;
                }
                entities.set(
                        i,
                        new DrawnEntity(
                                drawn.id(),
                                drawn.x(),
                                drawn.y(),
                                drawn.w(),
                                drawn.h(),
                                clip != null ? clip : drawn.clip(),
                                false));
                entityObjects.set(i, entity);
                return;
            }
            entities.add(
                    rect == null
                            ? new DrawnEntity(id, 0, 0, 0, 0, clip, false)
                            : new DrawnEntity(id, rect.x(), rect.y(), rect.w(), rect.h(), clip, false));
            entityObjects.add(entity);
        }
    }

    private static Rect entityDrawRect(Entity entity, PoseStack pose) {
        if (pose == null || !CaptureScissor.active() || CaptureScissor.pixelsPerGui() <= 0) {
            return null;
        }
        try {
            Matrix4f mv = new Matrix4f(RenderSystem.getModelViewMatrix())
                    .mul(pose.last().pose());
            Matrix4f mvp = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(mv);
            Vector4f point = mvp.transform(new Vector4f(0, 0, 0, 1));
            if (point.w() == 0) {
                return null;
            }
            double ppg = CaptureScissor.pixelsPerGui();
            int fbW = CaptureScissor.framebufferWidth();
            int fbH = CaptureScissor.framebufferHeight();
            if (fbW <= 0 || fbH <= 0) {
                return null;
            }
            float feetX = (point.x() / point.w() + 1f) / 2f * fbW;
            float feetY = (1f - point.y() / point.w()) / 2f * fbH;
            float scale = Math.abs(pose.last().pose().m00());
            float wPx = Math.max(1f, entity.getBbWidth() * scale * (float) ppg);
            float hPx = Math.max(1f, entity.getBbHeight() * scale * (float) ppg);
            int x = Math.round((feetX - wPx / 2f) / (float) ppg) - originX;
            int y = Math.round((feetY - hPx) / (float) ppg) - originY;
            int w = Math.max(1, Math.round(wPx / (float) ppg));
            int h = Math.max(1, Math.round(hPx / (float) ppg));
            return new Rect(x, y, w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    public static void recordEntityRendered(Entity entity) {
        if (entity == null) {
            return;
        }
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return;
        }
        synchronized (LOCK) {
            if (entities == null) {
                return;
            }
            entity.noCulling = false;
            if (suspendedScissors != null && !suspendedScissors.isEmpty()) {
                CaptureScissor.restoreSuspended(suspendedScissors.pop());
            }
            String id = key.toString();
            for (int i = entities.size() - 1; i >= 0; i--) {
                DrawnEntity drawn = entities.get(i);
                if (drawn.id().equals(id) && !drawn.rendered()) {
                    entities.set(
                            i,
                            new DrawnEntity(
                                    drawn.id(), drawn.x(), drawn.y(), drawn.w(), drawn.h(), drawn.clip(), true));
                    return;
                }
            }
        }
    }

    public static void recordEntityFailed(Entity entity, Throwable throwable) {
        if (entity == null || throwable == null) {
            return;
        }
        var key = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (key == null) {
            return;
        }
        entity.noCulling = false;
        synchronized (LOCK) {
            if (suspendedScissors != null && !suspendedScissors.isEmpty()) {
                CaptureScissor.restoreSuspended(suspendedScissors.pop());
            }
        }
        bakeLog(key.toString(), "capture render failed: {}", throwable);
    }

    private static Rect currentClip() {
        float[] gui = CaptureScissor.currentGui();
        if (gui == null || gui[2] - gui[0] <= 0 || gui[3] - gui[1] <= 0) {
            return null;
        }
        int x = Math.round(gui[0]) - originX;
        int y = Math.round(gui[1]) - originY;
        return new Rect(x, y, Math.round(gui[2]) - Math.round(gui[0]), Math.round(gui[3]) - Math.round(gui[1]));
    }

    private static boolean overlaps(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    public static void bakeUnrendered(GuiGraphics graphics, int margin) {
        List<DrawnEntity> snapshot;
        List<Entity> sources;
        synchronized (LOCK) {
            if (entities == null || entityObjects == null) {
                return;
            }
            snapshot = List.copyOf(entities);
            sources = List.copyOf(entityObjects);
        }
        boolean flushed = false;
        for (int i = 0; i < snapshot.size(); i++) {
            DrawnEntity drawn = snapshot.get(i);
            if (drawn.rendered() || drawn.w() <= 0 || drawn.h() <= 0) {
                bakeLog(drawn.id(), "bake skip rendered={} rect={}x{}", drawn.rendered(), drawn.w(), drawn.h());
                continue;
            }
            if (!flushed) {
                graphics.flush();
                flushed = true;
            }
            Entity entity = i < sources.size() ? sources.get(i) : null;
            if (!(entity instanceof LivingEntity living)) {
                bakeLog(drawn.id(), "bake skip not-living obj={}", entity);
                continue;
            }
            Rect frame = new Rect(drawn.x(), drawn.y(), drawn.w(), drawn.h());
            if (frame.w() <= 0 || frame.h() <= 0) {
                continue;
            }
            Rect spot = drawn.clip() != null && overlaps(drawn.clip(), frame) ? drawn.clip() : frame;
            float fit = Math.min(
                    ENTITY_BAKE_SPAN / Math.max(0.1f, entity.getBbWidth()),
                    ENTITY_BAKE_SPAN / Math.max(0.1f, entity.getBbHeight()));
            int scale = Math.max(1, Math.min(64, Math.round(fit)));
            int cx = Math.max(
                    frame.x() - margin + 2,
                    Math.min(frame.x() - margin + frame.w() - 2, spot.x() - margin + spot.w() / 2));
            int feet = Math.max(
                    frame.y() - margin + 2,
                    Math.min(frame.y() - margin + frame.h() - 2, spot.y() - margin + spot.h() - 1));
            try {
                bakeDepth++;
                CaptureScissor.pushGui(
                        frame.x() + originX,
                        frame.y() + originY,
                        frame.x() + originX + frame.w(),
                        frame.y() + originY + frame.h());
                try {
                    InventoryScreen.renderEntityInInventoryFollowsMouse(
                            graphics, cx, feet, scale, cx, feet - frame.h() * 0.35f, living);
                    bakeLog(drawn.id(), "bake drawn at {},{} scale={}", cx, feet, scale);
                } finally {
                    CaptureScissor.pop();
                    bakeDepth--;
                }
            } catch (Throwable t) {
                bakeLog(drawn.id(), "capture bake failed: {}", t.toString());
            }
        }
    }

    private static DrawnEntity mergeEntity(DrawnEntity a, DrawnEntity b) {
        DrawnEntity sized = a.w() > 0 && a.h() > 0 ? a : b;
        DrawnEntity other = sized == a ? b : a;
        if (sized.w() <= 0 || sized.h() <= 0) {
            sized = other;
        }
        Rect clip = b.clip() != null ? b.clip() : a.clip();
        boolean rendered = a.rendered() || b.rendered();
        return new DrawnEntity(sized.id(), sized.x(), sized.y(), sized.w(), sized.h(), clip, rendered);
    }

    public static Result merge(Result prev, Result next) {
        if (prev == null) {
            return next;
        }
        if (next == null) {
            return prev;
        }
        Map<BlockPos, BlockState> blocks = new LinkedHashMap<>(prev.blocks());
        blocks.putAll(next.blocks());
        List<DrawnItem> items = next.items().isEmpty() ? prev.items() : next.items();
        Map<String, DrawnEntity> entities = new LinkedHashMap<>();
        for (DrawnEntity entity : prev.entities()) {
            entities.merge(entity.id(), entity, SceneCapture::mergeEntity);
        }
        for (DrawnEntity entity : next.entities()) {
            entities.merge(entity.id(), entity, SceneCapture::mergeEntity);
        }
        Map<DrawnTextKey, DrawnText> texts = new LinkedHashMap<>();
        for (DrawnText text : prev.texts()) {
            texts.putIfAbsent(new DrawnTextKey(text.x(), text.y(), text.color(), text.payload()), text);
        }
        for (DrawnText text : next.texts()) {
            texts.putIfAbsent(new DrawnTextKey(text.x(), text.y(), text.color(), text.payload()), text);
        }
        return new Result(
                blocks,
                items,
                entities.isEmpty() ? List.of() : List.copyOf(entities.values()),
                texts.isEmpty() ? List.of() : List.copyOf(texts.values()),
                Rect.union(prev.bounds(), next.bounds()),
                prev.level() != null ? prev.level() : next.level());
    }

    public static Result end() {
        Map<BlockAndTintGetter, Map<BlockPos, BlockState>> captured;
        List<DrawnItem> drawn;
        List<DrawnEntity> drawnEntities;
        List<DrawnText> drawnTexts;
        Rect region;
        synchronized (LOCK) {
            captured = blocks;
            drawn = items;
            drawnEntities = entities;
            drawnTexts = texts;
            region = bounds;
            blocks = null;
            items = null;
            entities = null;
            entityObjects = null;
            texts = null;
            bounds = null;
            widgetActive = false;
            widgetTextCovered = false;
            suppressed = false;
            decorations = 0;
            suspendedScissors = null;
            Map<BlockPos, BlockState> mergedBlocks = new LinkedHashMap<>();
            BlockAndTintGetter sceneLevel = null;
            if (captured != null) {
                List<Map.Entry<BlockAndTintGetter, Map<BlockPos, BlockState>>> ordered =
                        new ArrayList<>(captured.entrySet());
                ordered.sort((a, b) ->
                        Integer.compare(b.getValue().size(), a.getValue().size()));
                for (Map.Entry<BlockAndTintGetter, Map<BlockPos, BlockState>> entry : ordered) {
                    if (sceneLevel == null) {
                        sceneLevel = entry.getKey();
                    }
                    for (Map.Entry<BlockPos, BlockState> block :
                            entry.getValue().entrySet()) {
                        mergedBlocks.putIfAbsent(block.getKey(), block.getValue());
                    }
                }
            }
            Map<DrawnKey, DrawnItem> unique = new LinkedHashMap<>();
            if (drawn != null) {
                for (DrawnItem item : drawn) {
                    unique.putIfAbsent(
                            new DrawnKey(
                                    item.x(),
                                    item.y(),
                                    item.stack().getItem(),
                                    item.stack().getTag()),
                            item);
                }
            }
            Map<String, DrawnEntity> uniqueEntities = new LinkedHashMap<>();
            if (drawnEntities != null) {
                for (DrawnEntity entity : drawnEntities) {
                    uniqueEntities.merge(entity.id(), entity, SceneCapture::mergeEntity);
                }
            }
            Map<DrawnTextKey, List<DrawnText>> textGroups = new LinkedHashMap<>();
            List<DrawnText> uniqueTexts = new ArrayList<>();
            if (drawnTexts != null) {
                for (DrawnText text : drawnTexts) {
                    textGroups
                            .computeIfAbsent(
                                    new DrawnTextKey(0, text.y(), text.color(), text.payload()),
                                    key -> new ArrayList<>())
                            .add(text);
                }
                for (List<DrawnText> group : textGroups.values()) {
                    if (group.size() >= 3) {
                        uniqueTexts.add(group.getLast());
                    } else {
                        uniqueTexts.addAll(group);
                    }
                }
            }
            return new Result(
                    Collections.unmodifiableMap(mergedBlocks),
                    unique.isEmpty() ? List.of() : List.copyOf(unique.values()),
                    uniqueEntities.isEmpty() ? List.of() : List.copyOf(uniqueEntities.values()),
                    uniqueTexts.isEmpty() ? List.of() : List.copyOf(uniqueTexts),
                    region,
                    sceneLevel);
        }
    }
}
