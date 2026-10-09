package cz.geokuk.core.render;

public class DlazdicovaMetrika {
	static final int dlaMezera = -10;
	final int dlaPocet;
	final int dlaRoztec;
	final int dlaSize;
	final int maxDlazdice;
	final int sizeCele;

	DlazdicovaMetrika(final int maxDlazdice, final int sizeCele) {
		this.maxDlazdice = maxDlazdice;
		this.sizeCele = sizeCele;
		dlaPocet = sizeCele / (maxDlazdice + dlaMezera) + 1;
		// Zaokrouhlení nahoru, ať poslední dlaždice sahá až k okraji obrázku.
		dlaRoztec = (sizeCele + dlaMezera + dlaPocet - 1) / dlaPocet;
		dlaSize = dlaRoztec - dlaMezera;

	}
}
