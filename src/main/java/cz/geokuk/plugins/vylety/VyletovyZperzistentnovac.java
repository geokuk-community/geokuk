package cz.geokuk.plugins.vylety;

import java.io.*;
import java.nio.charset.Charset;
import java.util.*;

import cz.geokuk.core.program.FConst;
import cz.geokuk.plugins.kesoid.KesBag;
import cz.geokuk.plugins.kesoid.Kesoid;
import cz.geokuk.plugins.kesoid.mvc.KesoidModel;
import cz.geokuk.util.file.BezpecnyZapis;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class VyletovyZperzistentnovac {



	private KesoidModel kesoidModel;

	public Vylet immediatlyNactiVylet(final KesBag vsechny) {
		try {
			final Vylet novyvylet = new Vylet();
			aktualizujVylet(novyvylet, loadGgt(kesoidModel.getUmisteniSouboru().getAnoGgtFile().getEffectiveFile()), EVylet.ANO, vsechny);
			aktualizujVylet(novyvylet, loadGgt(kesoidModel.getUmisteniSouboru().getNeGgtFile().getEffectiveFile()), EVylet.NE, vsechny);
			return novyvylet;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	/** Synchronizovaně, aby se dva zápisy nepřekryly a soubor nezůstal zpřeházený. */
	public synchronized void immediatlyZapisVylet(final List<String> ano, final List<String> ne) {
		zapis(ano, kesoidModel.getUmisteniSouboru().getAnoGgtFile().getEffectiveFile(), EVylet.ANO);
		zapis(ne, kesoidModel.getUmisteniSouboru().getNeGgtFile().getEffectiveFile(), EVylet.NE);
	}

	public void inject(final KesoidModel kesoidModel) {
		this.kesoidModel = kesoidModel;
	}

	private void aktualizujVylet(final Vylet novyvylet, final VyletPul vyletPul, final EVylet evyl, final KesBag vsechny) {
		final Set<String> nezname = new LinkedHashSet<>(vyletPul.kesides);
		if (vsechny != null) {
			for (final Kesoid kes : vsechny.getKesoidy()) {
				if (vyletPul.kesides.contains(kes.getIdentifier())) {
					novyvylet.add(evyl, kes);
					nezname.remove(kes.getIdentifier());
				}
			}
		}
		novyvylet.pridejNezname(evyl, nezname);
	}

	private VyletPul loadGgt(final BufferedReader reader) throws IOException {
		String line;
		final Set<String> set = new LinkedHashSet<>();
		while ((line = reader.readLine()) != null) {
			line = line.trim();
			if (line.isEmpty()) {
				continue;
			}
			set.add(line);
		}
		return new VyletPul(set);
	}

	private VyletPul loadGgt(final File file) throws IOException {
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			return loadGgt(br);
		} catch (final FileNotFoundException e) {
			return new VyletPul(new HashSet<>());
		}
	}

	private void zapis(final List<String> kody, final File file, final EVylet evyl) {
		try {
			BezpecnyZapis.zapisText(file, Charset.defaultCharset(), wrt -> {
				for (final String kod : kody) {
					wrt.print(kod);
					wrt.print(FConst.NL);
				}
			});
		} catch (final IOException e) {
			throw new RuntimeException("Výlet nelze uložit do souboru " + file, e);
		}
		log.info("Uloženo {} keší pro výlet {}.", kody.size(), evyl);
	}
}
