/**
 * 
 */
package ru.kfu.itis.issst.uima.postagger.opennlp;

import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import ru.kfu.cll.uima.tokenizer.fstype.Token;
import ru.kfu.cll.uima.tokenizer.fstype.W;
import ru.ksu.niimm.cll.uima.morph.opencorpora.WordUtils;
import ru.ksu.niimm.cll.uima.morph.opencorpora.model.Grammeme;
import ru.ksu.niimm.cll.uima.morph.opencorpora.model.Wordform;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.GramModel;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionary;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class DictionaryBasedContextGenerator {

	// config fields
	private MorphDictionary morphDict;
	private GramModel gramModel;
	// derived
	private final Map<Grammeme, BitSet> targetTagCategoriesMap;
	private final BitSet targetCategoriesMask;

	public DictionaryBasedContextGenerator(Iterable<String> targetGramCategories,
			MorphDictionary morphDict) {
		this.morphDict = morphDict;
		this.gramModel = morphDict.getGramModel();
		// re-pack into a set to avoid duplicates and maintain ID-based ordering
		TreeSet<Grammeme> tagCatGrams = Sets.newTreeSet(Grammeme.numIdComparator());
		for (String tc : targetGramCategories) {
			Grammeme tcGram = gramModel.getGrammem(tc);
			if (tcGram == null) {
				throw new IllegalArgumentException(String.format(
						"Tag category %s does not exist", tc));
			}
			tagCatGrams.add(tcGram);
		}
		this.targetTagCategoriesMap = Maps.newHashMapWithExpectedSize(tagCatGrams.size());
		this.targetCategoriesMask = new BitSet();
		for (Grammeme tcg : tagCatGrams) {
			BitSet tcBits = gramModel.getGrammemWithChildrenBits(tcg.getId(), true);
			targetTagCategoriesMap.put(tcg, tcBits);
			targetCategoriesMask.or(tcBits);
		}
	}

	public List<String> extract(Token focusToken, String prevTag) {
		if (!(focusToken instanceof W)) {
			return ImmutableList.of();
		}
		String form = focusToken.getCoveredText();
		if (!WordUtils.isRussianWord(form)) {
			return ImmutableList.of("DL=NotRussian");
		}
		form = WordUtils.normalizeToDictionaryForm(form);
		List<Wordform> dictWfs = morphDict.getEntries(form);
		if (dictWfs == null || dictWfs.isEmpty()) {
			return ImmutableList.of("DL=Unknown");
		}
		List<BitSet> dictWfBitSets = Lists.transform(dictWfs,
				Wordform.allGramBitsFunction(morphDict));
		//
		Set<BitSet> tokenPossibleTags = Sets.newHashSetWithExpectedSize(dictWfBitSets.size());
		for (BitSet dictWfBits : dictWfBitSets) {
			BitSet tokenPossibleBits = (BitSet) dictWfBits.clone();
			tokenPossibleBits.and(targetCategoriesMask);
			tokenPossibleTags.add(tokenPossibleBits);
		}
		List<String> resultList = Lists.newArrayListWithExpectedSize(tokenPossibleTags.size());
		for (BitSet tokenPossibleBits : tokenPossibleTags) {
			String featValue;
			if (tokenPossibleBits.isEmpty()) {
				featValue = "NULL";
			} else {
				featValue = gramJoiner.join(gramModel.toGramSet(tokenPossibleBits));
			}
			resultList.add("DL=" + featValue);
		}
		// XXX
		// TODO add aggreement features
		// XXX
		return resultList;
	}

	private static final Joiner gramJoiner = Joiner.on('_');

}
