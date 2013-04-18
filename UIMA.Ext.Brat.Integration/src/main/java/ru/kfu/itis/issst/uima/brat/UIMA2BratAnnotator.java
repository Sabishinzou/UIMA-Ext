package ru.kfu.itis.issst.uima.brat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.CASRuntimeException;
import org.apache.uima.cas.FSIterator;
import org.apache.uima.cas.Feature;
import org.apache.uima.cas.Type;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.cas.text.AnnotationIndex;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.uimafit.component.CasAnnotator_ImplBase;
import org.uimafit.descriptor.ConfigurationParameter;

import ru.kfu.itis.cll.uima.commons.DocumentMetadata;

/**
 * UIMA Annotator is CAS Annotator to convert UIMA annotations to brat standoff
 * format annotations. 1) defines input, ouput files directories 2) reading
 * annotator descriptor file and converts parameters to brat configuration file
 * saved as annotation.conf 3) saves annotations text file using specified file
 * name parameter in DocumentMetadata annotations. 4) reading UIMA annotations
 * and converts them to brat annotation (*.ann files)
 * 
 * T: text-bound annotation R: relation E: event A: attribute M: modification
 * (alias for attribute, for backward compatibility) N: normalization #: note
 * 
 * For event annotation you have to add additional info about event entities
 * into desc file
 * 
 * @author pathfinder
 * @author Rinat Gareev (Kazan Federal University)
 */

public class UIMA2BratAnnotator extends CasAnnotator_ImplBase {

	// Input Parameters
	// Brat Annotation types
	// Brat Output directory

	public final static String BRAT_OUT = "BratOutputDir";
	public final static String TYPES_TO_BRAT = "TypesToBrat";
	public final static String CONF_FILE = "annotation.conf";
	public final static String DEFAULT_BRAT_OUTPUTDIR = "data/bratOutPutDir/";

	public final static String ENCODING = "UTF-8";
	public final static String FILE_SEPARATOR = "file.separator";
	public final static String TXT_FILE_FORMAT = ".txt";
	public final static String ANN_FILE_FORMAT = ".ann";
	public final static String RELATION_ARG_NAME = "Arg";
	public final static String EVENT_ARG_NAME = "EArg";
	public final static String DM_ENTITY_TYPE_FEATURE_BASE_NAME = "sourceUri";

	public static int tCounter = 0;
	public static int rCounter = 0;
	public static int eCounter = 0;

	//
	@ConfigurationParameter(name = BRAT_OUT, mandatory = true)
	private File bratDirectory;
	@ConfigurationParameter(name = TYPES_TO_BRAT, mandatory = true)
	private String[] typesToBrat;

	// Brat types
	private HashMap<String, String> entities = new HashMap<String, String>();
	private HashMap<String, String> events = new HashMap<String, String>();
	private HashMap<String, String> eventsArgs = new HashMap<String, String>();
	private HashMap<String, String> relations = new HashMap<String, String>();

	// output
	// private HashMap<String, String> outs = new HashMap<String, String>();

	// Annotation.conf writer
	private BufferedWriter acWriter;

	FSIterator<AnnotationFS> iterator = null;
	URI uri = null;
	String sourceUri = null;
	Type dmEntityType = null;
	String txt = "";
	JCas jcas = null;
	Feature dmTypeFeature = null;
	AnnotationIndex<AnnotationFS> annotationIndex = null;
	File annFile, af, f = null;
	String ann, fileName = null;

	@Override
	public void initialize(UimaContext ctx)
			throws ResourceInitializationException {
		super.initialize(ctx);

		getLogger().info("Annotator is initializing ...");

		try {
			if (!bratDirectory.isDirectory())
				bratDirectory.mkdirs();
			File annotationConfFile = new File(bratDirectory, CONF_FILE);
			acWriter = new BufferedWriter(new OutputStreamWriter(
					new FileOutputStream(annotationConfFile), "utf-8"));
			getLogger().info("Reading UIMA types to convert to brat annotations ... ");
			writeBratTypesConfig();
		} catch (IOException e) {
			throw new ResourceInitializationException(e);
		} finally {
			IOUtils.closeQuietly(acWriter);
		}
	}

	@Override
	public void typeSystemInit(TypeSystem ts)
			throws AnalysisEngineProcessException {
		super.typeSystemInit(ts);
	}

