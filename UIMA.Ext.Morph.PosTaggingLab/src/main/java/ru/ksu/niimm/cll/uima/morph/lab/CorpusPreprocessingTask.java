/**
 * 
 */
package ru.ksu.niimm.cll.uima.morph.lab;

import static org.uimafit.factory.AnalysisEngineFactory.createAggregateDescription;
import static org.uimafit.factory.AnalysisEngineFactory.createPrimitiveDescription;
import static org.uimafit.factory.ExternalResourceFactory.bindExternalResource;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.KEY_CORPUS;

import java.io.File;
import java.io.IOException;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.uimafit.factory.CollectionReaderFactory;

import ru.kfu.itis.cll.uima.consumer.XmiWriter;
import ru.kfu.itis.cll.uima.cpe.XmiCollectionReader;
import ru.kfu.itis.issst.uima.morph.commons.GramModelBasedTagMapper;
import ru.kfu.itis.issst.uima.morph.commons.TagAssembler;
import ru.kfu.itis.issst.uima.postagger.PosTrimmingAnnotator;
import de.tudarmstadt.ukp.dkpro.lab.engine.TaskContext;
import de.tudarmstadt.ukp.dkpro.lab.storage.StorageService.AccessMode;
import de.tudarmstadt.ukp.dkpro.lab.task.Discriminator;
import de.tudarmstadt.ukp.dkpro.lab.uima.task.impl.UimaTaskBase;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class CorpusPreprocessingTask extends UimaTaskBase {

	{
		setType("CorpusPreProcessing");
	}
	// config fields
	private TypeSystemDescription inputTS;
	private ExternalResourceDescription gramModelDesc;

	public CorpusPreprocessingTask(TypeSystemDescription inputTS,
			ExternalResourceDescription gramModelDesc) {
		this.inputTS = inputTS;
		this.gramModelDesc = gramModelDesc;
	}

	// state fields
	@Discriminator
	Set<String> posCategories;
	@Discriminator
	File srcCorpusDir;

	@Override
	public CollectionReaderDescription getCollectionReaderDescription(TaskContext taskCtx)
			throws ResourceInitializationException, IOException {
		return CollectionReaderFactory.createDescription(XmiCollectionReader.class,
				inputTS,
				XmiCollectionReader.PARAM_INPUTDIR, srcCorpusDir.getPath());
	}

	@Override
	public AnalysisEngineDescription getAnalysisEngineDescription(TaskContext taskCtx)
			throws ResourceInitializationException, IOException {
		AnalysisEngineDescription posTrimmerDesc = createPrimitiveDescription(
				PosTrimmingAnnotator.class, inputTS,
				PosTrimmingAnnotator.PARAM_TARGET_POS_CATEGORIES, posCategories,
				PosTrimmingAnnotator.RESOURCE_GRAM_MODEL, gramModelDesc);
		//
		AnalysisEngineDescription tagAssemblerDesc = TagAssembler.createDescription();
		bindExternalResource(tagAssemblerDesc,
				GramModelBasedTagMapper.RESOURCE_GRAM_MODEL, gramModelDesc);
		//
		AnalysisEngineDescription xmiWriterDesc = XmiWriter.createDescription(
				taskCtx.getStorageLocation(KEY_CORPUS, AccessMode.READWRITE),
				// write to relative path
				true);
		//
		return createAggregateDescription(posTrimmerDesc, tagAssemblerDesc, xmiWriterDesc);
	}

}
