package org.monarchinitiative.fenominal.gui.output;

//import com.fasterxml.jackson.annotation.JsonFormat;

import com.google.protobuf.util.JsonFormat;


import com.google.protobuf.Timestamp;
import org.monarchinitiative.fenominal.gui.model.FenominalTerm;
import org.monarchinitiative.fenominal.gui.model.PhenopacketByAgeModel;
import org.monarchinitiative.fenominal.gui.model.SimpleUpdate;
import org.monarchinitiative.phenol.ontology.data.Term;
import org.phenopackets.phenopackettools.builder.PhenopacketBuilder;
import org.phenopackets.phenopackettools.builder.builders.MetaDataBuilder;
import org.phenopackets.phenopackettools.builder.builders.PhenotypicFeatureBuilder;
import org.phenopackets.phenopackettools.builder.builders.Resources;
import org.phenopackets.phenopackettools.builder.builders.TimeElements;
import org.phenopackets.schema.v2.Phenopacket;
import org.phenopackets.schema.v2.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.monarchinitiative.fenominal.gui.config.FenominalConfig.BIOCURATOR_ID_PROPERTY;
import static org.monarchinitiative.fenominal.gui.config.FenominalConfig.HPO_VERSION_KEY;

public record PhenopacketByAgeJsonOutputter(PhenopacketByAgeModel phenopacketModel)
        implements PhenoOutputter {
    private final static Logger LOGGER = LoggerFactory.getLogger(PhenopacketByAgeJsonOutputter.class);

    private MetaData getMetaData() {
        Map<String,String> data = phenopacketModel.getModelData();
        String biocurator = data.getOrDefault(BIOCURATOR_ID_PROPERTY, "n/a");
        String hpoVersion = data.getOrDefault(HPO_VERSION_KEY, "n/a");
        if (phenopacketModel.isUpdateOfExistingPhenopacket()) {
            Timestamp createdOn = phenopacketModel.getCreatedOn();
            String createdBy = phenopacketModel.getCreatedBy();
            List<SimpleUpdate> simpleUpdates = phenopacketModel.getUpdates();
            // Now add an update for the current curation task
            // Take the current biocurator (default to createdBy, but this should always work
            String currentBiocurator = phenopacketModel.getModelData().getOrDefault(BIOCURATOR_ID_PROPERTY,createdBy);
            Instant now = Instant.now();
            Timestamp timestamp =
                    Timestamp.newBuilder().setSeconds(now.getEpochSecond())
                            .setNanos(now.getNano()).build();
            simpleUpdates.add(new SimpleUpdate(currentBiocurator, timestamp));
            List<Update> updates = new ArrayList<>();
            for (var supd : simpleUpdates) {
                Update upd = Update.newBuilder().setTimestamp(supd.createdOn()).setUpdatedBy(supd.createdBy()).build();
                updates.add(upd);
            }
            return MetaData.newBuilder()
                    .setCreated(createdOn)
                    .setCreatedBy(createdBy)
                    .addAllUpdates(updates)
                    .addResources(Resources.hpoVersion(hpoVersion))
                    .build();
        } else {
            return MetaDataBuilder
                    .builder(LocalDate.now().toString(), biocurator)
                    .addResource(Resources.hpoVersion(hpoVersion))
                    .build();
        }
    }

    Phenopacket getPhenopacket() {
        MetaData meta = getMetaData();
        PhenopacketBuilder builder = PhenopacketBuilder.create(generatePhenopacketId(), meta);
        org.phenopackets.schema.v2.core.Sex sx = switch (phenopacketModel.sex()) {
            case MALE -> org.phenopackets.schema.v2.core.Sex.MALE;
            case FEMALE -> org.phenopackets.schema.v2.core.Sex.FEMALE;
            case OTHER_SEX -> org.phenopackets.schema.v2.core.Sex.OTHER_SEX;
            default -> org.phenopackets.schema.v2.core.Sex.UNKNOWN_SEX;
        };
        Individual subject = Individual.newBuilder().setId(phenopacketModel.getId()).setSex(sx).build();
        for (FenominalTerm fterm : phenopacketModel.getTerms()) {
            Term term = fterm.getTerm();
            PhenotypicFeature pf;
            if (fterm.isObserved() && fterm.hasAge()) {
                pf = PhenotypicFeatureBuilder
                        .builder(term.id().getValue(), term.getName())
                        .onset(TimeElements.age(fterm.getIso8601Age()))
                        .build();
            } else if (fterm.hasAge()) {
                pf = PhenotypicFeatureBuilder
                        .builder(term.id().getValue(), term.getName())
                        .onset(TimeElements.age(fterm.getIso8601Age()))
                        .excluded()
                        .build();
            } else if (fterm.isObserved()) {
                pf = PhenotypicFeatureBuilder
                        .builder(term.id().getValue(), term.getName())
                        .build();
            } else {
                pf = PhenotypicFeatureBuilder
                        .builder(term.id().getValue(), term.getName())
                        .excluded()
                        .build();
            }
            builder.addPhenotypicFeature(pf); // add feature, one at a time
        }
        builder.individual(subject);
        return builder.build();
    }



    @Override
    public void output(Writer writer) throws IOException {
        LOGGER.info("Output by age model with {} terms.", phenopacketModel.getTermCount());
        Map<String, String> data = phenopacketModel.getModelData();
        Phenopacket phenopacket = getPhenopacket();
        String json = JsonFormat.printer().print(phenopacket);
        writer.write(json);
    }

    private String generatePhenopacketId() {
        String date = LocalDate.now().toString();
        return date + "-fenominal";
    }

    /**
     * Ths goal of this method is to create Python code that can be used in a pyphetools notebook
     * to create a phenopacket in that framework.
     * @return pyphetools code as a String
     */
    public String getPyphetoolsCode() {
        Phenopacket phenopacket = getPhenopacket();
        StringBuilder sb = new StringBuilder();
        Individual individual = phenopacket.getSubject();
        String individual_id = individual.getId().replace(" ", "_");
        sb.append("pfeatures = []\n");
        for (var pf : phenopacket.getPhenotypicFeaturesList()) {
            var oclass = pf.getType();
            String hpo_id = oclass.getId();
            String hpo_label = oclass.getLabel();
            String observed = pf.getExcluded() ?  "False" : "True";
            String age_of_onset = null;
            if (pf.hasOnset()) {
                var onset = pf.getOnset();
                if (onset.hasAge()) {
                    age_of_onset = onset.getAge().getIso8601Duration();
                }
            }
            String hpTerm;
            if (age_of_onset != null) {
                hpTerm = String.format("HpTerm(hpo_id=\"%s\", label=\"%s\", observed=\"%s\", onset=\"%s\")",
                        hpo_id, hpo_label, observed, age_of_onset);
            } else {
                hpTerm = String.format("HpTerm(hpo_id=\"%s\", label=\"%s\", observed=\"%s\")",
                        hpo_id, hpo_label, observed);
            }
            sb.append("pfeatures.append(").append(hpTerm).append(")\n");
        }

        var phenopacket_sex = individual.getSex();
        sb.append("individual_").append(individual_id).
                append(" = Individual(individual_id=\"").
                append(individual.getId()).
                append("\"");
        if (phenopacket_sex != Sex.UNKNOWN_SEX) {
            sb.append(", sex=\"").append(phenopacket_sex).append("\"");
        }
        if (individual.hasTimeAtLastEncounter() &&
                individual.getTimeAtLastEncounter().hasAge()) {
            var age = individual.getTimeAtLastEncounter().getAge().getIso8601Duration();
            sb.append(", age = ").append(age).append("\n");
        }
        sb.append(", hpo_terms=pfeatures)\n");
        return sb.toString();
    }
}
