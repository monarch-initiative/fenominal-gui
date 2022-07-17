package org.monarchinitiative.fenominal.gui.model;

import org.monarchinitiative.fenominal.gui.io.PhenopacketImporter;

import java.time.LocalDate;
import java.util.Optional;

public class PhenopacketNoAgeModel extends AbstractPhenopacketModel {
    public PhenopacketNoAgeModel(PhenopacketImporter phenopacketImp) {
        super(phenopacketImp);
    }

    public PhenopacketNoAgeModel(String id, Sex sex) {
        super(id, sex);
    }

    @Override
    public String toString() {
        return String.format("[PhenopacketNoAgeModel] terms:%d.",terms.size());
    }

    @Override
    public Optional<LocalDate> getBirthdate() {
        return Optional.empty();
    }

    @Override
    public void setBirthdate(LocalDate birthdate) {
        throw new UnsupportedOperationException("Cannot set age of PhenopacketNoAgeModel");
    }



}
