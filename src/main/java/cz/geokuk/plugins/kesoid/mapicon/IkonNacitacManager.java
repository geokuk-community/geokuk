package cz.geokuk.plugins.kesoid.mapicon;

import java.util.Objects;

import javax.swing.Timer;

import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.plugins.kesoid.mvc.KesoidUmisteniSouboru;
import cz.geokuk.util.file.Filex;

/** Spouští načítání ikon; volá se jen z EDT. Běží nejvýš jedno načítání, požadavek během něj se nezahodí, ale načte se po něm. */
public class IkonNacitacManager {

	private final IkonNacitacLoader ikonNacitacLoader;
	private IkonNacitacSwingWorker sw;
	private final KesoidModel kesoidModel;

	private Filex lastThirdParty;
	private Filex lastMyOwn;

	/** Načítání běží, dokud na EDT neproběhne {@link #poNacteni(IkonBag)}; {@code isDone()} workeru platí už dřív. */
	private boolean nacita;
	/** Během běžícího načítání přišel požadavek (třeba jiné zvýraznění nebo sada ikon); načte se, až běžící doběhne. */
	private boolean cekaDalsi;

	public IkonNacitacManager(final KesoidModel kesoidModel) {
		this(kesoidModel, new IkonNacitacLoader());
		new Timer(10000, e -> startLoad(false)).start();
	}

	/** Pro testy: bez časovače a s vlastním načítáním. */
	IkonNacitacManager(final KesoidModel kesoidModel, final IkonNacitacLoader ikonNacitacLoader) {
		this.kesoidModel = kesoidModel;
		this.ikonNacitacLoader = ikonNacitacLoader;
	}

	public void startLoad(final boolean prenacti) {
		final KesoidUmisteniSouboru umisteniSouboru = kesoidModel.getUmisteniSouboru();
		if (umisteniSouboru == null) {
			return; // ikony se načtou, až se umístění nastaví (KesoidModel.setUmisteniSouboru)
		}
		final Filex thirdParty = umisteniSouboru.getImage3rdPartyDir();
		final Filex myDir = umisteniSouboru.getImageMyDir();

		if (prenacti || !Objects.equals(lastThirdParty, thirdParty) || !Objects.equals(lastMyOwn, myDir)) {
			lastMyOwn = myDir;
			lastThirdParty = thirdParty;
			if (nacita) {
				// Běžící načítání má staré zvýraznění, sadu nebo složky; po něm se načte znovu (#824).
				cekaDalsi = true;
				return;
			}
			spust(prenacti);
		}
	}

	private void spust(final boolean prenacti) {
		// Složky se loaderu mění, jen když nic nenačítá; běžící načítání je čte z jiného vlákna.
		ikonNacitacLoader.setImage3rdPartyDir(lastThirdParty.isActive() ? lastThirdParty.getEffectiveFile() : null);
		ikonNacitacLoader.setImageMyDir(lastMyOwn.isActive() ? lastMyOwn.getEffectiveFile() : null);
		nacita = true;
		sw = new IkonNacitacSwingWorker(ikonNacitacLoader, prenacti, kesoidModel, this::poNacteni);
		sw.execute();
	}

	/**
	 * Na EDT po doběhnutí načítání (i neúspěšného). Čeká-li další načtení, výsledek je zastaralý a nepoužije se (jinak by se keše načítaly dvakrát); čekající
	 * načtení pak načte vše znovu, protože loader si zastaralé načtení pamatuje jako poslední a beze změny složek by nic nevrátil.
	 */
	private void poNacteni(final IkonBag vysledek) {
		nacita = false;
		if (cekaDalsi) {
			cekaDalsi = false;
			spust(true);
		} else if (vysledek != null) {
			kesoidModel.setIkonBag(vysledek);
		}
	}

	/** Pro testy: načítání běží, nebo ještě neproběhlo jeho dokončení na EDT. */
	boolean nacita() {
		return nacita;
	}

	/** Pro testy: worker už dopočítal, ale {@link #poNacteni(IkonBag)} na EDT ještě neproběhlo. */
	boolean dopocitanoPredDokoncenim() {
		return nacita && sw.isDone();
	}
}
