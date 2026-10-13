package cz.geokuk.plugins.cesty.data;

import java.util.ArrayList;
import java.util.List;

import cz.geokuk.core.coordinates.*;
import cz.geokuk.plugins.cesty.routovani.Trasa;

public class Updator {

	public void odeberBod(final Bod bod) {
		bod.remove();
	}

	public void pipojitCestuPred(final Cesta cesta1, final Cesta cesta2) {
		cesta1.pripojitPred(cesta2);
	}

	public void pipojitCestuZa(final Cesta cesta1, final Cesta cesta2) {
		assert cesta1.getDoc() == cesta2.getDoc();
		assert cesta1.getStart() != null;
		assert cesta1.getCil() != null;
		cesta1.pripojitZa(cesta2);
		assert cesta1.getStart() != null;
		assert cesta1.getCil() != null;
	}

	public void pospojujVzdusneUseky(final Cesta cesta) {
		cesta.pospojujVzdusneUseky();
	}

	public Bod pridejNaKonec(final Cesta cesta, final Mouable mouable) {
		cesta.setChanged();
		return cesta.pridejNaKonec(mouable);
	}

	public Bod pridejNaMisto(final Cesta cesta, final Mouable mouable) {
		cesta.setChanged();
		return cesta.pridejNaMisto(mouable);
	}

	public void remove(final Cesta cesta) {
		cesta.remove();
	}

	public Usek removeBod(final Bod bod) {
		bod.setChanged();
		return bod.remove();
	}

	public void reverse(final Cesta cesta) {
		cesta.reverse();
	}

	public Cesta rozdelCestuVBode(final Bod bod) {
		bod.setChanged();
		return bod.rozdelCestu();
	}

	public Cesta rozdelCestuVUseku(final Usek usek, final Mou mou) {
		usek.setChanged();
		final Bod bod = usek.rozdelAZanikni(mou);
		return bod.rozdelCestu();
	}

	public Bod rozdelUsekNaDvaNove(final Usek usek, final Mou mouNovy) {
		usek.setChanged();
		return usek.rozdelAZanikni(mouNovy);
	}

	public void setMouable(final Bod bod, final Mouable mouable) {
		bod.setChanged();
		bod.setMouable(mouable);
	}

	public void setMouableButNoChange(final Bod bod, final Mouable mouable) {
		bod.setMouable(mouable);
	}

	public void setNazev(final Cesta cesta, final String aTrackName) {
		cesta.setNazev(aTrackName);
	}

	public void setVzdusny(final Usek usek, final boolean vzdusny) {
		usek.setChanged();
		usek.setVzdusny(vzdusny);
	}

	public void setTyp(final Usek usek, final TypUseku typ) {
		if (usek.getTyp() == typ) {
			return;
		}
		usek.setChanged();
		usek.setTyp(typ);
	}

	/**
	 * Uloží výsledek hledání trasy mezi body {@code od} a {@code kam} do úseku po cestě. Když se mezitím krajní body úseku posunuly, nebo úsek už po cestě nevede,
	 * nic neudělá.
	 *
	 * @return zda se výsledek uložil
	 */
	public boolean setTrasa(final Usek usek, final Mou od, final Mou kam, final Trasa trasa) {
		if (!usek.isPoCeste() || trasa.getStav() == Trasa.Stav.ZRUSENO || usek.getBvzad() == null || !od.equals(usek.getBvzad().getMou())
				|| !kam.equals(usek.getBvpred().getMou())) {
			return false;
		}
		List<Mou> body = null;
		if (trasa.isNalezena()) {
			body = new ArrayList<>(trasa.getBody().size());
			for (final Wgs w : trasa.getBody()) {
				body.add(w.toMou());
			}
			// Krajní body trasy jsou přesně body úseku, převod přes Wgs je může o kousek posunout.
			body.set(0, od);
			body.set(body.size() - 1, kam);
		}
		usek.setChanged();
		usek.setTrasa(od, kam, trasa.getStav(), body);
		return true;
	}

	/** Trasa úseku po cestě načtená ze souboru: body od {@link Usek#getBvzad()} k {@link Usek#getBvpred()} včetně krajních. */
	public void setNactenaTrasa(final Usek usek, final List<Mou> body) {
		usek.setTyp(TypUseku.PO_CESTE);
		usek.setTrasa(usek.getBvzad().getMou(), usek.getBvpred().getMou(), Trasa.Stav.NALEZENO, body);
	}

	/** Zapomene výsledek hledání trasy, takže se úsek po cestě spočítá znovu. */
	public void zapomenTrasu(final Usek usek) {
		usek.setTrasa(null, null, null, null);
	}

	public void smazatUsekAOtevritNeboRozdelitCestu(final Usek usek) {
		final Cesta cesta = usek.getCesta();
		final boolean jeKruh = cesta.isKruh();
		final Cesta druhaPulka = usek.getBvzad().rozdelCestu();
		druhaPulka.getStart().remove();
		if (jeKruh) {
			cesta.pripojitPred(druhaPulka);
		}
	}

	public void xadd(final Doc doc, final Cesta cesta) {
		doc.setChanged();
		doc.xadd(cesta);
	}

}
