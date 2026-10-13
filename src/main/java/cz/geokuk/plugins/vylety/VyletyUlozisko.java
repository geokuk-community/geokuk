package cz.geokuk.plugins.vylety;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import cz.geokuk.util.file.BezpecnyZapis;

/**
 * Pojmenované výlety v souborech. Aktivní výlet je vždy v lovim.ggt a tedne.ggt, ostatní (i aktivní, zrcadlený) v
 * {@code ulozene/<id>/} vedle lovim.ggt; seznam a aktivní výlet jsou v {@code vylety.txt}. Bez {@code vylety.txt} existuje jediný výlet
 * „Výchozí“ a nic dalšího na disku není.
 */
final class VyletyUlozisko {

	static final String VYCHOZI_ID = "v1";
	static final String VYCHOZI_JMENO = "Výchozí";
	static final int NEJVETSI_DELKA_JMENA = 60;

	private static final String KLIC_AKTIVNI = "aktivni";
	private static final String KLIC_PORADI = "poradi";
	private static final String PREFIX_JMENO = "jmeno.";

	private static final class Index {
		String aktivni = VYCHOZI_ID;
		final Map<String, String> jmena = new LinkedHashMap<>();
		boolean zDisku;

		Index() {
			jmena.put(VYCHOZI_ID, VYCHOZI_JMENO);
		}
	}

	private final File ano;
	private final File ne;
	private final File slozka;
	private final File indexSoubor;
	private final File zurnal;
	private final File ulozene;

	VyletyUlozisko(final File ano, final File ne) {
		this.ano = ano;
		this.ne = ne;
		slozka = ano.getAbsoluteFile().getParentFile();
		indexSoubor = new File(slozka, "vylety.txt");
		zurnal = new File(slozka, "aktivace.txt");
		ulozene = new File(slozka, "ulozene");
	}

	/** Dokončí aktivaci přerušenou uprostřed (pád programu). */
	void zotav() throws IOException {
		if (!zurnal.isFile()) {
			return;
		}
		final List<String> radky = Files.readAllLines(zurnal.toPath(), StandardCharsets.UTF_8);
		final String id = radky.isEmpty() ? "" : radky.get(0).trim();
		final Index index = nactiIndex();
		if (index.zDisku && index.jmena.containsKey(id) && slozkaVyletu(id).isDirectory()) {
			kopiruj(new File(slozkaVyletu(id), ano.getName()), ano);
			kopiruj(new File(slozkaVyletu(id), ne.getName()), ne);
			index.aktivni = id;
			zapisIndex(index);
		}
		Files.delete(zurnal.toPath());
	}

	List<VyletInfo> seznam() throws IOException {
		final Index index = nactiIndex();
		final List<VyletInfo> seznam = new ArrayList<>();
		for (final Map.Entry<String, String> e : index.jmena.entrySet()) {
			final boolean aktivni = e.getKey().equals(index.aktivni);
			final File a = aktivni ? ano : new File(slozkaVyletu(e.getKey()), ano.getName());
			final File n = aktivni ? ne : new File(slozkaVyletu(e.getKey()), ne.getName());
			seznam.add(new VyletInfo(e.getKey(), e.getValue(), aktivni, pocetKodu(a), pocetKodu(n)));
		}
		return seznam;
	}

	String aktivniId() throws IOException {
		return nactiIndex().aktivni;
	}

	/** Změnu aktivního výletu zapíše i do jeho uložené kopie. */
	void zrcadli(final List<String> kodyAno, final List<String> kodyNe) throws IOException {
		final Index index = nactiIndex();
		if (!index.zDisku) {
			return;
		}
		final File slozkaAktivniho = slozkaVyletu(index.aktivni);
		zapisKody(kodyAno, new File(slozkaAktivniho, ano.getName()));
		zapisKody(kodyNe, new File(slozkaAktivniho, ne.getName()));
	}

