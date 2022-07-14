package org.monarchinitiative.fenominal.gui.questionnaire.phenoitem;

import org.monarchinitiative.phenol.ontology.data.Term;

public interface PhenoAnswer {

    Term term();
    boolean observed();
    boolean excluded();
    boolean unknown();
    String question();

}
