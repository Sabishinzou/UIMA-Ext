/**
 * 
 */
package ru.ksu.niimm.cll.uima.morph.ruscorpora;

import static ru.kfu.itis.cll.uima.util.BitUtils.contains;
import static ru.kfu.itis.cll.uima.util.DocumentUtils.getDocumentUri;
import static ru.kfu.itis.issst.uima.morph.dictionary.WordUtils.normalizeToDictionaryForm;
import static ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionaryUtils.toGramBits;
import static ru.kfu.itis.issst.uima.morph.model.Wordform.getAllGramBits;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.BitSet;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.FeatureStructure;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.opencorpora.cas.Word;
import org.uimafit.component.initialize.ConfigurationParameterInitializer;
import org.uimafit.component.initialize.ExternalResourceInitializer;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.initializable.Initializable;

import ru.kfu.itis.cll.uima.cas.FSUtils;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.GramModel;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionary;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.MorphDictionaryHolder;
import ru.kfu.itis.issst.uima.morph.model.Wordform;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class DictionaryAligningTagMapper implements RusCorporaTagMapper, Initializable, Closeable {

	public static final String RESOURCE_KEY_MORPH_DICTIONARY = "MorphDictionary";
	public static final String PARAM_OUT_FILE = "outFile";
	// config fields
	private RusCorporaTagMapper delegate = new RusCorpora2OpenCorporaTagMapper();
	@ExternalResource(key = RESOURCE_KEY_MORPH_DICTIONARY, mandatory = true)
	private MorphDictionaryHolder dictHolder;
	@ConfigurationParameter(name = PARAM_OUT_FILE, mandatory = true)
	private File outFile;
	// config-derived
	private MorphDictionary dict;
	private GramModel gm;
	private PrintWriter out;

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		ExternalResourceInitializer.initialize(ctx, this);
		ConfigurationParameterInitializer.initialize(this, ctx);
		dict = dictHolder.getDictionary();
		gm = dict.getGramModel();
		try {
			FileOutputStream os = FileUtils.openOutputStream(outFile);
			Writer bw = new BufferedWriter(new OutputStreamWriter(os, "utf-8"));
			out = new PrintWriter(bw);
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		}
	}

	@Override
	public void close() throws IOException {
		IOUtils.closeQuietly(out);
	}

	@Override
	public void mapFromRusCorpora(RusCorporaWordform srcWf, org.opencorpora.cas.Wordform targetWf) {
		Word wordAnno = targetWf.getWord();
		delegate.mapFromRusCorpora(srcWf, targetWf);
		// first - check whether tag is in tagset
		final BitSet wfTag = toGramBits(gm, FSUtils.toList(targetWf.getGrammems()));
		// skip INIT as a whole new category
		if (wfTag.get(gm.getGrammemNumId(RNCMorphConstants.RNC_INIT))) {
			// skip
			return;
		}
		//
		if (!dict.containsGramSet(wfTag)) {
			// if there is no such tag in dictionary then look the word in it
			String wordStr = wordAnno.getCoveredText();
			List<Wordform> dictWfs = dict.getEntries(normalizeToDictionaryForm(wordStr));
			if (dictWfs == null || dictWfs.isEmpty()) {
				onUnknownWord(wordAnno, wfTag);
				return;
			}
			// search for a unique dictionary entry that has a tag extending the given one
			List<Wordform> wfExtensions = Lists.newLinkedList();
			for (Wordform dictWf : dictWfs) {
				BitSet dictWfTag = getAllGramBits(dictWf, dict);
				if (contains(dictWfTag, wfTag)) {
					wfExtensions.add(dictWf);
				}
			}
			if (wfExtensions.isEmpty()) {
				onConflictingTag(wordAnno, wfTag);
			} else if (wfExtensions.size() > 1) {
				onAmbiguousWordform(wordAnno, wfTag);
			} else {
				BitSet newTag = getAllGramBits(wfExtensions.get(0), dict);
				List<String> newTagStr = gm.toGramSet(newTag);
				targetWf.setGrammems(FSUtils.toStringArray(getCAS(targetWf), newTagStr));
				onTagExtended(wordAnno, wfTag, newTag);
			}
		}
	}

	private static JCas getCAS(FeatureStructure fs) {
		try {
			return fs.getCAS().getJCas();
		} catch (CASException e) {
			throw new IllegalStateException(e);
		}
	}

	private void onTagExtended(Word wordAnno, BitSet wfTag, BitSet newTag) {
		out.println(String.format("[+]\t%s\t%s\t%s\t%s",
				wordAnno.getCoveredText(),
				toGramString(wfTag), toGramString(newTag),
				getPrettyLocation(wordAnno)));
	}

	private void onAmbiguousWordform(Word wordAnno, BitSet wfTag) {
		out.println(String.format("[A]\t%s\t%s\t%s",
				wordAnno.getCoveredText(), toGramString(wfTag), getPrettyLocation(wordAnno)));
	}

	private void onConflictingTag(Word wordAnno, BitSet wfTag) {
		out.println(String.format("[C]\t%s\t%s\t%s",
				wordAnno.getCoveredText(), toGramString(wfTag), getPrettyLocation(wordAnno)));
	}

	/**
	 * Invoked when there is a word unknown to the dictionary and it was
	 * assigned unknown tag.
	 */
	private void onUnknownWord(Word wordAnno, BitSet wfTag) {
		out.println(String.format("[U]\t%s\t%s\t%s",
				wordAnno.getCoveredText(), toGramString(wfTag), getPrettyLocation(wordAnno)));
	}

	private static String getPrettyLocation(Word anno) {
		String docUri = getDocumentUri(anno.getCAS());
		return String.format("%s:%s", docUri, anno.getBegin());
	}

	private String toGramString(BitSet gramBits) {
		return gramJoiner.join(dict.getGramModel().toGramSet(gramBits));
	}

	private static final Joiner gramJoiner = Joiner.on(',');
}