	@Override
	public void process(CAS casObj) throws AnalysisEngineProcessException {

		// LOGGER.info("Saving text and annotations files into brat output directory.");
		AnnotationFS fs;
		// Initializing CAS Object and getting annotation iterator and source
		// URI annotation (Document Metadata)
		try {
			jcas = casObj.getJCas();
		} catch (CASException e) {
			throw new AnalysisEngineProcessException(e);
		}
		txt = jcas.getDocumentText();
		// TODO move to initialize & typeSystemInit
		dmEntityType = (Type) jcas.getTypeSystem().getType(
				DocumentMetadata.class.getName());
		annotationIndex = (AnnotationIndex<AnnotationFS>) jcas.getCas()
				.getAnnotationIndex();
		iterator = annotationIndex.iterator();
		dmTypeFeature = dmEntityType
				.getFeatureByBaseName(DM_ENTITY_TYPE_FEATURE_BASE_NAME);

		// Find sourceUri annotation first to write annotations somewhere!
		// TODO simplify using typed annotationIndex
		while (iterator.isValid()) {
			fs = iterator.get();
			// Writing annotations files init:
			try {
				if (fs.getFeatureValueAsString(dmTypeFeature) != null
						&& !fs.getFeatureValueAsString(dmTypeFeature)
								.equals("x-unspecified")) {
					try {
						uri = new URI(
								fs.getFeatureValueAsString(dmTypeFeature));
						sourceUri = uri.getPath();
						annFile = new File(sourceUri);
						fileName = annFile.getName();
						af = new File(bratDirectory, fileName + ANN_FILE_FORMAT);
						break;
					} catch (CASRuntimeException e) {
						e.printStackTrace();
					} catch (URISyntaxException e) {
						e.printStackTrace();
					}

				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			iterator.moveToNext();
		}
		iterator = annotationIndex.iterator();
		// Iterate over the annotations
		while (iterator.isValid()) {
			fs = iterator.get();
			// Get entity annotations
			if (entities.get(fs.getType().getName()) != null) {
				writeEntity(fs, af);
			}
			// Get relation annotations
			if (relations.get(fs.getType().getName()) != null) {
				writeEvent(fs, af);
			}
			// Get event annotations
			if (events.get(fs.getType().getName()) != null) {
				writeEvent(fs, af);
			}
			iterator.moveToNext();
		}
		// Writing annotations text
		if (sourceUri != null) {
			getLogger().debug(fileName + " is in process.");
			f = new File(bratDirectory, fileName + TXT_FILE_FORMAT);
			try {
				FileUtils.write(f, txt, ENCODING);
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else
			getLogger().warn("TEXT FILE NAME IS EMPTY");

		// finalize per one annotation file
		tCounter = 1;
		rCounter = 1;
		eCounter = 1;
		af = null;
		f = null;

	}

	private String writeEvent(AnnotationFS fs, File af) {
		String ann, t2 = null, t = null, out = "E";
		boolean fl = false;

		// Set event text-bound annotation.
		if (events.get(fs.getType().getName()) == null) {
			if (relations.get(fs.getType().getName()) != null) {
				// ???
				events.put(fs.getType().getName(), relations.get(fs.getType().getName()));
				out = "R";
				fl = true;
			}
		}
		if (!fl) {
			entities.put(fs.getType().getName(), events.get(fs.getType().getName()).split("\t")[0]);

			t2 = writeEntity(fs, af);

			entities.remove(fs.getType().getName());

		}
		if (!fl)
			ann = "E" + eCounter + "\t"
					+ events.get(fs.getType().getName()).split("\t ")[0] + ":"
					+ t2;
		else
			ann = "R" + rCounter + "\t"
					+ events.get(fs.getType().getName()).split("\t")[0];

		Type tp = fs.getType();

		HashMap<String, String> args = new HashMap<String, String>();

		String[] afs = eventsArgs.keySet().toArray(new String[0]);

		// Events.get(fs.getType().getName()).replaceAll("[^A-z\\-\\_]","").split(":");

		int k = 1;
		for (Feature f : tp.getFeatures()) {
			// Recognize a valid features and add them to brat annotation
			// structure
			try {
				for (String ftype : afs) {
					// check whether event  arg
					if (entities.containsValue(ftype)
							&& !f.getRange().isPrimitive()
							&& fs.getFeatureValue(f) != null
							&& entities.containsKey(fs.getFeatureValue(f).getType().getName())
							&& ftype.equals(entities.get(fs.getFeatureValue(f).getType()
									.getName()))) {
						t = writeEntity((AnnotationFS) fs.getFeatureValue(f), af);
						if (!fl) {
							System.out.println(ftype + k + t);

							args.put(eventsArgs.get(ftype) + k, t);
						} else
							args.put(RELATION_ARG_NAME.substring(0, 3) + k, t);
						k++;
						break;
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		if (fl) {
			if (relations.get(fs.getType().getName()) != null) {
				events.remove(fs.getType().getName());
				fl = false;
			}
		}
		System.out.println(args);
		for (String s : args.keySet()) {
			ann += " " + s + ":" + args.get(s);
		}
		ann += "\n";
		if (!fl) {
			out += eCounter;
			this.eCounter++;
		} else {
			out += rCounter;
			this.rCounter++;
		}
		if (ann.length() != 0) {
			try {
				if (!checkUnique(af, ann))
					FileUtils.writeStringToFile(af, ann, true);
				// outs.put(out, ann);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return out;
	}

	private String writeEntity(AnnotationFS fs, File af) {
		String ann, out = "T";
		// Add entity to annotation file ...
		ann = "T" + tCounter + "\t" + entities.get(fs.getType().getName())
				+ " " + fs.getBegin() + " " + fs.getEnd() + "\t"
				+ fs.getCoveredText();
		ann += "\n";
		out += tCounter;
		tCounter++;
		if (ann.length() != 0) {
			try {
				if (!checkUnique(af, ann))
					//System.out.println("writing entity ..."+ann);
					FileUtils.writeStringToFile(af, ann, true);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return out;
	}

	private boolean checkUnique(File af2, String ann2) throws IOException {
		if (af.canRead())
			for (String s : FileUtils.readLines(af2)) {
				if (s.replace(s.split("\t")[0] + "\t", "").equals(
						ann2.replace(ann2.split("\t")[0] + "\t", "")
								.split("\n")[0]))
					return true;
			}
		return false;
	}

	private void writeBratTypesConfig() {
		// Generate entities to brat configuration file
		BratTypes anTypes = null;
		String bratType = "none";

		boolean fl;
		if (typesToBrat.length != 0) {

			for (String s : typesToBrat) {
				fl = false;
				if (s.split(";").length > 2)
					bratType = s.split(";")[2];
				else
					getLogger().warn("There is no type for " + s + "this object!");
				try {
					anTypes = BratTypes.valueOf(bratType.toUpperCase());
				} catch (Exception e) {
					fl = true;
					continue;
				}
				if (!fl)
					switch (anTypes) {

					case ENTITY:
						entities.put(s.split(";")[1], s.split(";")[0]);
						break;

					case RELATION:
						relations.put(s.split(";")[1],
								parseRelationArgs(s.split(";")[0]));
						break;

					case EVENT:
						events.put(s.split(";")[1],
								parseEventArgs(s.split(";")[0]));
						break;

					default:
						break;
					}
			}

			// Writing results to file annotation configuration file
			writeToFile("[entities]");

			// System.out.println(entities);
			for (String s : entities.keySet()) {
				writeToFile(entities.get(s));
			}

			writeToFile("[events]");

			// System.out.println(events);
			for (String s : events.keySet()) {
				if (events.get(s).split(":").length > 0)
					writeToFile(events.get(s));
				else
					writeToFile(s);
			}

			writeToFile("[attributes]");

			writeToFile("[relations]");

			// System.out.println(relations);
			for (String s : relations.keySet()) {
				writeToFile(relations.get(s));
			}
		}
	}

	private String parseEventArgs(String args) {
		String out = null;

		String argss[] = args.split(":");

		if (args.length() > 0) {
			out = "";
			out += argss[0] + "\t ";
			int ik = 0;
			String val, param, arg;
			for (String s : argss) {
				if (ik != 0) {

					val = s.replaceAll("[\\?\\*\\+\\{\\d\\}]", "");

					arg = s.replaceAll("[^A-z]", "") + "-" + EVENT_ARG_NAME;

					param = s.replaceAll("[\\|\\-\\_A-z]", "");

					out += arg + param + ":" + val;

					eventsArgs.put(val, arg);

					if (ik != argss.length - 1 && ik != 0)
						out += ", ";
				}
				ik++;
			}
		} else {
			out = "";
			if (args.equals("") | args == null)
				throw new IllegalStateException("Event configuration error!");
			else
				out += args;
		}
		System.out.println(eventsArgs);
		return out;
	}

	private String parseRelationArgs(String args) {
		String out = null;
		String argss[] = args.split(":");
		// Relation has to have exactly two arguments
		if (argss.length == 3) {
			out = "";
			out += argss[0] + "\t ";
			// Add arguments and replace unnecessary characters
			out += RELATION_ARG_NAME + "1:"
					+ argss[1].replaceAll("[\\?\\*\\+\\{\\d\\}]", "");
			eventsArgs.put(argss[1].replaceAll("[\\?\\*\\+\\{\\d\\}]", "")
					, RELATION_ARG_NAME);

			out += ", ";
			out += RELATION_ARG_NAME + "2:"
					+ argss[2].replaceAll("[\\?\\*\\+\\{\\d\\}]", "");
			eventsArgs.put(argss[2].replaceAll("[\\?\\*\\+\\{\\d\\}]", "")
					, RELATION_ARG_NAME);

		} else
			throw new IllegalStateException("Relations must have exactly two arguments");
		return out;
	}

	public void writeToFile(String text) {
		try {
			acWriter.write(text);
			acWriter.newLine();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public enum BratTypes {
		ENTITY, EVENT, RELATION, ATTRIBUTE
	}
}