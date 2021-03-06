/**
 * 
 */
package ru.kfu.itis.issst.uima.morph.commons;

import static org.uimafit.factory.AnalysisEngineFactory.createPrimitiveDescription;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.resource.ResourceInitializationException;
import org.opencorpora.cas.Word;
import org.opencorpora.cas.Wordform;
import org.uimafit.component.JCasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;
import org.uimafit.factory.initializable.InitializableFactory;
import org.uimafit.util.FSCollectionFactory;
import org.uimafit.util.JCasUtil;

import ru.kfu.itis.cll.uima.cas.FSUtils;
import ru.kfu.itis.issst.uima.morph.dictionary.resource.GramModelHolder;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class TagAssembler extends JCasAnnotator_ImplBase {

	/**
	 * Create description of this annotator with default parameter values, i.e.:
	 * <ul>
	 * <li> {@link GramModelBasedTagMapper} is used, that requires to bind
	 * {@link GramModelHolder} to resource key
	 * {@value GramModelBasedTagMapper#RESOURCE_GRAM_MODEL}
	 * </ul>
	 * 
	 * @return a description instance
	 * @throws ResourceInitializationException
	 */
	public static AnalysisEngineDescription createDescription()
			throws ResourceInitializationException {
		AnalysisEngineDescription desc = createPrimitiveDescription(
				TagAssembler.class, // it does not produce any additional annotations => no need in TS 
				PARAM_TAG_MAPPER_CLASS, GramModelBasedTagMapper.class.getName());
		GramModelBasedTagMapper.declareResourceDependencies(desc);
		return desc;
	}

	public static final String PARAM_TAG_MAPPER_CLASS = "tagMapperClass";

	// config
	@ConfigurationParameter(name = PARAM_TAG_MAPPER_CLASS,
			defaultValue = "ru.kfu.itis.issst.uima.morph.commons.GramModelBasedTagMapper")
	private String tagMapperClassName;
	private TagMapper tagMapper;

	@Override
	public void initialize(UimaContext ctx) throws ResourceInitializationException {
		super.initialize(ctx);
		tagMapper = InitializableFactory.create(ctx, tagMapperClassName, TagMapper.class);
	}

	@Override
	public void process(JCas jCas) throws AnalysisEngineProcessException {
		for (Word word : JCasUtil.select(jCas, Word.class)) {
			FSArray wfs = word.getWordforms();
			if (wfs == null) {
				continue;
			}
			for (Wordform wf : FSCollectionFactory.create(wfs, Wordform.class)) {
				String tag = tagMapper.toTag(FSUtils.toSet(wf.getGrammems()));
				wf.setPos(tag);
			}
		}
	}

}
