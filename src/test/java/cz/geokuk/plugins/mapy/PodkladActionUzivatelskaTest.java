package cz.geokuk.plugins.mapy;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import javax.swing.Action;
import javax.swing.KeyStroke;

import org.junit.*;
import org.junit.rules.TemporaryFolder;

import cz.geokuk.plugins.mapy.kachle.data.*;

/** Položka menu uživatelské mapy přebírá název, nápovědu, klávesu i zkratku. */
public class PodkladActionUzivatelskaTest {

	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	@After
	public void uklid() {
		UzivatelskeMapyPristup.vycisti();
	}

	@Test
	public void polozkaMenu() throws Exception {
		final File soubor = new File(tmp.getRoot(), "m" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), "nazev=Moje mapa\nurl=https://t.example.org/{z}/{x}/{y}\npopis=Nápověda\nklavesa=j\nzkratka=F5\n".getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());
		final EKaType mapa = EKaType.podleJmena("user-m");
		final PodkladAction akce = new PodkladAction(mapa);
		Assert.assertEquals("Moje mapa", akce.getValue(Action.NAME));
		Assert.assertEquals("Nápověda", akce.getValue(Action.SHORT_DESCRIPTION));
		Assert.assertEquals((int) 'J', akce.getValue(Action.MNEMONIC_KEY));
		Assert.assertEquals(KeyStroke.getKeyStroke("F5"), akce.getValue(Action.ACCELERATOR_KEY));
		Assert.assertSame(mapa, akce.getPodklad());
	}

	@Test
	public void htmlVNazvuAPopisuSeZobraziJakoText() throws Exception {
		Files.write(new File(tmp.getRoot(), "h" + UzivatelskeMapy.PRIPONA).toPath(),
				"nazev=<html><img src=http://127.0.0.1/n>\nurl=https://t.example.org/{z}/{x}/{y}\npopis=<html><img src=http://127.0.0.1/p>\n".getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());
		final PodkladAction akce = new PodkladAction(EKaType.podleJmena("user-h"));
		Assert.assertEquals("<html>&lt;html&gt;&lt;img src=http://127.0.0.1/n&gt;", akce.getValue(Action.NAME));
		Assert.assertEquals("<html>&lt;html&gt;&lt;img src=http://127.0.0.1/p&gt;", akce.getValue(Action.SHORT_DESCRIPTION));
	}

	@Test
	public void bezKlavesyAZkratky() throws Exception {
		final File soubor = new File(tmp.getRoot(), "m" + UzivatelskeMapy.PRIPONA);
		Files.write(soubor.toPath(), "nazev=M\nurl=https://t.example.org/{z}/{x}/{y}\n".getBytes(StandardCharsets.UTF_8));
		UzivatelskeMapyPristup.nacti(tmp.getRoot());
		final PodkladAction akce = new PodkladAction(EKaType.podleJmena("user-m"));
		Assert.assertNull(akce.getValue(Action.MNEMONIC_KEY));
		Assert.assertNull(akce.getValue(Action.ACCELERATOR_KEY));
	}
}
