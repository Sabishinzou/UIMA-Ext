package ru.kfu.itis.issst.uima.shaltef

import org.scalatest.FunSuite
import org.apache.uima.cas.CAS
import java.io.InputStream
import org.apache.uima.cas.TypeSystem
import org.uimafit.factory.TypeSystemDescriptionFactory._
import org.apache.uima.util.CasCreationUtils
import java.io.File
import java.io.BufferedInputStream
import java.io.FileInputStream
import org.apache.uima.cas.impl.XmiCasDeserializer
import ru.kfu.cll.uima.segmentation.fstype.Sentence
import org.uimafit.util.CasUtil._
import org.apache.uima.cas.text.AnnotationFS
import ru.kfu.cll.uima.tokenizer.fstype.Token
import org.opencorpora.cas.Word
import ru.kfu.itis.issst.uima.phrrecog.cas.Phrase
import ru.kfu.itis.issst.uima.phrrecog.cas.NounPhrase
import scala.collection.JavaConversions.{ asJavaCollection, iterableAsScalaIterable }
import org.uimafit.util.FSCollectionFactory
import org.apache.uima.jcas.cas.FSArray
import ru.kfu.cll.uima.tokenizer.fstype.W
import org.apache.uima.jcas.JCas
import scala.collection.mutable
import org.apache.uima.cas.impl.XmiCasSerializer
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import org.apache.uima.cas.text.AnnotationIndex

class PhraseIndexTestSuite extends FunSuite {

  private val ts = {
    val tsDesc = createTypeSystemDescription("ru.kfu.itis.issst.uima.phrrecog.ts-phrase-recognizer",
      "ru.kfu.cll.uima.tokenizer.tokenizer-TypeSystem",
      "ru.kfu.cll.uima.segmentation.segmentation-TypeSystem")
    val dumbCas = CasCreationUtils.createCas(tsDesc, null, null)
    dumbCas.getTypeSystem()
  }
  private val sentenceType = ts.getType(classOf[Sentence].getName)

