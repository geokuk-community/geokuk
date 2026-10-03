package cz.geokuk.plugins.kesoid.importek;

import static com.google.common.truth.Truth.assertThat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import cz.geokuk.framework.ProgressModel;
import cz.geokuk.framework.Progressor;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.kind.KesoidPluginManager;
import cz.geokuk.plugins.kesoid.mvc.GccomNick;
import cz.geokuk.util.file.*;

public class KesoidImportBuilderPrubehTest {

	/** Pamatuje si nejvyšší průběh každého pruhu podle jeho textu. */
	private static final class ZaznamProgresu extends ProgressModel {
		final Map<String, Integer> nejvyssi = new HashMap<>();

		@Override
		public Progressor start(final int max, final String text) {
			nejvyssi.put(text, 0);
			return new Progressor() {
				int progress;

				@Override
				public void setProgress(final int p) {
					progress = p;
					nejvyssi.merge(text, p, Math::max);
				}

				@Override
				public void addProgress(final int p) {
					setProgress(progress + p);
				}

				@Override
				public void incProgress() {
					addProgress(1);
				}

				@Override
				public int getProgress() {
					return progress;
				}

				@Override
				public void finish() {}

				@Override
				public void setMax(final int m) {}

				@Override
				public void setText(final String t) {}

				@Override
				public void setTooltip(final String t) {}
			};
		}
	}

	@Test
	public void pruhIndexovaniSePosouvaBehemIndexovani() throws Exception {
		final StringBuilder wpt = new StringBuilder();
		for (int i = 0; i < 2500; i++) {
			wpt.append(ImportKesiTest.kes("GC" + Integer.toString(10000 + i, 36).toUpperCase(), "Geocache", "Traditional Cache", "Kačer", 7, true, false, "1.5", ""));
		}
		final ZaznamProgresu progress = new ZaznamProgresu();
		progress.inject(udalost -> {});
		final KesoidImportBuilder builder = new KesoidImportBuilder(new Genom(), new GccomNick("Ja", 42), progress, new KesoidPluginManager());
		builder.init();
		final File adresar = new File("data");
		builder.setCurrentlyLoading(new KeFile(new FileAndTime(new File(adresar, "test.gpx"), 0), new Root(adresar, new Root.Def(0, null, null))), true);
		new NacitacGpx().nacti(new ByteArrayInputStream(ImportKesiTest.gpx(wpt.toString()).getBytes(StandardCharsets.UTF_8)), "test.gpx", builder, null);
		builder.done();
		assertThat(builder.getKesBag().getKesoidy()).hasSize(2500);
		assertThat(progress.nejvyssi.get("Indexování")).isAtLeast(2000);
	}
}
