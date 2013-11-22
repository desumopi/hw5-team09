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

        // get nouns and NERs in it
        ArrayList<Token> candSentTokens = Utils.fromFSListToCollection(candSent.getSentence()
                .getTokenList(), Token.class);
        Set<Token> candSentTokenSet = new HashSet<Token>(candSentTokens);
        Map<Token, Integer> candSentTokenMap = list2map(candSentTokens);

        ArrayList<CandidateAnswer> candAnsList = new ArrayList<CandidateAnswer>();
        for (int j = 0; j < choiceList.size(); j++) {

          // compare candidate sentences and choices, based on nouns and NERs
          Answer answer = choiceList.get(j);

          ArrayList<Token> choiceTokens = Utils.fromFSListToCollection(answer.getTokenList(),
                  Token.class);
          Map<Token, Integer> choiceTokenMap = list2map(candSentTokens);
          Set<Token> allTokenSet = new HashSet<Token>(choiceTokens);
          allTokenSet.addAll(candSentTokenSet);
          double cosSim = computeCosSim(allTokenSet, choiceTokenMap, candSentTokenMap);
          double diceCoeff = computeDiceCoeff(allTokenSet, choiceTokenMap, candSentTokenMap);
          double jCoeff = computeJaccardCoeff(allTokenSet, choiceTokenMap, candSentTokenMap);
          System.out.println(choiceList.get(j).getText() + "\t" + cosSim);
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
          candAnswer.setSimilarityScore(diceCoeff + cosSim + jCoeff);
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

  public HashMap<Token, Integer> list2map(ArrayList<Token> tokens) {
    Map<Token, Integer> result = new HashMap<Token, Integer>();
    for (Token t : tokens) {
      if (result.containsKey(t)) {
        int tmp = result.get(t);
        tmp++;
        result.put(t, tmp);
      } else {
        result.put(t, 1);
      }
    }
    return (HashMap<Token, Integer>) result;

  }

  public double computeCosSim(Set<Token> allTokenSet, Map<Token, Integer> choiceTokenMap,
          Map<Token, Integer> candSentTokenMap) {
    double cosSim = 0.0;
    double sqrSumOne = 0.0;
    double sqrSumTwo = 0.0;
    for (Token t : allTokenSet) {
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

  public double computeDiceCoeff(Set<Token> allTokenSet, Map<Token, Integer> choiceTokenMap,
          Map<Token, Integer> candSentTokenMap) {

    Set<Token> listOne = new HashSet<Token>();
    Set<Token> common = new HashSet<Token>();

    for (Entry<Token, Integer> entry : choiceTokenMap.entrySet()) {
      listOne.add(entry.getKey());
    }
    for (Entry<Token, Integer> entry : candSentTokenMap.entrySet()) {
      if (!listOne.contains(entry.getKey())) {
        common.add(entry.getKey());
      }
    }
    return (common.size()) / (choiceTokenMap.size() + candSentTokenMap.size());

  }

  public double computeJaccardCoeff(Set<Token> allTokenSet, Map<Token, Integer> choiceTokenMap,
          Map<Token, Integer> candSentTokenMap) {

    Set<Token> listOne = new HashSet<Token>();
    Set<Token> common = new HashSet<Token>();

    for (Entry<Token, Integer> entry : choiceTokenMap.entrySet()) {
      listOne.add(entry.getKey());
    }
    for (Entry<Token, Integer> entry : candSentTokenMap.entrySet()) {
      if (!listOne.contains(entry.getKey())) {
        common.add(entry.getKey());
      }
    }
    return (common.size()) / (choiceTokenMap.size() + candSentTokenMap.size() - common.size());

  }
}
