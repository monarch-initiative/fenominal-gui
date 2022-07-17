package org.monarchinitiative.fenominal.gui.output;

import org.monarchinitiative.fenominal.gui.model.PhenopacketModel;

import java.io.IOException;
import java.io.Writer;

public interface PhenoOutputter {

    void output(Writer writer) throws IOException;
}
