package cz.geokuk.plugins.mapy.kachle.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import javax.swing.KeyStroke;

import cz.geokuk.core.program.FConst;
import lombok.extern.slf4j.Slf4j;

/**
 * Mapy, které si uživatel zadá v souboru vedle geokuk.jar. Zobrazují se
 * vedle vestavěných pod vlastními jmény, vestavěné nemění.
 */
@Slf4j
public final class UzivatelskeMapy {

	public static final String SOUBOR = "uzivatelske-mapy.properties";
	static final String PREFIX = "user-";
	private static final List<String> VLASTNOSTI = Arrays.asList("nazev", "url", "popis", "min", "max", "maxauto", "klavesa", "zkratka", "atribuce", "hromadne");
	private static final String HLAVICKA = "hlavicka.";
	private static final int MAX_MERITKO = 22;

	/** Načte mapy ze souboru vedle jaru a vrátí popis chyb v něm. */
	public static List<String> nacti() {
		return FConst.JAR_DIR_EXISTUJE ? nacti(new File(FConst.JAR_DIR, SOUBOR)) : Collections.emptyList();
	}

	static List<String> nacti(final File soubor) {
		EKaType.setUzivatelske(Collections.emptyList());
		if (!soubor.isFile()) {
			return Collections.emptyList();
		}
		final List<String> chyby = new ArrayList<>();
		try (Reader reader = Files.newBufferedReader(soubor.toPath(), StandardCharsets.UTF_8)) {
			final Properties p = new Properties();
			p.load(reader);
			EKaType.setUzivatelske(zpracuj(p, chyby));
		} catch (final IOException | IllegalArgumentException e) {
			chyby.add("Soubor nelze přečíst: " + e.getMessage());
		}
		if (!chyby.isEmpty()) {
			log.warn("Chyby v {}: {}", soubor, chyby);
		}
		return chyby;
	}

	static List<EKaType> zpracuj(final Properties p, final List<String> chyby) {
		final SortedMap<String, Map<String, String>> mapy = new TreeMap<>();
		for (final String klic : p.stringPropertyNames()) {
			final int tecka = klic.indexOf('.');
			final String id = tecka < 0 ? klic : klic.substring(0, tecka);
			final String vlastnost = tecka < 0 ? "" : klic.substring(tecka + 1);
			if (!id.matches("[a-z0-9][a-z0-9-]*")) {
				chyby.add(klic + ": označení mapy smí obsahovat jen malá písmena bez diakritiky, číslice a pomlčky");
			} else if (!VLASTNOSTI.contains(vlastnost) && !vlastnost.matches(HLAVICKA.replace(".", "\\.") + "[A-Za-z0-9-]+")) {
				chyby.add(klic + ": neznámá vlastnost, povolené jsou " + VLASTNOSTI + " a " + HLAVICKA + "<jméno hlavičky>");
			} else {
				mapy.computeIfAbsent(id, k -> new HashMap<>()).put(vlastnost, p.getProperty(klic).trim());
			}
		}
		final List<EKaType> vysledek = new ArrayList<>();
		for (final Map.Entry<String, Map<String, String>> e : mapy.entrySet()) {
			final EKaType mapa = vytvor(e.getKey(), e.getValue(), vysledek, chyby);
			if (mapa != null) {
				vysledek.add(mapa);
			}
		}
		return vysledek;
	}

	private static EKaType vytvor(final String id, final Map<String, String> v, final List<EKaType> predchozi, final List<String> chyby) {
		final String nazev = v.getOrDefault("nazev", "");
		final String url = v.getOrDefault("url", "");
		if (nazev.isEmpty()) {
			chyby.add(id + ".nazev chybí");
			return null;
		}
		if (!url.matches("https?://\\S+") || !url.contains("{z}") || !url.contains("{x}") || !url.contains("{y}")) {
			chyby.add(id + ".url musí začínat http:// nebo https:// a obsahovat {z}, {x} a {y}");
			return null;
		}
		final int min;
		final int max;
		final int maxauto;
		try {
			min = Integer.parseInt(v.getOrDefault("min", "0"));
			max = Integer.parseInt(v.getOrDefault("max", "18"));
			maxauto = Integer.parseInt(v.getOrDefault("maxauto", String.valueOf(max)));
		} catch (final NumberFormatException e) {
			chyby.add(id + ": min, max a maxauto musí být celá čísla");
			return null;
		}
		if (min < 0 || max > MAX_MERITKO || min > max || maxauto < min || maxauto > max) {
			chyby.add(id + ": měřítka musí splňovat 0 ≤ min ≤ maxauto ≤ max ≤ " + MAX_MERITKO);
			return null;
		}
		final String klavesa = v.getOrDefault("klavesa", "");
		if (!klavesa.isEmpty() && !klavesa.matches("[A-Za-z0-9]")) {
			chyby.add(id + ".klavesa musí být jedno písmeno nebo číslice");
			return null;
		}
		final String zkratka = v.getOrDefault("zkratka", "");
		final KeyStroke keyStroke = zkratka.isEmpty() ? null : zkratka.length() == 1 ? KeyStroke.getKeyStroke(zkratka.charAt(0)) : KeyStroke.getKeyStroke(zkratka);
		if (!zkratka.isEmpty() && keyStroke == null) {
			chyby.add(id + ".zkratka není platná klávesová zkratka (např. u, F5, ctrl U)");
			return null;
		}
		final List<EKaType> jine = new ArrayList<>(EKaType.vestavene());
		jine.addAll(predchozi);
		for (final EKaType jina : jine) {
			if (keyStroke != null && keyStroke.equals(jina.getKeyStroke())) {
				chyby.add(id + ".zkratka " + zkratka + " už používá mapa " + jina.getNazev());
				return null;
			}
		}
		final String hromadne = v.getOrDefault("hromadne", "ne");
		if (!hromadne.equals("ano") && !hromadne.equals("ne")) {
			chyby.add(id + ".hromadne musí být ano, nebo ne");
			return null;
		}
		final Map<String, String> hlavicky = new TreeMap<>();
		v.forEach((vlastnost, hodnota) -> {
			if (vlastnost.startsWith(HLAVICKA)) {
				hlavicky.put(vlastnost.substring(HLAVICKA.length()), hodnota.replace("{verze}", FConst.VERSION));
			}
		});
		return EKaType.uzivatelska(id, nazev, v.getOrDefault("popis", nazev), min, max, maxauto, klavesa.isEmpty() ? 0 : Character.toUpperCase(klavesa.charAt(0)), keyStroke, hlavicky,
				v.getOrDefault("atribuce", ""), hromadne.equals("ano"),
				new UzivatelskyUrlBuilder(url));
	}

	private UzivatelskeMapy() {}
}
