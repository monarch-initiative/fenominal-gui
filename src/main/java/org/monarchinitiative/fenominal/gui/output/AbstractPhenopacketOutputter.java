package org.monarchinitiative.fenominal.gui.output;

import org.monarchinitiative.fenominal.gui.model.AbstractPhenopacketModel;
import org.monarchinitiative.fenominal.gui.model.PhenopacketModel;

public abstract class AbstractPhenopacketOutputter {

    protected final PhenopacketModel phenopacketModel;

    public AbstractPhenopacketOutputter(PhenopacketModel model) {
        this.phenopacketModel = model;
    }
}
