package org.monarchinitiative.fenominal.gui.output;

import org.monarchinitiative.fenominal.gui.model.AllTextHitsModel;
import org.monarchinitiative.fenominal.model.MinedSentence;
import org.monarchinitiative.phenol.ontology.data.Ontology;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Optional;

public class AllTextHitsOutputter implements PhenoOutputter{

    private final String originalFileName;

    private final Collection<MinedSentence> minedSentences;

    private final Ontology ontology;
    public AllTextHitsOutputter(AllTextHitsModel model, Ontology ontology) {
        originalFileName = model.getInitialFileName();
        minedSentences = model.getMinedSentences();
        this.ontology = ontology;
    }

    @Override
    public void output(Writer writer) throws IOException {
        writer.write("# " + originalFileName + "\n");
        writer.write("HPO.id\tlabel\tmatch\texcluded?\tsentence\n");
        for (var sentence : minedSentences) {
            String sen = sentence.getText();
            for (var term : sentence.getMinedTerms()) {
                String excluded = term.isPresent() ? "false" : "true";
                String matching = String.valueOf(term.getMatchingString());
                String tid = term.getTermIdAsString();
                Optional<String> opt = ontology.getTermLabel(term.getTermId());
                String label = opt.orElse("n/a");
                String line = String.format("%s\t%s\t%s\t%s\t%s\n",
                        tid,
                        label,
                        matching,
                        excluded,
                        sen);
                writer.write(line);
            }
        }
    }
}
