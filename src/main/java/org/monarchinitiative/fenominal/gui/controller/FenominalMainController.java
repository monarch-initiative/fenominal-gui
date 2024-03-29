package org.monarchinitiative.fenominal.gui.controller;

import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.MapValueFactory;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontPosture;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.controlsfx.dialog.CommandLinksDialog;
import org.monarchinitiative.fenominal.core.FenominalRunTimeException;
import org.monarchinitiative.fenominal.core.TermMiner;
import org.monarchinitiative.fenominal.gui.OptionalResources;
import org.monarchinitiative.fenominal.gui.StartupTask;
import org.monarchinitiative.fenominal.gui.config.ApplicationProperties;
import org.monarchinitiative.fenominal.gui.guitools.*;
import org.monarchinitiative.fenominal.gui.hpotextminingwidget.HpoTextMining;
import org.monarchinitiative.fenominal.gui.hpotextminingwidget.PhenotypeTerm;
import org.monarchinitiative.fenominal.gui.io.HpoMenuDownloader;
import org.monarchinitiative.fenominal.gui.io.PhenopacketImporter;
import org.monarchinitiative.fenominal.gui.model.*;
import org.monarchinitiative.fenominal.gui.output.*;
import org.monarchinitiative.fenominal.model.MinedSentence;
import org.monarchinitiative.phenol.ontology.data.Ontology;
import org.monarchinitiative.phenol.ontology.data.TermId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static org.monarchinitiative.fenominal.gui.config.FenominalConfig.*;
import static org.monarchinitiative.fenominal.gui.guitools.MiningTask.*;

@SuppressWarnings({"unchecked", "rawtypes"})
@Component
public class FenominalMainController {
    private static final Logger LOGGER = LoggerFactory.getLogger(FenominalMainController.class);
    @FXML
    public Button parseButton;

    @FXML
    public Button outputButton;
    @FXML
    public Button setupButton;
    @FXML
    private Button previwButton;
    @FXML
    public Label hpoReadyLabel;
    @FXML
    public TableView metaDataTableView;
    /**
     * We hide the table until the first bits of data are entered.
     */
    private final BooleanProperty tableHidden;


    private final ExecutorService executor;

    private final OptionalResources optionalResources;

    private final static TermId PHENOTYPIC_ABNORMALITY = TermId.of("HP:0000118");

    private final Properties pgProperties;

    private final File appHomeDirectory;

    private MiningTask miningTaskType = UNINITIALIZED;

    private TextMiningResultsModel model = null;

    @Autowired
    ApplicationProperties applicationProperties;


    @Autowired
    public FenominalMainController(OptionalResources optionalResources,
                                   ExecutorService executorService,
                                   Properties pgProperties,
                                   @Qualifier("appHomeDir") File appHomeDir) {
        this.optionalResources = optionalResources;
        this.executor = executorService;
        this.pgProperties = pgProperties;
        this.appHomeDirectory = appHomeDir;
        this.tableHidden = new SimpleBooleanProperty(true);
    }


    public void initialize() {
        // run the initialization task on a separate thread
        StartupTask task = new StartupTask(optionalResources, pgProperties, this.appHomeDirectory);
        this.hpoReadyLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(e -> this.hpoReadyLabel.textProperty().unbind());
        this.executor.submit(task);
        // only enable analyze if Ontology downloaded (enabled property watches
        this.setupButton.disableProperty().bind(optionalResources.ontologyProperty().isNull());
        this.parseButton.setDisable(true);
        this.previwButton.setDisable(true);
        this.outputButton.setDisable(true);
        Platform.runLater(() ->{
            Scene scene = this.parseButton.getScene();
            scene.getWindow().setOnCloseRequest(ev -> {
                if (!shutdown()) {
                    ev.consume();
                }
            });
        });
        // set up table view
        TableColumn<Map, String> itemColumn = new TableColumn<>("item");
        itemColumn.setCellValueFactory(new MapValueFactory<>("item"));
        TableColumn<Map, String> valueColumn = new TableColumn<>("value");
        valueColumn.setCellValueFactory(new MapValueFactory<>("value"));
        this.metaDataTableView.getColumns().add(itemColumn);
        this.metaDataTableView.getColumns().add(valueColumn);
        this.metaDataTableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        itemColumn.setMaxWidth(1f * Integer.MAX_VALUE * 25);
        valueColumn.setMaxWidth(1f * Integer.MAX_VALUE * 75);
        // Ordered map of data for the table
        Map<String, String> mp = new LinkedHashMap<>();
        String versionInfo = getHpoVersion();
        mp.put(HPO_VERSION_KEY, versionInfo);
        populateTableWithData(mp);
    }

