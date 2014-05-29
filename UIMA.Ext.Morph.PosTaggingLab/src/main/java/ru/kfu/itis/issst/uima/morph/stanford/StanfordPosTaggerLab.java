/**
 * 
 */
package ru.kfu.itis.issst.uima.morph.stanford;

import static com.google.common.collect.Sets.newTreeSet;
import static de.tudarmstadt.ukp.dkpro.lab.storage.StorageService.AccessMode.READONLY;
import static de.tudarmstadt.ukp.dkpro.lab.storage.StorageService.AccessMode.READWRITE;
import static org.apache.commons.io.FileUtils.openOutputStream;
import static org.apache.commons.io.IOUtils.closeQuietly;
import static org.uimafit.factory.AnalysisEngineFactory.createAggregateDescription;
import static org.uimafit.factory.AnalysisEngineFactory.createPrimitiveDescription;
import static org.uimafit.factory.ExternalResourceFactory.bindResource;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.DISCRIMINATOR_CORPUS_SPLIT_INFO_DIR;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.DISCRIMINATOR_FOLD;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.DISCRIMINATOR_POS_CATEGORIES;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.DISCRIMINATOR_SOURCE_CORPUS_DIR;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.KEY_CORPUS;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.KEY_MODEL_DIR;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.KEY_OUTPUT_DIR;
import static ru.ksu.niimm.cll.uima.morph.lab.LabConstants.KEY_TRAINING_DIR;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.resource.ExternalResourceDescription;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.uima.util.InvalidXMLException;
import org.uimafit.factory.ExternalResourceFactory;

import ru.kfu.itis.cll.uima.util.CorpusUtils.PartitionType;
import ru.kfu.itis.issst.uima.morph.commons.DictionaryBasedTagMapper;
import ru.ksu.niimm.cll.uima.morph.lab.AnalysisTaskBase;
import ru.ksu.niimm.cll.uima.morph.lab.CorpusPreprocessingTask;
import ru.ksu.niimm.cll.uima.morph.lab.EvaluationTask;
import ru.ksu.niimm.cll.uima.morph.lab.FeatureExtractionTaskBase;
import ru.ksu.niimm.cll.uima.morph.lab.LabLauncherBase;
import ru.ksu.niimm.cll.uima.morph.opencorpora.resource.MorphDictionaryHolder;

import com.beust.jcommander.JCommander;
import com.google.common.base.Joiner;

