package edu.cmu.lti.deiis.hw5.annotators;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

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

public class AnswerDiscardAnnotator extends JCasAnnotator_ImplBase{

  @Override
  public void initialize(UimaContext context)
      throws ResourceInitializationException {
    super.initialize(context);
    
  }

  @Override
  public void process(JCas aJCas) throws AnalysisEngineProcessException {
    ArrayList<QuestionAnswerSet> qaSet = Utils.getQuestionAnswerSetFromTestDocCAS(aJCas);

    // traverse each set of Q&A
    for (int i = 0; i < qaSet.size(); i++) {
      // each set of Q and choices
      Question question = qaSet.get(i).getQuestion();
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
    }
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

