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
public class ZkontrolovatAktualizaceSwingWorker extends MySwingWorker0<ZpravyAVerze, Void> {

	private static final Pattern TAG_NAME = Pattern.compile("\"tag_name\"\\s*:\\s*\"v?([^\"]+)\"");

	private final boolean zobrazitDialogPriPosledniVerzi;
	private final NapovedaModel napovedaModel;

	public ZkontrolovatAktualizaceSwingWorker(final boolean zobrazitDialogPriPosledniVerzi, final NapovedaModel napovedaModel) {
		this.zobrazitDialogPriPosledniVerzi = zobrazitDialogPriPosledniVerzi;
		this.napovedaModel = napovedaModel;
	}

	/**
	 * Porovná verze po číselných částech, písmena a jiné oddělovače ignoruje.
	 */
	static boolean jeNovejsi(final String verze, final String oproti) {
		final int[] a = cislaVerze(verze);
		final int[] b = cislaVerze(oproti);
		for (int i = 0; i < Math.max(a.length, b.length); i++) {
			final int x = i < a.length ? a[i] : 0;
			final int y = i < b.length ? b[i] : 0;
			if (x != y) {
				return x > y;
			}
		}
		return false;
	}

	private static int[] cislaVerze(final String verze) {
		return Arrays.stream(verze.split("\\D+")).filter(s -> !s.isEmpty()).mapToInt(Integer::parseInt).toArray();
	}

	@Override
	protected ZpravyAVerze doInBackground() throws Exception {
		try {
			final URLConnection connection = new URL(FConst.LATEST_RELEASE_API_URL).openConnection();
			connection.setRequestProperty("User-Agent", "Geokuk/" + FConst.VERSION + " (" + FConst.WEB_PAGE_URL + ")");
			connection.setRequestProperty("Accept", "application/vnd.github+json");
			connection.setConnectTimeout(60000);
			connection.setReadTimeout(60000);
			final String json;
			try (Scanner sc = new Scanner(connection.getInputStream(), "UTF-8").useDelimiter("\\A")) {
				json = sc.hasNext() ? sc.next() : "";
			}
			final Matcher matcher = TAG_NAME.matcher(json);
			final String lastVersion = matcher.find() ? matcher.group(1) : null;
			log.info("Posledni verze: '" + lastVersion + "' ");
			return new ZpravyAVerze(Collections.<ZpravaUzivateli> emptyList(), lastVersion);
		} catch (final IOException e) {
			log.error("An error has occurred while retrieving the info!", e);
			return new ZpravyAVerze(Collections.<ZpravaUzivateli> emptyList(), null);
		}
	}

	@Override
	protected void donex() throws Exception {
		final ZpravyAVerze vysledek = get();
		if (FConst.I_AM_IN_DEVELOPMENT_ENVIRONMENT) {
			log.info("LAST VERSION: " + vysledek.lastVersion + " i have no version, i am in development environment");
		} else if (vysledek.lastVersion == null) {
			if (zobrazitDialogPriPosledniVerzi) {
				Dlg.info("Nepodařilo se zjistit poslední verzi programu Geokuk.", "Oznámení");
			}
		} else if (!jeNovejsi(vysledek.lastVersion, FConst.VERSION)) {
			if (zobrazitDialogPriPosledniVerzi) {
				Dlg.info("Používaná verze programu Geokuk " + FConst.VERSION + " je poslední distribuovanou verzí.", "Oznámení");
			}
		} else {
			final Object[] options = { "Zobrazit web", "Stáhnout nejnovější verzi", "Připomenout za měsíc" };
			final int n = JOptionPane.showOptionDialog(Dlg.parentFrame(),
					"<html></b>Používaná verze programu Geokuk <b>" + FConst.VERSION + "</b> " + "není poslední distribuovanou verzí. Poslední distribuovaná verze je " + vysledek.lastVersion + ".",
					"Spuštění nové verze", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[2]);
			switch (n) {
			case 0:
				zobrazitWeb();
				break;
			case 1:
				stahnoutJar();
				break;
			default:
				napovedaModel.odlozKontroluAktualizaci(30L);
				break;
			}
		}
		napovedaModel.setZpravyUzivatelum(vysledek.zpravy);

		super.donex();
	}

	private void stahnoutJar() {
		try {
			BrowserOpener.displayURL(new URL(FConst.LATEST_RELEASE_URL));
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