  test("Create phrase index over segment without NPs") {
    val cas = readCAS("src/test/resources/phr-idx/no-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "перенесен"), ts)
    assert(idx.phraseSeq.isEmpty)
    assert(idx.refWordIndex === -1)
  }

  test("Create phrase index over segment with NPs and refWord is not inside an NP") {
    val cas = readCAS("src/test/resources/phr-idx/9-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "перенесен"), ts)
    assert(idx.phraseSeq.toList.map(toTestNP(_)) ===
      TestNP("матч", depWords = "Отборочный" :: Nil,
        depNPs = TestNP("чемпионата", depNPs = TestNP("мира") :: Nil) :: Nil) ::
        TestNP("футболу", "по") ::
        TestNP("года", depWords = "2014" :: Nil) ::
        TestNP("сборными", "между",
          depNPs = TestNP("Ирландии", depWords = "Северной" :: Nil) :: TestNP("России") :: Nil) ::
          TestNP("срок", "на", depWords = "более" :: "поздний" :: Nil) :: Nil)
    assert(idx.refWordIndex == 3)
  }

  test("Create phrase index over segment with NPs and refWord is head of an NP") {
    val cas = readCAS("src/test/resources/phr-idx/9-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "сборными"), ts)
    assert(idx.phraseSeq.toList.map(toTestNP(_)) ===
      TestNP("матч", depWords = "Отборочный" :: Nil,
        depNPs = TestNP("чемпионата", depNPs = TestNP("мира") :: Nil) :: Nil) ::
        TestNP("футболу", "по") ::
        TestNP("года", depWords = "2014" :: Nil) ::
        TestNP("Ирландии", depWords = "Северной" :: Nil) ::
        TestNP("России") ::
        TestNP("срок", "на", depWords = "более" :: "поздний" :: Nil) :: Nil)
    assert(idx.refWordIndex == 2)
  }

  test("Create phrase index over segment with NPs and refWord is head of a sub-NP") {
    val cas = readCAS("src/test/resources/phr-idx/9-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "чемпионата"), ts)
    assert(idx.phraseSeq.toList.map(toTestNP(_)) ===
      TestNP("мира") ::
      TestNP("футболу", "по") ::
      TestNP("года", depWords = "2014" :: Nil) ::
      TestNP("сборными", "между",
        depNPs = TestNP("Ирландии", depWords = "Северной" :: Nil) :: TestNP("России") :: Nil) ::
        TestNP("срок", "на", depWords = "более" :: "поздний" :: Nil) :: Nil)
    assert(idx.refWordIndex == -1)
  }

  test("Create phrase index over segment with NPs and refWord is head of a sub-NP - 2") {
    val cas = readCAS("src/test/resources/phr-idx/9-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "мира"), ts)
    assert(idx.phraseSeq.toList.map(toTestNP(_)) ===
      TestNP("футболу", "по") ::
      TestNP("года", depWords = "2014" :: Nil) ::
      TestNP("сборными", "между",
        depNPs = TestNP("Ирландии", depWords = "Северной" :: Nil) :: TestNP("России") :: Nil) ::
        TestNP("срок", "на", depWords = "более" :: "поздний" :: Nil) :: Nil)
    assert(idx.refWordIndex == -1)
  }

  private def toTestNP(p: Phrase): TestNP = {
    assert(p.isInstanceOf[NounPhrase])
    val np = p.asInstanceOf[NounPhrase]
    val testDepWords =
      if (np.getDependentWords != null)
        FSCollectionFactory.create(np.getDependentWords, classOf[Word]).toList
      else Nil
    TestNP(np.getHead.getCoveredText,
      toTestWord(np.getPreposition()),
      toTestWord(np.getParticle()),
      testDepWords.map(toTestWord(_)),
      if (np.getDependentPhrases != null)
        FSCollectionFactory.create(np.getDependentPhrases, classOf[Phrase]).toList.map(toTestNP(_))
      else Nil)
  }

  test("Create phrase index over segment with NPs and refWord is head of last NP") {
    val cas = readCAS("src/test/resources/phr-idx/9-np.xmi")
    val jCas = cas.getJCas()
    val segment = selectSingle(cas, sentenceType).asInstanceOf[AnnotationFS]
    val idx = new PhraseIndex(segment, selectWord(jCas, "срок"), ts)
    assert(idx.phraseSeq.toList.map(toTestNP(_)) ===
      TestNP("матч", depWords = "Отборочный" :: Nil,
        depNPs = TestNP("чемпионата", depNPs = TestNP("мира") :: Nil) :: Nil) ::
        TestNP("футболу", "по") ::
        TestNP("года", depWords = "2014" :: Nil) ::
        TestNP("сборными", "между",
          depNPs = TestNP("Ирландии", depWords = "Северной" :: Nil) :: TestNP("России") :: Nil) ::
          Nil)
    assert(idx.refWordIndex == 3)
  }

  private def toTestWord(w: Word): String =
    if (w == null) null
    else w.getCoveredText

  private def selectWord(jCas: JCas, wordTxt: String): Word = {
    val wordIdx: Iterable[Word] = jCas.getAnnotationIndex(Word.typeIndexID).asInstanceOf[AnnotationIndex[Word]]
    wordIdx.find(_.getCoveredText == wordTxt) match {
      case Some(w) => w
      case None => throw new IllegalStateException("Can't find word '%s'".format(wordTxt))
    }
  }

  private def readCAS(filePath: String): CAS = readCAS(filePath, ts)

  private def readCAS(filePath: String, ts: TypeSystem): CAS = {
    val file = new File(filePath)
    if (!file.exists()) throw new IllegalArgumentException(
      "File %s does not exist".format(file))
    val xmiIS = new BufferedInputStream(new FileInputStream(file))
    try {
      readCAS(xmiIS, ts)
    } finally {
      xmiIS.close()
    }
  }

  private def readCAS(xmiIS: InputStream, ts: TypeSystem): CAS = {
    val cas = CasCreationUtils.createCas(ts, null, null, null)
    XmiCasDeserializer.deserialize(xmiIS, cas)
    cas
  }
}

private[shaltef] case class TestNP(head: String, prepOpt: String = null, particleOpt: String = null,
  depWords: List[String] = Nil, depNPs: List[TestNP] = Nil)

object PhraseIndexTestSuiteResources extends App {
  private val text = "Отборочный матч чемпионата мира по футболу 2014 года между сборными Северной Ирландии и России перенесен на более поздний срок."
  private val ts = {
    val tsDesc = createTypeSystemDescription("ru.kfu.itis.issst.uima.phrrecog.ts-phrase-recognizer",
      "ru.kfu.cll.uima.tokenizer.tokenizer-TypeSystem",
      "ru.kfu.cll.uima.segmentation.segmentation-TypeSystem")
    val dumbCas = CasCreationUtils.createCas(tsDesc, null, null)
    dumbCas.getTypeSystem()
  }

  private val wordMap = mutable.Map.empty[String, Word]
  private var jCas: JCas = _ // no-np xmi

  {
    getNewCas()
    serialize("src/test/resources/phr-idx/no-np.xmi")
  } // 9-np xmi
  {
    getNewCas()
    val np1 = np("мира")
    val np2 = np("чемпионата", depNPs = np1 :: Nil)
    val np3 = np("матч", depWordIds = "Отборочный" :: Nil, depNPs = np2 :: Nil, index = true)
    val np4 = np("футболу", "по", index = true)
    val np5 = np("года", depWordIds = "2014" :: Nil, index = true)
    val np6 = np("Ирландии", depWordIds = "Северной" :: Nil)
    val np7 = np("России")
    val np8 = np("сборными", "между", depNPs = np6 :: np7 :: Nil, index = true)
    val np9 = np("срок", "на", depWordIds = "более" :: "поздний" :: Nil, index = true)
    serialize("src/test/resources/phr-idx/9-np.xmi")
  }

  private def w(begin: Int, end: Int): Word = w(text.substring(begin, end), begin, end)

  private def w(id: String, begin: Int, end: Int): Word = {
    require(!wordMap.contains(id), "Duplicate id: %s".format(id))
    val token = new W(jCas)
    token.setBegin(begin)
    token.setEnd(end)
    token.addToIndexes()
    val word = new Word(jCas)
    word.setBegin(begin)
    word.setEnd(end)
    word.setToken(token)
    word.addToIndexes()
    wordMap(id) = word
    word
  }

  private def w(id: String): Word =
    if (id == null) null
    else wordMap(id)

  private def np(headId: String, prepId: String = null, particleId: String = null,
    depWordIds: Iterable[String] = Nil, depNPs: Iterable[Phrase] = Nil,
    index: Boolean = false): NounPhrase = {
    val npAnno = new NounPhrase(jCas)
    val head = w(headId)
    npAnno.setBegin(head.getBegin)
    npAnno.setEnd(head.getEnd)
    npAnno.setHead(head)
    npAnno.setParticle(w(particleId))
    npAnno.setPreposition(w(prepId))
    if (!depWordIds.isEmpty)
      npAnno.setDependentWords(FSCollectionFactory.createFSArray(jCas, depWordIds.map(w(_))).asInstanceOf[FSArray])
    if (!depNPs.isEmpty)
      npAnno.setDependentPhrases(FSCollectionFactory.createFSArray(jCas, depNPs).asInstanceOf[FSArray])
    if (index)
      npAnno.addToIndexes()
    npAnno
  }

  private def sent(begin: Int, end: Int): Sentence = {
    val sentAnno = new Sentence(jCas)
    sentAnno.setBegin(begin)
    sentAnno.setEnd(end)
    sentAnno.addToIndexes()
    sentAnno
  }

  private def getNewCas() {
    wordMap.clear()
    jCas = CasCreationUtils.createCas(ts, null, null, null).getJCas();
    jCas.setDocumentText(text)
    w(0, 10)
    w(11, 15)
    w(16, 26)
    w(27, 31)
    w(32, 34)
    w(35, 42)
    w(43, 47)
    w(48, 52)
    w(53, 58)
    w(59, 67)
    w(68, 76)
    w(77, 85)
    w(86, 87)
    w(88, 94)
    w(95, 104)
    w(105, 107)
    w(108, 113)
    w(114, 121)
    w(122, 126)
    sent(0, 127)
  }

  private def serialize(outPath: String) {
    val os = new BufferedOutputStream(new FileOutputStream(outPath))
    try {
      XmiCasSerializer.serialize(jCas.getCas(), null, os, true, null)
    } finally {
      os.close()
    }
  }
}