	/** Vytvoří prázdný výlet a vrátí jeho id. */
	String novy(final String jmeno) throws IOException {
		final Index index = zajistiIndex();
		overJmeno(index, jmeno, null);
		int nejvetsi = 0;
		for (final String id : index.jmena.keySet()) {
			nejvetsi = Math.max(nejvetsi, Integer.parseInt(id.substring(1)));
		}
		final String id = "v" + (nejvetsi + 1);
		zapisKody(Collections.<String>emptyList(), new File(slozkaVyletu(id), ano.getName()));
		zapisKody(Collections.<String>emptyList(), new File(slozkaVyletu(id), ne.getName()));
		index.jmena.put(id, jmeno.trim());
		zapisIndex(index);
		return id;
	}

	void aktivuj(final String id) throws IOException {
		final Index index = nactiIndex();
		if (!index.jmena.containsKey(id)) {
			throw new IllegalArgumentException("Výlet " + id + " neexistuje.");
		}
		if (id.equals(index.aktivni)) {
			return;
		}
		final File cil = slozkaVyletu(id);
		final File cilAno = new File(cil, ano.getName());
		final File cilNe = new File(cil, ne.getName());
		if (!cilAno.isFile() || !cilNe.isFile()) {
			throw new IOException("Výlet " + index.jmena.get(id) + " nemá uložené soubory ve složce " + cil);
		}
		// Změna lovim.ggt provedená zvenku se tak neztratí s odcházejícím výletem.
		final File puvodni = slozkaVyletu(index.aktivni);
		kopiruj(ano, new File(puvodni, ano.getName()));
		kopiruj(ne, new File(puvodni, ne.getName()));
		BezpecnyZapis.zapisText(zurnal, StandardCharsets.UTF_8, out -> out.println(id));
		kopiruj(cilAno, ano);
		kopiruj(cilNe, ne);
		index.aktivni = id;
		zapisIndex(index);
		Files.delete(zurnal.toPath());
	}

	void prejmenuj(final String id, final String jmeno) throws IOException {
		final Index index = zajistiIndex();
		if (!index.jmena.containsKey(id)) {
			throw new IllegalArgumentException("Výlet " + id + " neexistuje.");
		}
		overJmeno(index, jmeno, id);
		index.jmena.put(id, jmeno.trim());
		zapisIndex(index);
	}

	/** Smaže výlet; smazání aktivního nejdřív aktivuje Výchozí. Výchozí výlet smazat nejde. */
	void smaz(final String id) throws IOException {
		if (VYCHOZI_ID.equals(id)) {
			throw new IllegalArgumentException("Výchozí výlet nejde smazat.");
		}
		Index index = nactiIndex();
		if (!index.jmena.containsKey(id)) {
			throw new IllegalArgumentException("Výlet " + id + " neexistuje.");
		}
		if (id.equals(index.aktivni)) {
			aktivuj(VYCHOZI_ID);
			index = nactiIndex();
		}
		index.jmena.remove(id);
		zapisIndex(index);
		smazSlozku(slozkaVyletu(id));
	}

	/** Text chyby jména, nebo null, když je jméno v pořádku. {@code id} je výlet, který se přejmenovává (jeho jméno nevadí). */
	String validujJmeno(final String jmeno, final String id) throws IOException {
		return chybaJmena(nactiIndex(), jmeno, id);
	}

	private static String chybaJmena(final Index index, final String jmeno, final String id) {
		final String j = jmeno == null ? "" : jmeno.trim();
		if (j.isEmpty()) {
			return "Jméno výletu nesmí být prázdné.";
		}
		if (j.length() > NEJVETSI_DELKA_JMENA) {
			return "Jméno výletu může mít nejvýš " + NEJVETSI_DELKA_JMENA + " znaků.";
		}
		for (int i = 0; i < j.length(); i++) {
			if (Character.isISOControl(j.charAt(i))) {
				return "Jméno výletu nesmí obsahovat řídicí znaky.";
			}
		}
		for (final Map.Entry<String, String> e : index.jmena.entrySet()) {
			if (!e.getKey().equals(id) && e.getValue().toLowerCase(Locale.ROOT).equals(j.toLowerCase(Locale.ROOT))) {
				return "Výlet se jménem „" + e.getValue() + "“ už existuje.";
			}
		}
		return null;
	}

