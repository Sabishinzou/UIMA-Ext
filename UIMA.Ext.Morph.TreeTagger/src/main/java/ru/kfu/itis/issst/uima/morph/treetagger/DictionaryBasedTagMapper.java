/**
 * 
 */
package ru.kfu.itis.issst.uima.morph.treetagger;

import static ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionaryUtils.toGramBits;

import java.util.BitSet;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.cas.CASException;
import org.apache.uima.resource.ResourceInitializationException;
import org.opencorpora.cas.Wordform;
import org.uimafit.component.initialize.ExternalResourceInitializer;
import org.uimafit.descriptor.ExternalResource;
import org.uimafit.factory.initializable.Initializable;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;

import ru.kfu.itis.cll.uima.cas.FSUtils;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionary;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionaryHolder;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class DictionaryBasedTagMapper implements TagMapper, Initializable {

	public static final String RESOURCE_KEY_MORPH_DICTIONARY = "MorphDictionary";
	// config fields
	@ExternalResource(key = RESOURCE_KEY_MORPH_DICTIONARY, mandatory = true)
	private MorphDictionaryHolder dictHolder;
	// derived
	private MorphDictionary dict;

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		ExternalResourceInitializer.initialize(ctx, this);
		dict = dictHolder.getDictionary();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void parseTag(String tag, Wordform wf, String token) {
		if (tag == null || tag.equalsIgnoreCase("null")) {
			return;
		}
		List<String> grams = Lists.newLinkedList(targetGramSplitter.split(tag));
		try {
			wf.setGrammems(FSUtils.toStringArray(wf.getCAS().getJCas(), grams));
		} catch (CASException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toTag(Wordform wf) {
		BitSet wfBits = toGramBits(dict, FSUtils.toList(wf.getGrammems()));
		if (wfBits.isEmpty()) {
			return null;
		}
		return targetGramJoiner.join(dict.toGramSet(wfBits));
	}

	private static final String targetGramDelim = "&";
	private static final Joiner targetGramJoiner = Joiner.on(targetGramDelim);
	private static final Splitter targetGramSplitter = Splitter.on(targetGramDelim);
}