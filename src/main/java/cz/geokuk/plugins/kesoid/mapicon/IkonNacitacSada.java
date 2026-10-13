package cz.geokuk.plugins.kesoid.mapicon;

import java.io.*;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

import cz.geokuk.plugins.kesoid.genetika.Genom;
import cz.geokuk.util.file.KeyNode;
import cz.geokuk.util.file.LamUrl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class IkonNacitacSada {

	private static final String SKLA_TXT = "skla.txt";
	private static final String GROUPS_PROPERTIES = "groups.properties";

	private final Genom genom;
	private final Properties groupDisplayNames = new Properties();

	public IkonNacitacSada(final Genom genom) {
		this.genom = genom;
	}

	public Sada loadSada(final KeyNode<String, LamUrl> nodeSada) throws IOException {
		loadGroupDisplayNames(nodeSada);

		final KeyNode<String, LamUrl> sadyTxt = nodeSada.locate(SKLA_TXT);
		final Sada sada = new Sada(nodeSada.getData().name);
		if (sadyTxt == null) {
			error("Nenalezena definice skel " + SKLA_TXT + " v " + nodeSada);
			return null;
		}

		try (BufferedReader br = new BufferedReader(new StringReader(precti(sadyTxt.getData().url)))) {
			String line;
			final Map<String, Sklo> nactenaSkla = new HashMap<>();
			while ((line = br.readLine()) != null) {
				line = line.trim();
				if (line.length() == 0) {
					continue;
				}
				final String[] dvoj = line.split("\\.", 2);
				final String skloName = dvoj[0];
				final String aplikaceSklaName = dvoj[1];

				final SkloAplikant skloAplikant = new SkloAplikant();
				skloAplikant.aplikaceSkla = Enum.valueOf(EAplikaceSkla.class, aplikaceSklaName);

				Sklo sklo = nactenaSkla.get(skloName);
				if (sklo == null) {
					final KeyNode<String, LamUrl> skloNode = nodeSada.locate(skloName);
					if (skloNode == null || skloNode.getData() == null) {
						error("Nenalezeno sklo " + skloName + " referencing from " + sadyTxt.getData().url);
						continue;
					}
					sklo = loadSklo(skloNode);
					nactenaSkla.put(skloName, sklo);
				}
				skloAplikant.sklo = sklo;
				sada.skloAplikanti.add(skloAplikant);
			}
		}
		return sada;
	}

	/** Text v UTF-8 (případně s BOM); vlastní sady uložené v kódování Windows se čtou jako windows-1250. */
	static String precti(final URL url) throws IOException {
		final byte[] bajty;
		try (InputStream in = url.openStream()) {
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final byte[] buf = new byte[4096];
			for (int n = in.read(buf); n >= 0; n = in.read(buf)) {
				out.write(buf, 0, n);
			}
			bajty = out.toByteArray();
		}
		String text;
		try {
			text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bajty))
			        .toString();
		} catch (final CharacterCodingException e) {
			text = new String(bajty, Charset.forName("windows-1250"));
		}
		return text.startsWith("\uFEFF") ? text.substring(1) : text;
	}

	String getGroupDisplayName(final String groupName) {
		return groupName == null ? null : groupDisplayNames.getProperty(groupName);
	}

	private void error(final String errstr) {
		log.warn(errstr);
	}

	/**
	 * @param nodeSada
	 * @throws IOException
	 */
	private void loadGroupDisplayNames(final KeyNode<String, LamUrl> nodeSada) throws IOException {
		final KeyNode<String, LamUrl> gropNamesNode = nodeSada.locate(GROUPS_PROPERTIES);
		if (gropNamesNode != null) {
			try (InputStream stm = gropNamesNode.getData().url.openStream()) {
				groupDisplayNames.load(stm);
			}
		}
	}

	private Sklo loadSklo(final KeyNode<String, LamUrl> skloNode) {
		final Sklo sklo = new Sklo(skloNode.getData().name);
		for (final KeyNode<String, LamUrl> vrstvaNode : skloNode.getSubNodes()) {
			final Vrstva vrstva = loadVrstva(vrstvaNode, sklo.getImageProvider());
			sklo.vrstvy.add(vrstva);
		}
		return sklo;
	}

	private Vrstva loadVrstva(final KeyNode<String, LamUrl> vrstvaNode, final ImageProvider imageProvider) {
		final Vrstva vrstva = new Vrstva();
		for (final Map.Entry<String, KeyNode<String, LamUrl>> entry : vrstvaNode.getItems().entrySet()) {
			final IconDefNacitac idn = new IconDefNacitac(genom, entry.getKey(), entry.getValue().getData().url, this);
			final IconDef iconDef = idn.loadIconDef(imageProvider);
			vrstva.add(iconDef);
		}
		return vrstva;
	}

}
