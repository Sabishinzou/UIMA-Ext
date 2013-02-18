/**
 *
 */
package ru.kfu.itis.issst.uima.chunker
import org.apache.uima.jcas.JCas
import scala.collection.JavaConversions._
import ru.kfu.cll.uima.segmentation.fstype.Sentence
import org.apache.uima.cas.text.AnnotationFS
import org.uimafit.util.CasUtil
import input.AnnotationSpan
import org.uimafit.component.CasAnnotator_ImplBase
import org.apache.uima.cas.TypeSystem
import org.apache.uima.cas.Type
import org.opencorpora.cas.Word
import ru.kfu.itis.issst.uima.chunker.parsing.NPParsers
import org.apache.uima.cas.CAS
import ru.kfu.itis.issst.uima.chunker.parsing.NP
import scala.util.parsing.input.Reader
import ru.kfu.itis.issst.uima.chunker.cas.Chunk
import org.uimafit.util.FSCollectionFactory
import org.apache.uima.jcas.cas.FSArray

/**
 * @author Rinat Gareev (Kazan Federal University)
 *
 */
class NPRecognizer extends CasAnnotator_ImplBase with NPParsers {
  private var wordType: Type = _

  override def typeSystemInit(ts: TypeSystem) {
    wordType = ts.getType(classOf[Word].getName)
  }

  override def process(cas: CAS): Unit =
    process(cas.getJCas())

  private def process(jCas: JCas) =
    jCas.getAnnotationIndex(Sentence.typeIndexID).foreach(processSpan(_))

  private def processSpan(span: AnnotationFS) {
    val spanWords = CasUtil.selectCovered(span.getCAS(), wordType, span)
      .asInstanceOf[java.util.List[Word]].toList
    parseFrom(new AnnotationSpan(spanWords).reader)
  }

  private def parseFrom(reader: Reader[Word]): Unit =
    if (!reader.atEnd)
      np(reader) match {
        case Success(np, rest) => {
          makeNPAnnotation(np)
          parseFrom(rest)
        }
        case Failure(_, _) =>
          // start from next anno
          parseFrom(reader.rest)
      }

  private def makeNPAnnotation(np: NP) {
    val head = np.noun
    val jCas = head.getCAS().getJCas()

    val chunk = new Chunk(jCas)
    chunk.setBegin(head.getBegin())
    chunk.setEnd(head.getEnd())

    chunk.setHead(head)

    val depsFsArray = new FSArray(jCas, np.deps.size)
    FSCollectionFactory.fillArrayFS(depsFsArray, np.deps)
    chunk.setDependents(depsFsArray)
    chunk.addToIndexes()
  }
}