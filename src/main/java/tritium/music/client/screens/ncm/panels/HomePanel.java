package tritium.music.client.screens.ncm.panels;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import tritium.music.client.render.RenderContext;
import tritium.music.client.rendering.animation.Interpolations;
import tritium.music.client.rendering.font.CFontRenderer;
import tritium.music.client.rendering.font.FontManager;
import tritium.music.client.rendering.ui.AbstractWidget;
import tritium.music.client.rendering.ui.container.ScrollPanel;
import tritium.music.client.rendering.ui.widgets.LabelWidget;
import tritium.music.client.rendering.ui.widgets.RoundedImageWidget;
import tritium.music.client.screens.ncm.NCMPanel;
import tritium.music.client.screens.ncm.NCMScreen;
import tritium.music.core.model.PlayList;
import tritium.music.core.ncm.api.CloudMusicApi;
import tritium.music.core.util.AsyncUtil;
import tritium.music.core.util.JsonUtils;
import tritium.music.core.util.Textures;
import tritium.music.platform.Platform;
import tritium.music.platform.TextureHandle;

import java.util.ArrayList;

public class HomePanel extends NCMPanel {

    public HomePanel() {
        super();
    }

    static final ArrayList<PlayList> playLists = new ArrayList<>();

    @Override
    public void onInit() {

        if (!playLists.isEmpty()) {
            layout();
        } else {
            AsyncUtil.runAsync(() -> {
                JsonObject jObj = CloudMusicApi.recommendResource().toJsonObject();

                if (!jObj.isJsonObject()) {
                    return;
                }

                JsonElement codeElement = jObj.get("code");

                if (codeElement == null || !codeElement.isJsonPrimitive() || codeElement.getAsDouble() != 200) {
                    return;
                }

                JsonArray recommend = jObj.getAsJsonArray("recommend");

                for (JsonElement element : recommend) {
                    if (!element.isJsonObject()) {
                        continue;
                    }

                    JsonObject playList = element.getAsJsonObject();
                    playLists.add(JsonUtils.parse(playList, PlayList.class));
                }

                layout();
            });
        }
    }

    public ScrollPanel scrollPanel;

