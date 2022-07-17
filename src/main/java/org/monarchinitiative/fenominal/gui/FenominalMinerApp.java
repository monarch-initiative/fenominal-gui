package org.monarchinitiative.fenominal.gui;

//import org.monarchinitiative.fenominal.core.corenlp.MappedSentencePart;
//import org.monarchinitiative.fenominal.core.lexical.LexicalResources;
//import org.monarchinitiative.fenominal.core.textmapper.ClinicalTextMapper;
//import org.monarchinitiative.fenominal.core.SimpleMinedTerm;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class FenominalMinerApp  {
    private static final Logger LOGGER = LoggerFactory.getLogger(FenominalMinerApp.class);

   /* private static final TermId PHENOTYPIC_ABNORMALITY = TermId.of("HP:0000118");
    private final ClinicalTextMapper mapper;

    private final Ontology ontology;


    private final Ontology phenotypicAbnormalityOntology;

    public FenominalMinerApp(Ontology ontology) {
        this.ontology = ontology;
        this.phenotypicAbnormalityOntology = ontology.subOntology(PHENOTYPIC_ABNORMALITY);
        LexicalResources lexicalResources = new LexicalResources();
        this.mapper = new ClinicalTextMapper(ontology, lexicalResources);
    }


    @Override
    public Collection<MinedTerm> doMining(final String query) {
        List<MappedSentencePart> mappedSentenceParts = mapper.mapText(query, false);
        LOGGER.trace("Retrieved {} mapped sentence parts ", mappedSentenceParts.size());
        return mappedSentenceParts.stream().map(SimpleMinedTerm::fromMappedSentencePart).collect(Collectors.toList());
    }

    @Override
    public Collection<MinedTerm> doFuzzyMining(final String query) {
        List<MappedSentencePart> mappedSentenceParts = mapper.mapText(query, true);
        LOGGER.trace("(Fuzzy match) Retrieved {} mapped sentence parts ", mappedSentenceParts.size());
        return mappedSentenceParts.stream().map(SimpleMinedTerm::fromMappedSentencePart).collect(Collectors.toList());
    }


    public Ontology getHpo() {
        return this.ontology;
    }

    public Ontology getPhenotypicAbnormalityOntology() {
        return this.phenotypicAbnormalityOntology;
    }
*/

}