    private BooleanProperty tableHiddenProperty() {
        return this.tableHidden;
    }

    private void populateTableWithData(Map<String, String> data) {
        this.metaDataTableView.getItems().clear();
        ObservableList<Map<String, Object>> itemMap = FXCollections.observableArrayList();
        for (Map.Entry<String, String> e : data.entrySet()) {
            Map<String, Object> item = new HashMap<>();
            item.put("item", e.getKey());
            item.put("value", e.getValue());
            itemMap.add(item);
        }
        this.metaDataTableView.getItems().addAll(itemMap);
        this.tableHiddenProperty().set(false);
    }

    /**
     * Calculate the age of the patient as a Java Period object
     *
     * @param birthdate     birth date
     * @param encounterDate data at which the phenotype was first observed
     * @return a Period object representing the patient age
     */
    private Period getAge(LocalDate birthdate, LocalDate encounterDate) {
        return Period.between(birthdate, encounterDate);
    }

    @FXML
    private void parseButtonPressed(ActionEvent e) {
        LOGGER.trace("Parse button pressed");
        Ontology ontology = this.optionalResources.getOntology();
        if (ontology == null) {
            PopUps.showInfoMessage("Need to set location to hp.json ontology file first! (See edit menu)", "Error");
            return;
        }
        Ontology phenotypicAbnSubontology = ontology.subOntology(PHENOTYPIC_ABNORMALITY);
        LocalDate encounterDate = null;
        String isoAge = null;
        if (this.miningTaskType == PHENOPACKET) {
            PhenopacketModel pmodel = (PhenopacketModel) this.model;
            Optional<LocalDate> bdOpt = pmodel.getBirthdate();
            if (bdOpt.isEmpty()) {
                PopUps.showInfoMessage("Error", "Cannot enter phenotype info without birthdate");
                return; // should never happen
            }
            DatePickerDialog dialog = DatePickerDialog.getEncounterDate(bdOpt.get(), pmodel.getEncounterDates(bdOpt.get()));
            encounterDate = dialog.showDatePickerDialog();
        } else if (this.miningTaskType == PHENOPACKET_BY_AGE) {
            PhenopacketByAgeModel pAgeModel = (PhenopacketByAgeModel) this.model;
            AgePickerDialog agePickerDialog = new AgePickerDialog(pAgeModel.getEncounterAges());
            isoAge = agePickerDialog.showAgePickerDialog();
        }
        TermMiner exactMiner = TermMiner.defaultNonFuzzyMapper(ontology);
        HpoTextMining hpoTextMining = HpoTextMining.builder()
                .withExecutorService(executor)
                .withOntology(phenotypicAbnSubontology)
                .withTermMiner(exactMiner)
                .build();
        // get reference to primary stage
        Window w = this.parseButton.getScene().getWindow();
        // show the text mining analysis dialog in the new stage/window
        Stage secondary = new Stage();
        secondary.initOwner(w);
        secondary.setTitle("HPO text mining analysis");
        secondary.setScene(new Scene(hpoTextMining.getMainParent()));
        secondary.showAndWait();
        Set<PhenotypeTerm> approved = hpoTextMining.getApprovedTerms();
        switch (miningTaskType) {
            case PHENOPACKET -> parsePhenopacket(encounterDate, approved);
            case PHENOPACKET_BY_AGE -> parsePhenopacketByAge(isoAge, approved);
            case PHENOPACKET_NO_AGE -> parsePhenopacketNoAge(approved);
            default -> {
                PopUps.showInfoMessage("Error, mining task not implemented yet", "Error");
                return;
            }
        }
        updateTable();
        // if we get here, we have data that could be output
        this.previwButton.setDisable(false);
        this.outputButton.setDisable(false);
        e.consume();
    }

