package cz.geokuk.core.napoveda;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import cz.geokuk.core.program.FConst;
import cz.geokuk.framework.Dlg;
import cz.geokuk.framework.MySwingWorker0;
import cz.geokuk.util.process.BrowserOpener;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ZkontrolovatAktualizaceSwingWorker extends MySwingWorker0<String, Void> {

	private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");

	private boolean zobrazitDialogPriPosledniVerzi;
	private final NapovedaModel napovedaModel;
	private final boolean betaKanal = Diagnostika.betaKanal();

	public ZkontrolovatAktualizaceSwingWorker(final boolean zobrazitDialogPriPosledniVerzi, final NapovedaModel napovedaModel) {
		this.zobrazitDialogPriPosledniVerzi = zobrazitDialogPriPosledniVerzi;
		this.napovedaModel = napovedaModel;
	}

	/** Ruční kontrola během automatické: výsledek se ukáže i bez nové verze a s nabídkou stabilní verze. */
	void zobrazitDialogPriPosledniVerzi() {
		zobrazitDialogPriPosledniVerzi = true;
	}

	boolean isZobrazitDialogPriPosledniVerzi() {
		return zobrazitDialogPriPosledniVerzi;
	}

	boolean isBetaKanal() {
		return betaKanal;
	}

	/** Beta kanál se během kontroly přepnul, výsledek patří ke starému kanálu. */
	boolean jeZastarala() {
		return betaKanal != Diagnostika.betaKanal();
	}

	/**
	 * Porovná verze po číselných částech, písmena a jiné oddělovače ignoruje.
	 * Testovací verze (s příponou za pomlčkou, např. 6.0.1-beta.2) je starší
	 * než stejná verze bez přípony.
	 */
	static boolean jeNovejsi(final String verze, final String oproti) {
		final String[] a = verze.split("-", 2);
		final String[] b = oproti.split("-", 2);
		final int hlavni = porovnej(cislaVerze(a[0]), cislaVerze(b[0]));
		if (hlavni != 0) {
			return hlavni > 0;
		}
		if (a.length == 1 || b.length == 1) {
			return a.length < b.length;
		}
		return porovnej(cislaVerze(a[1]), cislaVerze(b[1])) > 0;
	}

	private static int porovnej(final int[] a, final int[] b) {
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			final int x = i < a.length ? a[i] : 0;
			final int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return Integer.compare(x, y);
			}
		}
		return 0;
	}

	/**
	 * Nabídne novější verzi. Při ruční kontrole nabídne testovací verzi bez beta kanálu i přechod na starší
	 * stabilní verzi.
	 */
	static boolean nabidnout(final String posledni, final String pouzivana, final boolean betaKanal, final boolean rucne) {
		return jeNovejsi(posledni, pouzivana) || rucne && jePrechodNaStabilni(posledni, pouzivana, betaKanal);
	}

	static boolean jePrechodNaStabilni(final String posledni, final String pouzivana, final boolean betaKanal) {
		return !betaKanal && pouzivana.contains("-") && !posledni.contains("-") && !jeNovejsi(posledni, pouzivana);
	}

	static String nejnovejsiVerze(final String json) {
		String nejnovejsi = null;
		final Matcher matcher = TAG_NAME.matcher(json);
		while (matcher.find()) {
			if (nejnovejsi == null || jeNovejsi(matcher.group(1), nejnovejsi)) {
				nejnovejsi = matcher.group(1);
			}
		}
		return nejnovejsi;
	}

	private static int[] cislaVerze(final String verze) {
		return Arrays.stream(verze.split("\\D+")).filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).toArray();
	}

	@Override
	protected String doInBackground() throws Exception {
		try {
			final URLConnection connection = new URL(betaKanal ? FConst.RELEASES_API_URL : FConst.LATEST_RELEASE_API_URL).openConnection();
			connection.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			connection.setConnectTimeout(60000);
			connection.setReadTimeout(60000);
			final String json;
			try (Scanner sc = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
				json = sc.hasNext() ? sc.next() : "";
			}
			final String lastVersion = nejnovejsiVerze(json);
			log.info("Posledni verze: '" + lastVersion + "' ");
			Diagnostika.zaznamenej("Kontrola aktualizací: poslední verze " + lastVersion + (betaKanal ? " (beta kanál)" : ""));
			return lastVersion;
		} catch (final IOException e) {
			log.error("An error has occurred while retrieving the info!", e);
			Diagnostika.zaznamenej("Kontrola aktualizací selhala: " + e);
			return null;
		}
	}

	@Override
	protected void donex() throws Exception {
		try {
			ukazVysledek();
		} finally {
			napovedaModel.kontrolaSkoncila(this);
		}
	}

	private void ukazVysledek() throws Exception {
		final String lastVersion = get();
		if (jeZastarala()) {
			log.info("Kontrola aktualizací pro jiný beta kanál, výsledek se neukáže: " + lastVersion);
		} else if (FConst.I_AM_IN_DEVELOPMENT_ENVIRONMENT) {
			log.info("LAST VERSION: " + lastVersion + " i have no version, i am in development environment");
		} else if (lastVersion == null) {
			if (zobrazitDialogPriPosledniVerzi) {
				Dlg.info("Nepodařilo se zjistit poslední verzi programu Geokuk.", "Oznámení");
			}
		} else if (!zobrazitDialogPriPosledniVerzi && napovedaModel.jeOdlozena(lastVersion)) {
			log.info("Nabídka verze " + lastVersion + " je odložená");
		} else if (!nabidnout(lastVersion, FConst.VERSION, betaKanal, zobrazitDialogPriPosledniVerzi)) {
			if (zobrazitDialogPriPosledniVerzi) {
				Dlg.info("Používaná verze programu Geokuk " + FConst.VERSION + " je poslední distribuovanou verzí.", "Oznámení");
			}
		} else {
			final boolean prechod = jePrechodNaStabilni(lastVersion, FConst.VERSION, betaKanal);
			final Object[] options = prechod
					? new Object[] { "Zobrazit web", "Přejít na stabilní verzi", "Zůstat u testovací verze" }
					: new Object[] { "Zobrazit web", "Stáhnout novou verzi", "Připomenout za týden" };
			final String text = prechod
					? "<html>Používáte testovací verzi <b>" + FConst.VERSION + "</b>.<br>Poslední stabilní verze je <b>" + lastVersion + "</b>."
					: "<html>Používaná verze programu Geokuk je <b>" + FConst.VERSION + "</b>.<br>Nová verze je <b>" + lastVersion + "</b>.";
			final int n = JOptionPane.showOptionDialog(Dlg.parentFrame(), text, prechod ? "Přechod na stabilní verzi" : "Nová verze programu",
					JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
			switch (n) {
			case 0:
				zobrazitWeb();
				break;
			case 1:
				if (StahnoutAktualizaciSwingWorker.lzeInstalovat()) {
					new StahnoutAktualizaciSwingWorker(lastVersion).execute();
				} else {
					stahnoutJar(lastVersion);
				}
				break;
			default:
				if (!prechod) {
					napovedaModel.odlozKontroluAktualizaci(lastVersion);
				}
				break;
			}
		}
	}

	private void stahnoutJar(final String verze) {
		try {
			// Beta kanál nabízí i testovací verze, které na stránce „latest“ nejsou.
			BrowserOpener.displayURL(new URL(FConst.RELEASE_TAG_URL + verze));
		} catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

	private void zobrazitWeb() {
		try {
			BrowserOpener.displayURL(new URL(FConst.WEB_PAGE_URL));
		} catch (final MalformedURLException e) {
			throw new RuntimeException(e);
		}
	}

}