    private void layout() {
        LabelWidget lblWelcome = new LabelWidget("欢迎来到 Tritium Music!", FontManager.pf25bold);

        this.addChild(lblWelcome);

        int margin = 12;

        lblWelcome.setBeforeRenderCallback(() -> lblWelcome
                .setPosition(margin, margin)
                .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT)));

        LabelWidget lblRecommendations = new LabelWidget("推荐歌单", FontManager.pf14bold);

        this.addChild(lblRecommendations);

        lblRecommendations.setBeforeRenderCallback(() -> lblRecommendations
                .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT))
                .setPosition(margin, lblWelcome.getRelativeY() + lblWelcome.getHeight() + 2 + margin * .5 - lblRecommendations.getHeight() * .5));

        scrollPanel = new ScrollPanel();

        this.addChild(scrollPanel);

        scrollPanel.setSpacing(margin);

        scrollPanel
                .setAlignment(ScrollPanel.Alignment.VERTICAL_WITH_HORIZONTAL_FILL)
                .setBeforeRenderCallback(() -> scrollPanel
                        .setMargin(margin)
                        .setBounds(
                                scrollPanel.getRelativeX(),
                                scrollPanel.getRelativeY() + lblWelcome.getHeight() + margin,
                                scrollPanel.getWidth(),
                                scrollPanel.getHeight() - lblWelcome.getHeight() - margin
                        ));

        long revealStart = System.currentTimeMillis();
        for (int i = 0; i < playLists.size(); i++) {
            scrollPanel.addChild(new PlaylistWidget(playLists.get(i), i, revealStart).setShouldOverrideMouseCursor(true));
        }
    }

    private static class PlaylistWidget extends AbstractWidget<PlaylistWidget> {

        @Getter
        private final PlayList playList;

        double emphasizeAnim = 0;

        boolean coverLoaded = false;

        private final int index;
        private final long revealStart;
        private boolean entranceDone = false;

        private static final long ENTRANCE_STAGGER_MS = 35;
        private static final long ENTRANCE_DURATION_MS = 450;
        private static final int ENTRANCE_INDEX_CAP = 10;
        private static final double ENTRANCE_SLIDE = 14;

        public PlaylistWidget(PlayList playList, int index, long revealStart) {
            this.playList = playList;
            this.index = index;
            this.revealStart = revealStart;

            this.setTransformations(() -> {
                float ep = this.entranceProgress();
                RenderContext.graphics().pose().translate(0, (1f - ep) * (float) ENTRANCE_SLIDE);
            });

            this.setBeforeRenderCallback(() -> {
                if (!entranceDone) {
                    float ep = this.entranceProgress();
                    if (ep >= 1f) {
                        entranceDone = true;
                        this.setAlpha(1f);
                        this.setTransformations(null);
                    } else {
                        this.setAlpha(ep);
                    }
                }
            });

            double size = 100;
            double emphasizeAnimMax = 5;

            this.setBounds(size + emphasizeAnimMax, size + emphasizeAnimMax + 16);

            RoundedImageWidget cover = new RoundedImageWidget(this::getCoverLocation, 0, 0, size, size);

            this.addChild(cover);

            cover
                    .setClickable(false)
                    .fadeIn()
                    .setLinearFilter(true)
                    .setBeforeRenderCallback(() -> {

                        if (!coverLoaded) {
                            coverLoaded = true;
                            this.loadCover();
                        }

                        this.emphasizeAnim = Interpolations.interpolate(this.emphasizeAnim, cover.isHovering() ? emphasizeAnimMax : 0, .2f);

                        cover
                                .setBounds(size + this.emphasizeAnim)
                                .setRadius(4)
                                .centerHorizontally()
                                .setBounds(cover.getRelativeX(), this.getWidth() * .5 - size * .5 - emphasizeAnim * .5, cover.getWidth(), cover.getHeight());
                    });

            CFontRenderer pf14bold = FontManager.pf14bold;
            LabelWidget lblName = new LabelWidget(() -> String.join("\n", pf14bold.fitWidth(playList.getName(), size)), pf14bold);

            this.addChild(lblName);

            lblName
                    .setClickable(false)
                    .setBeforeRenderCallback(() -> lblName
                            .setColor(NCMScreen.getColor(NCMScreen.ColorType.PRIMARY_TEXT))
                            .setPosition(cover.getRelativeX(), cover.getRelativeY() + cover.getHeight() + 4));

            this.setOnClickCallback((relativeX, relativeY, mouseButton) -> {
                if (mouseButton == 0) {
                    NCMScreen.getInstance().setCurrentPanel(new PlaylistPanel(playList));
                }
                return true;
            });
        }

        @Override
        public void onRender(double mouseX, double mouseY) {
        }

        private float entranceProgress() {
            long delay = (long) (Math.min(index, ENTRANCE_INDEX_CAP) * ENTRANCE_STAGGER_MS);
            long elapsed = System.currentTimeMillis() - revealStart - delay;

            if (elapsed <= 0L) {
                return 0f;
            }
            if (elapsed >= ENTRANCE_DURATION_MS) {
                return 1f;
            }

            float t = elapsed / (float) ENTRANCE_DURATION_MS;
            float inv = 1f - t;
            return 1f - inv * inv * inv;
        }

        private void loadCover() {
            TextureHandle coverLoc = this.getCoverLocation();
            if (Platform.hasTexture(coverLoc))
                return;

            Textures.downloadTextureAndLoadAsync(playList.getCoverUrl() + "?param=256y256", coverLoc);
        }

        private TextureHandle getCoverLocation() {
            return TextureHandle.of("textures/playlist/" + this.playList.getId() + "/cover.png");
        }
    }
}
