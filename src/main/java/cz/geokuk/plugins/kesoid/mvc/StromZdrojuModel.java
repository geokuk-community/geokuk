package cz.geokuk.plugins.kesoid.mvc;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import javax.swing.table.AbstractTableModel;

import cz.geokuk.plugins.kesoid.importek.InformaceOZdroji;
import cz.geokuk.util.file.KeFile;
import cz.geokuk.util.lang.FString;

/** Strom zdrojů jako tabulka: řádky jsou viditelné uzly pod skrytým kořenem, rozbalené uzly si pamatuje podle souboru. */
class StromZdrojuModel extends AbstractTableModel {

	private static final long serialVersionUID = 1L;

	static final class Radek {
		final InformaceOZdroji uzel;
		final int hloubka;

		Radek(final InformaceOZdroji uzel, final int hloubka) {
			this.uzel = uzel;
			this.hloubka = hloubka;
		}

		boolean maDeti() {
			return !uzel.getChildren().isEmpty();
		}
	}

	private static final String[] NAZVY = { "Zdroj", "Načíst", "WP braných", "WP celkem" };
	private static final Class<?>[] TRIDY = { String.class, Boolean.class, Integer.class, Integer.class };

	private final Predicate<KeFile> maSeNacist;
	private final Predicate<KeFile> jeZamcena;
	private final BiConsumer<KeFile, Boolean> nastavNacitani;
	private final Set<KeFile> rozbalene = new HashSet<>();
	private final List<Radek> radky = new ArrayList<>();
	private InformaceOZdroji koren;

	StromZdrojuModel(final Predicate<KeFile> maSeNacist, final Predicate<KeFile> jeZamcena, final BiConsumer<KeFile, Boolean> nastavNacitani) {
		this.maSeNacist = maSeNacist;
		this.jeZamcena = jeZamcena;
		this.nastavNacitani = nastavNacitani;
	}

	void setKoren(final InformaceOZdroji koren) {
		this.koren = koren;
		obnov();
	}

	Radek getRadek(final int radek) {
		return radky.get(radek);
	}

	boolean jeRozbaleny(final int radek) {
		return rozbalene.contains(radky.get(radek).uzel.jmenoZdroje);
	}

	void setRozbaleny(final int radek, final boolean rozbalit) {
		final Radek r = radky.get(radek);
		if (r.maDeti() && (rozbalit ? rozbalene.add(r.uzel.jmenoZdroje) : rozbalene.remove(r.uzel.jmenoZdroje))) {
			obnov();
		}
	}

	private void obnov() {
		radky.clear();
		if (koren != null) {
			pridej(koren, 0);
		}
		fireTableDataChanged();
	}

	private void pridej(final InformaceOZdroji rodic, final int hloubka) {
		for (final InformaceOZdroji dite : rodic.getChildren()) {
			radky.add(new Radek(dite, hloubka));
			if (rozbalene.contains(dite.jmenoZdroje)) {
				pridej(dite, hloubka + 1);
			}
		}
	}

	@Override
	public int getRowCount() {
		return radky.size();
	}

	@Override
	public int getColumnCount() {
		return NAZVY.length;
	}

	@Override
	public String getColumnName(final int sloupec) {
		return NAZVY[sloupec];
	}

	@Override
	public Class<?> getColumnClass(final int sloupec) {
		return TRIDY[sloupec];
	}

	@Override
	public boolean isCellEditable(final int radek, final int sloupec) {
		return sloupec == 1;
	}

	@Override
	public Object getValueAt(final int radek, final int sloupec) {
		final InformaceOZdroji ioz = radky.get(radek).uzel;
		switch (sloupec) {
		case 0:
			return FString.text(ioz.getDisplayName() + (jeZamcena.test(ioz.jmenoZdroje) ? " – čeká na dokončení zápisu" : ""));
		case 1:
			return maSeNacist.test(ioz.jmenoZdroje);
		case 2:
			return ioz.getPocetWaypointuBranychSDetmi();
		default:
			return ioz.getPocetWaypointuCelkemSDetmi();
		}
	}

	@Override
	public void setValueAt(final Object hodnota, final int radek, final int sloupec) {
		nastavNacitani.accept(radky.get(radek).uzel.jmenoZdroje, (Boolean) hodnota);
		// Volba u složky mění i soubory pod ní.
		fireTableRowsUpdated(0, radky.size() - 1);
	}
}