    /**
     * Get all text hits from a file with lines. Show the hits and their position in
     * the sentences.
     */
    private void getAllTextHits() {
        Ontology ontology = optionalResources.getOntology();
        if (ontology == null) {
            PopUps.showInfoMessage("Error", "Could not retrieve ontology");
            return;
        }

        TermMiner miner = TermMiner.defaultNonFuzzyMapper(ontology);
        LOGGER.info("Choose file to get all text hits");
        FileChooser fileChooser = new FileChooser();
        Stage stage = (Stage) this.outputButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) {
            PopUps.showInfoMessage("Could not retrieve file for parsing, please try again.", "Error");
            return;
        }
        String query = "";

        try {
            // Note that using a buffered reader replaces unmappable characters
            // while using the Files API led to encoding errors being thrown
            var br = new BufferedReader(new InputStreamReader(new FileInputStream(file.getAbsolutePath()),"utf-8"));
            String line;
            List<String> lines = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                LOGGER.info(line.substring(10));
                lines.add(line);
            }
            LOGGER.info("Added {} lines for parsing", lines.size());
            query = String.join(" ", lines);

        } catch (IOException e) {
            PopUps.showException("Error", "Could not read input file",
                    e.getMessage(), e);
            return;
        }
        Collection<MinedSentence> sentences = miner.mineSentences(query);
        this.model = new AllTextHitsModel(sentences);
        this.parseButton.setDisable(true);
        this.previwButton.setDisable(false);
        this.outputButton.setDisable(false);
        this.miningTaskType = ALL_TEXT_HITS;
    }


    private void parsePhenopacket(LocalDate encounterDate, Set<PhenotypeTerm> approved) {
        PhenopacketModel pmodel = (PhenopacketModel) this.model;
        Optional<LocalDate> bdateOpt = pmodel.getBirthdate();
        if (bdateOpt.isEmpty()) {
            PopUps.showInfoMessage("Error", "Cannot add phenotypes without initializing birthdate");
            return;
        }
        Period age = getAge(bdateOpt.get(), encounterDate);
        List<FenominalTerm> approvedTerms = approved.stream()
                .map(pterm -> FenominalTerm.fromMainPhenotypeTermWithAge(pterm, age))
                .sorted()
                .collect(Collectors.toList());
        pmodel.addHpoFeatures(approvedTerms);
        int encountersSoFar = pmodel.casesMined();
        this.parseButton.setText(String.format("Mine encounter %d", encountersSoFar + 1));
    }

    private void parsePhenopacketByAge(String isoAge, Set<PhenotypeTerm> approved) {
        Period agePeriod = Period.parse(isoAge);
        List<FenominalTerm> approvedTerms = approved.stream()
                .map(pterm -> FenominalTerm.fromMainPhenotypeTermWithIsoAge(pterm, agePeriod))
                .sorted()
                .collect(Collectors.toList());
        model.addHpoFeatures(approvedTerms);
        int encountersSoFar = model.casesMined();
        this.parseButton.setText(String.format("Mine encounter %d", encountersSoFar + 1));
    }

    private void parsePhenopacketNoAge(Set<PhenotypeTerm> approved) {
        List<FenominalTerm> approvedTerms = approved.stream()
                .map(FenominalTerm::fromMainPhenotypeTermNoAge)
                .sorted()
                .collect(Collectors.toList());
        model.addHpoFeatures(approvedTerms);
    }

    private void updateTable() {
        if (this.model == null) {
            LOGGER.error("Attempt to update table while model was null");
            return;
        }
        if (model.casesMined() > 0) {
            model.setModelDataItem("patients (n)", String.valueOf(model.casesMined()));
        }
        model.setModelDataItem("terms curated (n)", String.valueOf(model.getTermCount()));
        populateTableWithData(model.getModelData());
    }

    /**
     * @return Version of HPO being used for curation, corresponding to the data-version attribute in hp.json
     */
    private String getHpoVersion() {
        Ontology hpo = optionalResources.getOntology();
        if (hpo != null) {
            return hpo.getMetaInfo().getOrDefault("data-version", "n/a");
        } else {
            return "not initialized";
        }
    }


    /**
     * Set up parsing for a single individual over one or more time points with the goal of outputting a
     * GA4GH phenopacket with one or multiple time points
     */
    private void initPhenopacket() {
        this.parseButton.setDisable(false);
        this.parseButton.setText("Mine time point 1");
        this.miningTaskType = PHENOPACKET;
        Optional<PatientSexIdAndBirthdate> opt = BirthDatePickerDialog.showDatePickerDialogSIB();
        if (opt.isEmpty()) {
            PopUps.showInfoMessage("Error", "Could not retrieve id/sex/birthdate");
            return;
        }
        PatientSexIdAndBirthdate psidb = opt.get();
        String id = psidb.id();
        Sex sex = psidb.sex();
        LocalDate birthdate = psidb.birthdate();
        LOGGER.info("Retrieved id {} and sex {} and birthdate {}", id, sex, birthdate);
        this.model = new PhenopacketModel(id, sex);
        model.setModelDataItem(HPO_VERSION_KEY, getHpoVersion());
        model.setModelDataItem(PATIENT_ID_KEY, id);
        model.setModelDataItem(N_CURATED_KEY, "0");
        model.setBirthdate(birthdate);
        populateTableWithData(model.getModelData());
    }


    /**
     * Initialize a phenopacket using ISO 8601 age strings, e.g., P3Y2M214D for 3 years, 2 months and 14 days.
     * This dialog will get the age for the first encounter.
     */
    private void initPhenopacketWithManualAge() {
        this.parseButton.setDisable(false);
        Optional<PatientSexAndId> opt = BirthDatePickerDialog.showDatePickerDialogSI();
        if (opt.isEmpty()) {
            PopUps.showInfoMessage("Error", "Could not initialize Phenopacket");
            return;
        }
        PatientSexAndId psid = opt.get();
        this.parseButton.setText("Mine encounter 1");
        this.miningTaskType = PHENOPACKET_BY_AGE;
        this.model = new PhenopacketByAgeModel(psid.id(), psid.sex());
        model.setModelDataItem(HPO_VERSION_KEY, getHpoVersion());
        model.setModelDataItem(N_CURATED_KEY, "0");
        populateTableWithData(model.getModelData());
    }


    private void initPhenopacketNoAge() {
        this.parseButton.setDisable(false);
        Optional<PatientSexAndId> opt = BirthDatePickerDialog.showDatePickerDialogSI();
        if (opt.isEmpty()) {
            PopUps.showInfoMessage("Error", "Could not initialize Phenopacket");
            return;
        }
        PatientSexAndId psid = opt.get();
        this.parseButton.setText("Mine encounter");
        this.miningTaskType = PHENOPACKET_NO_AGE;
        this.model = new PhenopacketNoAgeModel(psid.id(), psid.sex());
        model.setModelDataItem(HPO_VERSION_KEY, getHpoVersion());
        model.setModelDataItem(N_CURATED_KEY, "0");
        populateTableWithData(model.getModelData());
    }


    @FXML
    private void getStarted(ActionEvent e) {
        if (! cleanBeforeNewCase()) {
            LOGGER.warn("Not clean before new case, returning");
            return;
        }
        var phenopacketByBirthDate = new CommandLinksDialog.CommandLinksButtonType("Phenopacket", "Enter data about one individual, multiple time points", false);
        var phenopacketByIso8601Age = new CommandLinksDialog.CommandLinksButtonType("Phenopacket (by age at encounter)", "Enter data about one individual, multiple ages", false);
        var phenopacketNoAge = new CommandLinksDialog.CommandLinksButtonType("Phenopacket (one encounter, no age)", "Enter data about one individual, no age data", false);
        var updatePhenopacket = new CommandLinksDialog.CommandLinksButtonType("Update Existing Phenopacket", "Update data in phenopacket", false);
        var allTextHits = new CommandLinksDialog.CommandLinksButtonType("Show all HPO terms and context", "Show detailed concept recognition results", false);
        var cancel = new CommandLinksDialog.CommandLinksButtonType("Cancel", "Go back and do not delete current work", false);
        CommandLinksDialog dialog = new CommandLinksDialog(phenopacketByBirthDate, phenopacketByIso8601Age, phenopacketNoAge, updatePhenopacket, allTextHits, cancel);
        dialog.setTitle("Get started");
        dialog.setHeaderText("Select type of curation");
        dialog.setContentText("Fenominal HPO biocuration to create GA4GH Phenopackets");
        Optional<ButtonType> opt = dialog.showAndWait();
        if (opt.isPresent()) {
            ButtonType btype = opt.get();
            switch (btype.getText()) {
                case "Phenopacket" -> initPhenopacket();
                case "Phenopacket (by age at encounter)" -> initPhenopacketWithManualAge();
                case "Phenopacket (one encounter, no age)"-> initPhenopacketNoAge();
                case "Update Existing Phenopacket" -> updatePhenopacket(e);
                case "Show all HPO terms and context" -> getAllTextHits();
                case "Cancel" -> LOGGER.trace("Canceled operation");
            }
        }
        String biocurator = this.pgProperties.getProperty(BIOCURATOR_ID_PROPERTY);
        if (biocurator != null && model != null) {
            this.model.setModelDataItem(BIOCURATOR_ID_PROPERTY, biocurator);
        }
        e.consume();
    }


    @FXML
    private void importHpJson(ActionEvent e) {
        String fname = this.appHomeDirectory.getAbsolutePath() + File.separator + OptionalResources.DEFAULT_HPO_FILE_NAME;
        HpoMenuDownloader downloader = new HpoMenuDownloader();
        try {
            downloader.downloadHpo(fname);
            pgProperties.setProperty(OptionalResources.ONTOLOGY_PATH_PROPERTY, fname);
        } catch (FenominalRunTimeException ex) {
            ex.printStackTrace();
        }
    }

    @FXML
    private void quitApplication(ActionEvent e) {
        if (shutdown()) {
            Platform.exit();
        }
        e.consume();
    }

    @FXML
    public void outputButtonPressed(ActionEvent actionEvent) {
        actionEvent.consume();
        String initialFilename = model.getInitialFileName();
        LOGGER.info("Saving data with initial file name {}", initialFilename);
        FileChooser fileChooser = new FileChooser();
        Stage stage = (Stage) this.outputButton.getScene().getWindow();
        fileChooser.setInitialFileName(initialFilename);
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) {
            PopUps.showInfoMessage("Could not retrieve output file name, please try again.", "Error");
            return;
        }
        LOGGER.info("Retrieved file for saving: {}", file.getAbsolutePath());
        try (Writer writer = new BufferedWriter(new FileWriter(file))) {
            PhenoOutputter phenoOutputter;
            switch (this.miningTaskType) {
                case PHENOPACKET -> phenoOutputter = new PhenopacketJsonOutputter((PhenopacketModel) this.model);
                case PHENOPACKET_BY_AGE -> phenoOutputter = new PhenopacketByAgeJsonOutputter((PhenopacketByAgeModel) this.model);
                case PHENOPACKET_NO_AGE -> phenoOutputter = new PhenopacketNoAgeJsonOutputter((PhenopacketNoAgeModel) this.model);
                case ALL_TEXT_HITS ->  {
                    Ontology ontology = optionalResources.getOntology();
                    if (ontology == null) {
                        LOGGER.info("Ontology null, skipping generation of all text hits file");
                        return;
                    }
                    phenoOutputter = new AllTextHitsOutputter((AllTextHitsModel)this.model, ontology);
                }
                default -> phenoOutputter = new ErrorOutputter();
            }
            phenoOutputter.output(writer);
        } catch (IOException e) {
            PopUps.showInfoMessage("Could not write to file: " + e.getMessage(), "IO Error");
        }
        this.model.resetChanged(); // we have now saved all unsaved data if we get here.
    }

    @FXML
    public void previewOutput(ActionEvent e) {
        PhenoOutputter phenoOutputter;
        LOGGER.info("preview output");
        Writer writer = new StringWriter();
        Ontology ontology = optionalResources.getOntology();
        phenoOutputter = switch (this.miningTaskType) {
            case PHENOPACKET -> new PhenopacketJsonOutputter((PhenopacketModel) this.model);
            case PHENOPACKET_BY_AGE -> new PhenopacketByAgeJsonOutputter((PhenopacketByAgeModel) this.model);
            case PHENOPACKET_NO_AGE -> new PhenopacketNoAgeJsonOutputter((PhenopacketNoAgeModel) this.model);
            case ALL_TEXT_HITS -> new AllTextHitsOutputter((AllTextHitsModel) this.model, ontology);
            default -> new ErrorOutputter();
        };
        try {
            phenoOutputter.output(writer);
            writer.close();
        } catch (IOException ioe) {
            System.err.println(ioe.getMessage());
        }
        Text text1 = new Text(writer.toString());
        text1.setFill(Color.BLUE);
        text1.setFont(Font.font("Helvetica", FontPosture.REGULAR, 14));
        TextFlow textFlow = new TextFlow(text1);
        ScrollPane spane = new ScrollPane(textFlow);
        Stage stage = new Stage();
        Scene previewOutputScene = new Scene(spane);
        stage.setScene(previewOutputScene);
        stage.setHeight(750);
        stage.showAndWait();
        e.consume();
    }


    @FXML
    void setBiocuratorMenuItemClicked(ActionEvent event) {
        String biocurator = PopUps.getStringFromUser("Biocurator ID",
                "e.g., HPO:rrabbit", "Enter your biocurator ID:");
        if (biocurator != null) {
            this.pgProperties.setProperty(BIOCURATOR_ID_PROPERTY, biocurator);
            PopUps.showInfoMessage(String.format("Biocurator ID set to \n\"%s\"",
                    biocurator), "Success");
        } else {
            PopUps.showInfoMessage("Biocurator ID not set.",
                    "Information");
        }
        event.consume();
    }


    public void openAboutDialog(ActionEvent e) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Fenominal");
        alert.setHeaderText(null);
        String fenomimalVersion = applicationProperties.getApplicationVersion();
        alert.setContentText(String.format("Version %s", fenomimalVersion));
        alert.showAndWait();
        e.consume();
    }

    /**
     * Loads a pre-existing Phenopacket and populates the model
     *
     * @param phenopacketImp Importer object
     */
    private void loadPhenopacket(PhenopacketImporter phenopacketImp) {
        this.parseButton.setDisable(false);
        this.parseButton.setText("Mine time point 1");
        this.miningTaskType = PHENOPACKET;

        this.model = new PhenopacketModel(phenopacketImp);
        Optional<LocalDate> opt = BirthDatePickerDialog.showDatePickerDialogBirthDate(phenopacketImp);
        if (opt.isEmpty()) {
            PopUps.showInfoMessage("Error", "Could not load phenopacket");
            return;
        }
        LocalDate birthdate = opt.get();
        model.setBirthdate(birthdate);
        updateModelForPhenopacketUpdate(phenopacketImp.getId());
        populateTableWithData(model.getModelData());
    }

    /**
     * Loads a prexisting Phenopacket and populates the model
     *
     * @param phenopacketImp Importer object
     */
    private void loadPhenopacketWithManualAge(PhenopacketImporter phenopacketImp) {
        this.parseButton.setDisable(false);
        this.parseButton.setText("Mine time point 1");
        this.miningTaskType = PHENOPACKET;

        this.model = new PhenopacketByAgeModel(phenopacketImp);
        updateModelForPhenopacketUpdate(phenopacketImp.getId());
        populateTableWithData(model.getModelData());
    }

    /**
     * Loads a prexisting Phenopacket and populates the model
     *
     * @param phenopacketImp Importer object
     */
    private void loadPhenopacketNoAge(PhenopacketImporter phenopacketImp) {
        this.parseButton.setDisable(false);
        this.parseButton.setText("Mine");
        this.miningTaskType = PHENOPACKET_NO_AGE;

        this.model = new PhenopacketNoAgeModel(phenopacketImp);
        updateModelForPhenopacketUpdate(phenopacketImp.getId());
        populateTableWithData(model.getModelData());
    }


    public void updateModelForPhenopacketUpdate(String phenopacketID) {
        model.setModelDataItem(HPO_VERSION_KEY, getHpoVersion());
        model.setModelDataItem(PATIENT_ID_KEY, phenopacketID);
        model.setModelDataItem(UPDATE_KEY, "true");
        int termCount = model.getTermCount();
        model.setModelDataItem(TERM_COUNT_KEY, String.valueOf(termCount));
        String biocurator = this.pgProperties.getProperty(BIOCURATOR_ID_PROPERTY);
        if (biocurator != null) {
            this.model.setModelDataItem(BIOCURATOR_ID_PROPERTY, biocurator);
        }
    }


    public void updatePhenopacket(ActionEvent actionEvent) {
        var phenopacketByBirthDate = new CommandLinksDialog.CommandLinksButtonType("Phenopacket", "Enter age via brithdate/encounter date", false);
        var phenopacketByIso8601Age = new CommandLinksDialog.CommandLinksButtonType("Phenopacket (by age at encounter)", "Enter dage directly", false);
        var phenopacketNoAge = new CommandLinksDialog.CommandLinksButtonType("Phenopacket (one encounter, no age)", "Enter data about one individual, no age data", false);
        var cancel = new CommandLinksDialog.CommandLinksButtonType("Cancel", "Cancel", false);
        CommandLinksDialog dialog = new CommandLinksDialog(phenopacketByBirthDate, phenopacketByIso8601Age, phenopacketNoAge, cancel);
        dialog.setTitle("Update Phenopacket");
        dialog.setHeaderText("Select type of curation");

        dialog.setContentText("Select a phenopacket file for updating.");
        Optional<ButtonType> opt = dialog.showAndWait();
        if (opt.isPresent()) {
            Optional<PhenopacketImporter> optpp = loadPhenopacketFromFile();
            if (optpp.isEmpty()) {
                PopUps.showInfoMessage("Error", "Could not load phenopacket file");
                return;
            }
            ButtonType btype = opt.get();
            switch (btype.getText()) {
                case "Phenopacket" -> {
                    loadPhenopacket(optpp.get());
                    this.miningTaskType = PHENOPACKET;
                }
                case "Phenopacket (by age at encounter)" -> {
                    loadPhenopacketWithManualAge(optpp.get());
                    this.miningTaskType = PHENOPACKET_BY_AGE;
                }
                case "Phenopacket (one encounter, no age)" -> {
                    loadPhenopacketNoAge(optpp.get());
                    this.miningTaskType = PHENOPACKET_NO_AGE;
                }
                default -> { return; }
            }
        }
        actionEvent.consume();
    }


    private Optional<PhenopacketImporter> loadPhenopacketFromFile() {
        FileChooser fileChooser = new FileChooser();
        // limit to *.json
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter("JSON files (*.json)", "*.json");
        fileChooser.getExtensionFilters().add(extFilter);
        Stage stage = (Stage) this.outputButton.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file == null) {
            return Optional.empty();
        }
        Ontology ontology = optionalResources.getOntology();
        if (ontology == null) {
            PopUps.showInfoMessage("Error", "Cannot import Phenopacket before initialized HPO.");
            return Optional.empty();
        }
        PhenopacketImporter ppacket = PhenopacketImporter.fromJson(file, ontology);
        return Optional.of(ppacket);
    }

    public boolean cleanBeforeNewCase() {
        if (model == null) {
            return true;
        } else if (miningTaskType == ALL_TEXT_HITS) {
            // 'clean' is not relevant for this mining type
            return true;
        }
        if (model.isChanged()) {
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Warning - Unsaved Data");
            dialog.setHeaderText("Discard changes?");
            dialog.setContentText("Cancel revokes the new case request");
            DialogPane dialogPane = dialog.getDialogPane();
            dialogPane.getButtonTypes().addAll(ButtonType.YES, ButtonType.CANCEL);
            Optional<ButtonType> opt = dialog.showAndWait();
            if (opt.isEmpty()) return false;
            ButtonType btype = opt.get();
            if (btype.equals(ButtonType.CANCEL)) return false;
            return  (btype.equals(ButtonType.YES));
        }
        // if we get here, somethinbg probably went wrong, let's cancel the quit request
        return false;
    }

    public boolean shutdown() {
        LOGGER.trace("shutdown");
        if (model == null) {
            LOGGER.trace("shutdown with model null");
            return true; // in this case, the use has not started anything and just wants out
        } else if (model.isChanged()) {
            LOGGER.trace("shutdown with dirty data, checking with user");
            Dialog<ButtonType> dialog = new Dialog<>();
            dialog.setTitle("Warning - Unsaved Data");
            dialog.setHeaderText("Discard changes?");
            dialog.setContentText("Cancel revokes the exit request");
            DialogPane dialogPane = dialog.getDialogPane();
            dialogPane.getButtonTypes().addAll(ButtonType.YES, ButtonType.CANCEL);
            Optional<ButtonType> opt = dialog.showAndWait();
            if (opt.isEmpty()) return false;
            ButtonType btype = opt.get();
            if (btype.equals(ButtonType.CANCEL)) return false;
            return  (btype.equals(ButtonType.YES));
        } else if (miningTaskType == ALL_TEXT_HITS) {
            LOGGER.trace("shutdown with all text hits model");
            return true;
        } else {
            PopUps.showInfoMessage("Unexpected error shutting down", "error");
            return true;
        }
    }
}
