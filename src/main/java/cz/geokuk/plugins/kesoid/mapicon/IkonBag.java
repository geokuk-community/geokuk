package cz.geokuk.plugins.kesoid.mapicon;

import java.util.Map;
import java.util.Set;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import cz.geokuk.api.mapicon.Imagant;
import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.plugins.kesoid.genetika.Genotyp;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IkonBag {

	private Set<String> jmenaSad;
	private Sada sada;
	private final Genom genom = new Genom();
	private Map<ASada, Icon> iJmenaAIkonySad;

	public Genom getGenom() {
		return genom;
	}

	/**
	 * @return the jmenaAIkonySad
	 */
	public Map<ASada, Icon> getJmenaAIkonySad() {
		return iJmenaAIkonySad;
	}

	public Sada getSada() {
		return sada;
	}

	public Set<String> getSadyNames() {
		return jmenaSad;
	}

	public Sklivec getSklivec(final Genotyp genotyp) {
		return sada.getSklivec(genotyp);

	}

	public void print() {
		log.debug("Sady: {}", jmenaSad);
		for (final SkloAplikant skloAplikant : sada.skloAplikanti) {
			log.debug("   *** sklo: {}", skloAplikant.aplikaceSkla);
			// for (Vrstva vrstva : skloAplikant.sklo.vrstvy) {
			log.debug("        ### vrstva");
		}
	}

	public Icon seekIkon(final Genotyp genotyp) {
		final Sklivec sklivec = getSklivec(genotyp);
		final Imagant imagant = Imagant.prekresliNaSebe(sklivec.imaganti);
		if (imagant == null) {
			return null;
		}
		return new ImageIcon(imagant.getImage());
	}

	/**
	 * @param aNactiIkonySad
	 */
	public void setJmenaAIkonySad(final Map<ASada, Icon> aJmenaAIkonySad) {
		iJmenaAIkonySad = aJmenaAIkonySad;
	}

	public void setJmenaSad(final Set<String> jmenaSad) {
		this.jmenaSad = jmenaSad;
	}

	public void setSada(final Sada sada) {
		this.sada = sada;
	}

}
