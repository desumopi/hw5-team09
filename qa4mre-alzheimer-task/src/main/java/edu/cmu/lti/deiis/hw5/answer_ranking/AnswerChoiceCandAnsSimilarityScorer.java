package edu.cmu.lti.deiis.hw5.answer_ranking;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

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
import edu.cmu.lti.qalab.utils.Utils;

public class AnswerChoiceCandAnsSimilarityScorer extends JCasAnnotator_ImplBase {

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
      try {
        BufferedWriter bW = new BufferedWriter(new FileWriter("RemovedAnswers.txt", true));
        for (int ind = choiceList.size() - 1; ind >= 0; ind--) {
          Answer temp = choiceList.get(ind);
          Character lastChar = temp.getText().charAt(temp.getText().length() - 1);
          Character plural = new Character('s');
          int stind1 = getFirstSpace(question.getText()) + 1;
          int stind2 = getFirstSpace(question.getText().substring(stind1)) + stind1;
          // System.out.println(stind1 + " - " + stind2 + " of " + question.getText());
          String qWdTwo = question.getText().substring(stind1, stind2);
          Character lastQChar = qWdTwo.charAt(qWdTwo.length() - 1);

          if ("How many".equals(question.getText().substring(0, 8)) && !isNumeric(temp.getText())
                  && !temp.getText().contains("more") && !temp.getText().contains("less")) {

            temp.setIsDiscard(true);
            bW.write("Q: " + question.getText() + "\n");
            bW.write("auto: " + choiceList.get(ind).getText() + "\n");

          } else if ("What are".equals(question.getText().substring(0, 8))
                  && !(lastChar.equals(plural)) && !(temp.getText().contains("and"))) {

            temp.setIsDiscard(true);
            bW.write("Q: " + question.getText() + "\n");
            bW.write("auto: " + choiceList.get(ind).getText() + "\n");

          } else if ("What".equals(question.getText().substring(0, 4))
                  && !(qWdTwo.equals("regulates")) && lastQChar.equals(plural)
                  && qWdTwo.length() > 2 && !(lastChar.equals(plural))
                  && !(temp.getText().contains("and"))) {

            temp.setIsDiscard(true);
            bW.write("Q: " + question.getText() + "\n");
            bW.write("auto: " + choiceList.get(ind).getText() + "\n");

          } else if (question.getText().contains("Which")
                  && question.getText().substring(0, question.getText().length() - 3)
                          .contains("CLU isoform") && !temp.getText().contains("CLU")) {

            temp.setIsDiscard(true);
            bW.write("Q: " + question.getText() + "\n");
            bW.write("auto: " + choiceList.get(ind).getText() + "\n");

          }
          if (temp.getIsDiscard()) {
            bW.write("Q: " + question.getText() + "\n");
            bW.write("hand: " + choiceList.get(ind).getText() + "\n");
            choiceList.remove(ind);
          }
        }
        bW.close();
      } catch (IOException e1) {
        System.out.println("THE FILE DOES NOT EXIST");
        e1.printStackTrace();
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
        ArrayList<NounPhrase> candSentNouns = Utils.fromFSListToCollection(candSent.getSentence()
                .getPhraseList(), NounPhrase.class);
        ArrayList<NER> candSentNers = Utils.fromFSListToCollection(candSent.getSentence()
                .getNerList(), NER.class);

        ArrayList<CandidateAnswer> candAnsList = new ArrayList<CandidateAnswer>();
        for (int j = 0; j < choiceList.size(); j++) {

          // compare candidate sentences and choices, based on nouns and NERs
          Answer answer = choiceList.get(j);
          ArrayList<NounPhrase> choiceNouns = Utils.fromFSListToCollection(
                  answer.getNounPhraseList(), NounPhrase.class);
          ArrayList<NER> choiceNERs = Utils.fromFSListToCollection(answer.getNerList(), NER.class);

          int nnMatch = 0;
          // for each noun in this candidate sentence
          for (int k = 0; k < candSentNouns.size(); k++) {
            // compare it with each element (nouns and NERs) in the choice
            // if there is a match, count++
            for (int l = 0; l < choiceNERs.size(); l++) {
              if (candSentNouns.get(k).getText().contains(choiceNERs.get(l).getText())) {
                nnMatch++;
              }
            }
            for (int l = 0; l < choiceNouns.size(); l++) {
              if (candSentNouns.get(k).getText().contains(choiceNouns.get(l).getText())) {
                nnMatch++;
              }
            }
                       
          }
          


          // Wenyi modified this part
          // removed some error in the baseline code

          // for each NER in this candidate sentence
          // do the same thing as to nouns
          for (int k = 0; k < candSentNers.size(); k++) {
            for (int l = 0; l < choiceNERs.size(); l++) {
              if (candSentNers.get(k).getText().contains(choiceNERs.get(l).getText())) {
                nnMatch++;
              }
            }
            for (int l = 0; l < choiceNouns.size(); l++) {
              if (candSentNers.get(k).getText().contains(choiceNouns.get(l).getText())) {
                nnMatch++;
              }
            }

          }

          
          //napat add score to question/answer direct match
          for (int l = 0; l < choiceNERs.size(); l++) {
            if (question.getText().contains(choiceNERs.get(l).getText())) {
              nnMatch ++;
            }
          }
          for (int l = 0; l < choiceNouns.size(); l++) {
            if (question.getText().contains(choiceNouns.get(l).getText())) {
              nnMatch ++;
            }
          }
         
          System.out.println(choiceList.get(j).getText() + "\t" + nnMatch);
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
          candAnswer.setSimilarityScore(1.0*nnMatch/(answer.getEnd()-answer.getBegin())); 
          // nnMatch is the default similarity score between candidate sentence and choice
          // Wenyi modified it with a normalization using the length of answer.
          // when running "questionRun", the result improves from 02113 to 02213.
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

  public static boolean isNumeric(String str) {
    try {
      double d = Double.parseDouble(str);
      if (d != (int) d) {
        return false;
      }
    } catch (NumberFormatException nfe) {
      return false;
    }
    return true;
  }

  public static int getFirstSpace(String st) {
    Character space = ' ';
    for (int i = 0; i < st.length(); i++) {
      Character tmp = st.charAt(i);
      if (tmp.equals(space)) {
        return i;
      }
    }
    return st.length() - 1;
  }
  
}