import de.tudarmstadt.ukp.dkpro.lab.Lab;
import de.tudarmstadt.ukp.dkpro.lab.engine.TaskContext;
import de.tudarmstadt.ukp.dkpro.lab.storage.StorageService.AccessMode;
import de.tudarmstadt.ukp.dkpro.lab.task.Dimension;
import de.tudarmstadt.ukp.dkpro.lab.task.Discriminator;
import de.tudarmstadt.ukp.dkpro.lab.task.ParameterSpace;
import de.tudarmstadt.ukp.dkpro.lab.task.Task;
import de.tudarmstadt.ukp.dkpro.lab.task.impl.BatchTask;
import de.tudarmstadt.ukp.dkpro.lab.task.impl.BatchTask.ExecutionPolicy;
import de.tudarmstadt.ukp.dkpro.lab.task.impl.ExecutableTaskBase;
import de.tudarmstadt.ukp.dkpro.lab.uima.task.UimaTask;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class StanfordPosTaggerLab extends LabLauncherBase {

	static final String DEFAULT_WRK_DIR = "wrk/stanford-pos-lab";

	public static void main(String[] args) throws Exception {
		System.setProperty("DKPRO_HOME", DEFAULT_WRK_DIR);
		StanfordPosTaggerLab lab = new StanfordPosTaggerLab();
		new JCommander(lab, args);
		lab.run();
	}

	private boolean allowTaggerMultiDeployment = false;

	private StanfordPosTaggerLab() {
	}

	private void run() throws Exception {
		UimaTask preprocessingTask = new CorpusPreprocessingTask(inputTS, morphDictDesc);
		//
		UimaTask prepareTrainingDataTask = new FeatureExtractionTaskBase(
				"PrepareTrainingData", inputTS) {
			@Override
			public AnalysisEngineDescription getAnalysisEngineDescription(TaskContext taskCtx)
					throws ResourceInitializationException, IOException {
				File trainDataDir = taskCtx.getStorageLocation(KEY_TRAINING_DIR, READWRITE);
				AnalysisEngineDescription stanfordTrainDataWriterDesc = createPrimitiveDescription(
						StanfordTrainingDataWriter.class,
						StanfordTrainingDataWriter.PARAM_OUTPUT_DIR, trainDataDir);
				try {
					ExternalResourceFactory.createDependency(stanfordTrainDataWriterDesc,
							DictionaryBasedTagMapper.RESOURCE_KEY_MORPH_DICTIONARY,
							MorphDictionaryHolder.class);
					bindResource(stanfordTrainDataWriterDesc,
							DictionaryBasedTagMapper.RESOURCE_KEY_MORPH_DICTIONARY, morphDictDesc);
				} catch (InvalidXMLException e) {
					throw new ResourceInitializationException(e);
				}
				return createAggregateDescription(stanfordTrainDataWriterDesc);
			}
		};
		//
		Task trainingTask = new ExecutableTaskBase() {
			{
				setType("Training");
			}

			@Discriminator
			String featureArch;

			@Override
			public void execute(TaskContext taskCtx) throws Exception {
				File trainDataDir = taskCtx.getStorageLocation(KEY_TRAINING_DIR, READONLY);
				File trainDataFile = getTrainingDataFile(trainDataDir);
				File modelDir = taskCtx.getStorageLocation(KEY_MODEL_DIR, READWRITE);
				File modelFile = getModelFile(modelDir);
				//
				Properties props = new Properties();
				props.setProperty("model", modelFile.getPath());
				props.setProperty("trainFile", "format=TSV," + trainDataFile.getPath());
				//
				//extractors.add("tags(-1,1)");
				props.setProperty("arch", featureArch);
				// load closed class tags set
				Set<String> closedClassTags;
				{
					File ccTagsFile = new File(trainDataDir,
							StanfordTrainingDataWriter.CLOSED_CLASS_TAGS_FILENAME);
					closedClassTags = newTreeSet(FileUtils.readLines(ccTagsFile, "utf-8"));
				}
				props.setProperty("closedClassTags", Joiner.on(' ').join(closedClassTags));
				//
				File srcPropsFile = new File(modelDir, "train.props");
				saveProperties(props, srcPropsFile);
				MaxentTagger.main(new String[] { "-props", srcPropsFile.getPath() });
			}
		};
		//
		UimaTask analysisTask = new StanfordTaggerAnalysisTask(morphDictDesc, inputTS,
				PartitionType.DEV, allowTaggerMultiDeployment);
		//
		Task evaluationTask = new EvaluationTask(PartitionType.DEV);
		// configure data-flow between tasks
		prepareTrainingDataTask.addImport(preprocessingTask, KEY_CORPUS);
		trainingTask.addImport(prepareTrainingDataTask, KEY_TRAINING_DIR);
		analysisTask.addImport(preprocessingTask, KEY_CORPUS);
		analysisTask.addImport(trainingTask, KEY_MODEL_DIR);
		evaluationTask.addImport(preprocessingTask, KEY_CORPUS);
		evaluationTask.addImport(analysisTask, KEY_OUTPUT_DIR);
		// create parameter space
		// TODO:LOW determine PartitionTypes and folds number by scanning corpusSplitDir
		/*Integer[] foldValues = ContiguousSet.create(
				Range.closedOpen(0, foldsNum),
				DiscreteDomain.integers()).toArray(new Integer[0]);*/
		ParameterSpace pSpace = new ParameterSpace(
				getFileDimension(DISCRIMINATOR_SOURCE_CORPUS_DIR),
				getFileDimension(DISCRIMINATOR_CORPUS_SPLIT_INFO_DIR),
				// posCategories discriminator is used in the preprocessing task
				getStringSetDimension(DISCRIMINATOR_POS_CATEGORIES),
				Dimension.create(DISCRIMINATOR_FOLD, 0),
				// model training parameters
				getStringDimension("featureArch")
				);
		//
		BatchTask batchTask = new BatchTask();
		batchTask.addTask(preprocessingTask);
		batchTask.addTask(prepareTrainingDataTask);
		batchTask.addTask(trainingTask);
		batchTask.addTask(analysisTask);
		batchTask.addTask(evaluationTask);
		//
		batchTask.setParameterSpace(pSpace);
		batchTask.setExecutionPolicy(ExecutionPolicy.USE_EXISTING);
		Lab.getInstance().run(batchTask);
	}

	private static File getTrainingDataFile(File dir) {
		return new File(dir, StanfordTrainingDataWriter.TRAINING_DATA_FILENAME);
	}

	private static File getModelFile(File dir) {
		return new File(dir, "stanford.model");
	}

	private static void saveProperties(Properties props, File outFile) throws IOException {
		OutputStream os = new BufferedOutputStream(openOutputStream(outFile));
		try {
			props.store(os, null);
		} finally {
			closeQuietly(os);
		}
	}

	static class StanfordTaggerAnalysisTask extends AnalysisTaskBase {

		private ExternalResourceDescription morphDictDesc;
		private boolean allowTaggerMultiDeployment;

		StanfordTaggerAnalysisTask(ExternalResourceDescription morphDictDesc,
				TypeSystemDescription inputTS,
				PartitionType targetPartition,
				boolean allowTaggerMultiDeployment) {
			super(PartitionType.DEV.equals(targetPartition) ? "Analysis" : "AnalysisFinal",
					inputTS, targetPartition);
			this.morphDictDesc = morphDictDesc;
			this.allowTaggerMultiDeployment = allowTaggerMultiDeployment;
		}

		@Override
		public AnalysisEngineDescription getAnalysisEngineDescription(TaskContext taskCtx)
				throws ResourceInitializationException, IOException {
			File modelDir = taskCtx.getStorageLocation(KEY_MODEL_DIR, AccessMode.READONLY);
			File modelFile = getModelFile(modelDir);
			File outputDir = taskCtx.getStorageLocation(KEY_OUTPUT_DIR, AccessMode.READWRITE);
			//
			AnalysisEngineDescription goldRemoverDesc = createGoldRemoverDesc();
			AnalysisEngineDescription stanfordAnnotatorDesc = createPrimitiveDescription(
					StanfordPosAnnotator.class,
					StanfordPosAnnotator.PARAM_MODEL_FILE, modelFile.getPath(),
					StanfordPosAnnotator.PARAM_TAG_MAPPER_CLASS,
					DictionaryBasedTagMapper.class.getName());
			stanfordAnnotatorDesc.getAnalysisEngineMetaData().getOperationalProperties()
					.setMultipleDeploymentAllowed(allowTaggerMultiDeployment);
			try {
				ExternalResourceFactory.createDependency(stanfordAnnotatorDesc,
						DictionaryBasedTagMapper.RESOURCE_KEY_MORPH_DICTIONARY,
						MorphDictionaryHolder.class);
				bindResource(stanfordAnnotatorDesc,
						DictionaryBasedTagMapper.RESOURCE_KEY_MORPH_DICTIONARY,
						morphDictDesc);
			} catch (InvalidXMLException e) {
				throw new ResourceInitializationException(e);
			}
			AnalysisEngineDescription xmiWriterDesc = createXmiWriterDesc(outputDir);
			return createAggregateDescription(
					goldRemoverDesc, stanfordAnnotatorDesc, xmiWriterDesc);
		}
	}
}
