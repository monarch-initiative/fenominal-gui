package org.monarchinitiative.fenominal.gui.model;

import org.monarchinitiative.fenominal.model.MinedSentence;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AllTextHitsModel implements TextMiningResultsModel{

    private final Collection<MinedSentence> minedSentences;



    public AllTextHitsModel(Collection<MinedSentence> sentences) {
        minedSentences = sentences;
    }

    public Collection<MinedSentence> getMinedSentences() {
        return minedSentences;
    }

    @Override
    public void addHpoFeatures(List<FenominalTerm> terms) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public int casesMined() {
        return 0;
    }

    @Override
    public int getTermCount() {
        return 0;
    }

    @Override
    public Sex sex() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public Map<String, String> getModelData() {
        return null;
    }

    @Override
    public void setModelDataItem(String k, String v) {

    }

    @Override
    public String getInitialFileName() {
        return "fenominal.tsv";
    }

    @Override
    public Optional<LocalDate> getBirthdate() {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public void setBirthdate(LocalDate birthdate) {
        throw new UnsupportedOperationException("Not supported");
    }

    @Override
    public boolean isChanged() {
        return false;
    }

    @Override
    public void resetChanged() {

    }
}
