package edu.cmu.lti.deiis.hw5.answer_ranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.qalab.types.Answer;
import edu.cmu.lti.qalab.types.CandidateAnswer;
import edu.cmu.lti.qalab.types.CandidateSentence;
import edu.cmu.lti.qalab.types.NER;
import edu.cmu.lti.qalab.types.NounPhrase;
import edu.cmu.lti.qalab.types.Question;
import edu.cmu.lti.qalab.types.QuestionAnswerSet;
import edu.cmu.lti.qalab.types.TestDocument;
import edu.cmu.lti.qalab.types.Token;
import edu.cmu.lti.qalab.utils.Utils;

public class AnswerChoiceCandAnsAltSimilarityScorer extends JCasAnnotator_ImplBase {

  int K_CANDIDATES = 5;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    K_CANDIDATES = (Integer) context.getConfigParameterValue("K_CANDIDATES");
  }

  @Override
  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    TestDocument testDoc = Utils.getTestDocumentFromCAS(aJCas);
    // String testDocId = testDoc.getId();
    ArrayList<QuestionAnswerSet> qaSet = Utils.getQuestionAnswerSetFromTestDocCAS(aJCas);

    // traverse each set of Q&A
    for (int i = 0; i < qaSet.size(); i++) {
      // each set of Q and choices
      Question question = qaSet.get(i).getQuestion();
      System.out.println("Question: " + question.getText());
      ArrayList<Answer> choiceList = Utils.fromFSListToCollection(qaSet.get(i).getAnswerList(),
              Answer.class);
      // callie
      for (int ind = choiceList.size() - 1; ind >= 0; ind--) {
        Answer temp = choiceList.get(ind);
        if (temp.getIsDiscard()) {
          choiceList.remove(ind);
        }
      }
      // get candidate sentences of each question
      ArrayList<CandidateSentence> candSentList = Utils.fromFSListToCollection(qaSet.get(i)
              .getCandidateSentenceList(), CandidateSentence.class);

      // focus on top k candidate sentences
      int topK = Math.min(K_CANDIDATES, candSentList.size());
      for (int c = 0; c < topK; c++) {
        // for each sentence
        CandidateSentence candSent = candSentList.get(c);

        // get nouns and NERs in it, now maybe tokens
        ArrayList<NounPhrase> candSentNouns = Utils.fromFSListToCollection(candSent.getSentence()
                .getPhraseList(), NounPhrase.class);
        Set<NounPhrase> candSentTokenSet = new HashSet<NounPhrase>(candSentNouns);
        Map<NounPhrase, Integer> candSentTokenMap = list2map(candSentNouns);

        ArrayList<NER> candSentNouns2 = Utils.fromFSListToCollection(candSent.getSentence()
                .getPhraseList(), NER.class);
        Set<NER> candSentTokenSet2 = new HashSet<NER>(candSentNouns2);
        Map<NER, Integer> candSentTokenMap2 = list2map(candSentNouns2);

        ArrayList<CandidateAnswer> candAnsList = new ArrayList<CandidateAnswer>();
        for (int j = 0; j < choiceList.size(); j++) {

          // compare candidate sentences and choices, based on nouns and NERs, and now maybe tokens
          Answer answer = choiceList.get(j);

          ArrayList<NounPhrase> choiceNouns = Utils.fromFSListToCollection(candSent.getSentence()
                  .getPhraseList(), NounPhrase.class);
          Map<NounPhrase, Integer> choiceTokenMap = list2map(choiceNouns);
          Set<NounPhrase> allTokenSet = new HashSet<NounPhrase>(choiceNouns);
          allTokenSet.addAll(candSentTokenSet);
          double cosSim = computeCosSim(allTokenSet, choiceTokenMap, candSentTokenMap);
          double diceCoeff = computeDiceCoeff(allTokenSet, choiceTokenMap, candSentTokenMap);
          double jCoeff = computeJaccardCoeff(allTokenSet, choiceTokenMap, candSentTokenMap);

          ArrayList<NER> choiceNouns2 = Utils.fromFSListToCollection(candSent.getSentence()
                  .getPhraseList(), NER.class);
          Map<NER, Integer> choiceTokenMap2 = list2map(choiceNouns2);
          Set<NER> allTokenSet2 = new HashSet<NER>(choiceNouns2);
          allTokenSet2.addAll(candSentTokenSet2);
          double cosSim2 = computeCosSim(allTokenSet2, choiceTokenMap2, candSentTokenMap2);
          double diceCoeff2 = computeDiceCoeff(allTokenSet2, choiceTokenMap2, candSentTokenMap2);
          double jCoeff2 = computeJaccardCoeff(allTokenSet2, choiceTokenMap2, candSentTokenMap2);

          // System.out.println(choiceList.get(j).getText() + "\t" + cosSim);
          CandidateAnswer candAnswer = null;
          if (candSent.getCandAnswerList() == null) {
            candAnswer = new CandidateAnswer(aJCas);
          } else {
            candAnswer = Utils.fromFSListToCollection(candSent.getCandAnswerList(),
                    CandidateAnswer.class).get(j);// new
            // CandidateAnswer(aJCas);;

          }
          candAnswer.setText(answer.getText());
          candAnswer.setQId(answer.getQuestionId());
          candAnswer.setChoiceIndex(j);
          candAnswer.setSimilarityScore(diceCoeff + cosSim + jCoeff + diceCoeff2 + cosSim2
                  + jCoeff2);
          candAnsList.add(candAnswer);
        }

        FSList fsCandAnsList = Utils.fromCollectionToFSList(aJCas, candAnsList);
        candSent.setCandAnswerList(fsCandAnsList);
        candSentList.set(c, candSent);

      }

      System.out.println("================================================");
      FSList fsCandSentList = Utils.fromCollectionToFSList(aJCas, candSentList);
      qaSet.get(i).setCandidateSentenceList(fsCandSentList);

    }
    FSList fsQASet = Utils.fromCollectionToFSList(aJCas, qaSet);
    testDoc.setQaList(fsQASet);

  }

  public <T> HashMap<T, Integer> list2map(ArrayList<T> tokens) {
    Map<T, Integer> result = new HashMap<T, Integer>();
    for (T t : tokens) {
      if (result.containsKey(t)) {
        int tmp = result.get(t);
        tmp++;
        result.put(t, tmp);
      } else {
        result.put(t, 1);
      }
    }
    return (HashMap<T, Integer>) result;

  }

  public <T> double computeCosSim(Set<T> allTokenSet, Map<T, Integer> choiceTokenMap,
          Map<T, Integer> candSentTokenMap) {
    double cosSim = 0.0;
    double sqrSumOne = 0.0;
    double sqrSumTwo = 0.0;
    for (T t : allTokenSet) {
      double freqOne = 0.0;
      double freqTwo = 0.0;
      if (choiceTokenMap.containsKey(t)) {
        freqOne = choiceTokenMap.get(t);
      }
      if (candSentTokenMap.containsKey(t)) {
        freqTwo = candSentTokenMap.get(t);
      }
      cosSim += freqOne * freqTwo;
      sqrSumOne += Math.pow(freqOne, 2);
      sqrSumTwo += Math.pow(freqTwo, 2);

    }
    if (cosSim != 0) {
      cosSim /= Math.pow((Math.sqrt(sqrSumOne) * Math.sqrt(sqrSumTwo)), 1); // may adjust this
                                                                            // number later
    }
    return cosSim;

  }

  public <T> double computeDiceCoeff(Set<T> allTokenSet, Map<T, Integer> choiceTokenMap,
          Map<T, Integer> candSentTokenMap) {

    Set<T> listOne = new HashSet<T>();
    Set<T> common = new HashSet<T>();

    for (Entry<T, Integer> entry : choiceTokenMap.entrySet()) {
      listOne.add(entry.getKey());
    }
    for (Entry<T, Integer> entry : candSentTokenMap.entrySet()) {
      if (!listOne.contains(entry.getKey())) {
        common.add(entry.getKey());
      }
    }
    if (choiceTokenMap.size() + candSentTokenMap.size() == 0) {
      return 0.0;
    } else {
      return (common.size()) / (choiceTokenMap.size() + candSentTokenMap.size());
    }

  }

  public <T> double computeJaccardCoeff(Set<T> allTokenSet, Map<T, Integer> choiceTokenMap,
          Map<T, Integer> candSentTokenMap) {

    Set<T> listOne = new HashSet<T>();
    Set<T> common = new HashSet<T>();

    for (Entry<T, Integer> entry : choiceTokenMap.entrySet()) {
      listOne.add(entry.getKey());
    }
    for (Entry<T, Integer> entry : candSentTokenMap.entrySet()) {
      if (!listOne.contains(entry.getKey())) {
        common.add(entry.getKey());
      }
    }
    if (choiceTokenMap.size() + candSentTokenMap.size() == 0) {
      return 0.0;
    } else {
      return (common.size()) / (choiceTokenMap.size() + candSentTokenMap.size() - common.size());
    }

  }
}
