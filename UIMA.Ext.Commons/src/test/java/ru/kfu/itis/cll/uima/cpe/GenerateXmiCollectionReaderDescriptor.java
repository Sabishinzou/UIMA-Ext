/**
 * 
 */
package ru.kfu.itis.cll.uima.cpe;

import static org.uimafit.factory.CollectionReaderFactory.createDescription;

import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.xml.sax.SAXException;

/**
 * @author Rinat Gareev (Kazan Federal University)
 * 
 */
public class GenerateXmiCollectionReaderDescriptor {

	public static void main(String[] args) throws UIMAException, IOException, SAXException {
		String outputPath = "src/main/resources/ru/kfu/itis/cll/uima/cpe/XmiCollectionReader.xml";
		CollectionReaderDescription crDesc = createDescription(
				XmiCollectionReader.class);

		FileOutputStream out = new FileOutputStream(outputPath);
		try {
			crDesc.toXML(out);
		} finally {
			out.close();
		}
	}
}