	private static void overJmeno(final Index index, final String jmeno, final String id) {
		final String chyba = chybaJmena(index, jmeno, id);
		if (chyba != null) {
			throw new IllegalArgumentException(chyba);
		}
	}

	/** Index se na disku zakládá, až když je potřeba; stávající lovim.ggt a tedne.ggt se stanou výletem Výchozí. */
	private Index zajistiIndex() throws IOException {
		final Index index = nactiIndex();
		if (!index.zDisku) {
			final File vychozi = slozkaVyletu(VYCHOZI_ID);
			kopiruj(ano, new File(vychozi, ano.getName()));
			kopiruj(ne, new File(vychozi, ne.getName()));
			zapisIndex(index);
			index.zDisku = true;
		}
		return index;
	}

	private File slozkaVyletu(final String id) {
		return new File(ulozene, id);
	}

	private Index nactiIndex() throws IOException {
		final Index index = new Index();
		if (!indexSoubor.isFile()) {
			return index;
		}
		final Properties p = new Properties();
		try (Reader r = new InputStreamReader(new FileInputStream(indexSoubor), StandardCharsets.UTF_8)) {
			p.load(r);
		} catch (final IllegalArgumentException e) {
			return index; // poškozený index = jediný výlet Výchozí
		}
		final String poradi = p.getProperty(KLIC_PORADI, "");
		final Map<String, String> jmena = new LinkedHashMap<>();
		for (final String id : poradi.split(",")) {
			final String jmeno = p.getProperty(PREFIX_JMENO + id.trim());
			if (id.trim().matches("v\\d{1,9}") && jmeno != null && !jmeno.trim().isEmpty()) {
				jmena.put(id.trim(), jmeno.trim());
			}
		}
		final String aktivni = p.getProperty(KLIC_AKTIVNI, "").trim();
		if (!jmena.containsKey(VYCHOZI_ID) || !jmena.containsKey(aktivni)) {
			return index; // poškozený index = jediný výlet Výchozí
		}
		index.jmena.clear();
		index.jmena.putAll(jmena);
		index.aktivni = aktivni;
		index.zDisku = true;
		return index;
	}

	private void zapisIndex(final Index index) throws IOException {
		final Properties p = new Properties();
		p.setProperty(KLIC_AKTIVNI, index.aktivni);
		final StringBuilder poradi = new StringBuilder();
		for (final Map.Entry<String, String> e : index.jmena.entrySet()) {
			poradi.append(poradi.length() == 0 ? "" : ",").append(e.getKey());
			p.setProperty(PREFIX_JMENO + e.getKey(), e.getValue());
		}
		p.setProperty(KLIC_PORADI, poradi.toString());
		BezpecnyZapis.zapisText(indexSoubor, StandardCharsets.UTF_8, out -> p.store(out, null));
	}

	private static int pocetKodu(final File soubor) throws IOException {
		if (!soubor.isFile()) {
			return 0;
		}
		final Set<String> kody = new HashSet<>();
		for (final String radek : Files.readAllLines(soubor.toPath(), StandardCharsets.UTF_8)) {
			if (!radek.trim().isEmpty()) {
				kody.add(radek.trim());
			}
		}
		return kody.size();
	}

	private static void kopiruj(final File zdroj, final File cil) throws IOException {
		final byte[] obsah = zdroj.isFile() ? Files.readAllBytes(zdroj.toPath()) : new byte[0];
		BezpecnyZapis.zapis(cil, out -> out.write(obsah));
	}

	private static void zapisKody(final List<String> kody, final File soubor) throws IOException {
		BezpecnyZapis.zapisText(soubor, StandardCharsets.UTF_8, out -> {
			for (final String kod : kody) {
				out.print(kod);
				out.print(cz.geokuk.core.program.FConst.NL);
			}
		});
	}

	private static void smazSlozku(final File slozka) throws IOException {
		final File[] soubory = slozka.listFiles();
		if (soubory != null) {
			for (final File f : soubory) {
				Files.delete(f.toPath());
			}
		}
		Files.deleteIfExists(slozka.toPath());
	}
}
