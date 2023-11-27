package org.monarchinitiative.fenominal.gui.output;

import java.io.IOException;
import java.io.Writer;

public interface PhenoOutputter {

    void output(Writer writer) throws IOException;

    default String getPyphetoolsCode() {
        throw new RuntimeException("copyPyphetoolsToClipboard Not implemented yet");
    }
}
