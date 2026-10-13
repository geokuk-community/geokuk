package cz.geokuk.plugins.mapy.kachle.gui;

import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.*;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.framework.Event0;
import cz.geokuk.framework.Prefe;
import cz.geokuk.plugins.mapy.KachleUmisteniSouboru;
import cz.geokuk.plugins.mapy.kachle.KachleModel;
import cz.geokuk.plugins.mapy.kachle.podklady.KachleZiskavac;
import cz.geokuk.util.file.Filex;

/** Menu Téma offline mapy nečte zipy ve složce na EDT: ukáže, co má načtené, a témata ze složky doplní vlákno na pozadí. */
public class JTemaOfflineMapyMenuTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static List<String> polozky(final JMenu menu) {
		final List<String> texty = new ArrayList<>();
		for (final Component c : menu.getMenuComponents()) {
			if (c instanceof JMenuItem) {
				texty.add(((JMenuItem) c).getText());
			}
		}
		return texty;
	}

	@Test(timeout = 20000)
	public void temataZeSlozkyNaPozadi() throws Exception {
		final File slozka = tmp.newFolder("offline-mapy");
		try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(new File(slozka, "paws_test.zip")))) {
			zip.putNextEntry(new ZipEntry("a.xml"));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("b.xml"));
			zip.closeEntry();
		}
		final KachleModel model = new KachleModel() {
			@Override
			public void fire(final Event0<?> event) {
				// bez správce událostí
			}
		};
		model.inject(new Prefe());
		model.inject(new KachleZiskavac());
		final KachleUmisteniSouboru u = new KachleUmisteniSouboru();
		u.setKachleCacheDir(new Filex(tmp.newFolder("cache"), false, true));
		u.setOfflineMapyDir(new Filex(slozka, false, true));
		model.setUmisteniSouboru(u);

		final JTemaOfflineMapyMenu[] menu = new JTemaOfflineMapyMenu[1];
		SwingUtilities.invokeAndWait(() -> {
			menu[0] = new JTemaOfflineMapyMenu(model);
			menu[0].sestav();
			Assert.assertTrue("zipy se na EDT neotvírají", polozky(menu[0]).contains("Načítám témata ze složky…"));
			menu[0].nactiTemataNaPozadi();
		});
		while (menu[0].getTemataZeSlozky() == null) {
			Thread.sleep(20);
		}
		SwingUtilities.invokeAndWait(() -> {
			menu[0].sestav();
			final List<String> texty = polozky(menu[0]);
			Assert.assertTrue(texty.toString(), texty.contains("paws_test.zip – a.xml"));
			Assert.assertTrue(texty.contains("paws_test.zip – b.xml"));
			Assert.assertFalse(texty.contains("Načítám témata ze složky…"));
		});
	}
}
