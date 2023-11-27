module org.monarchinitiative.fenominal.gui {
    requires spring.beans;
    requires spring.context;
    requires javafx.graphics;
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;
    requires java.sql;
    requires org.monarchinitiative.phenol.core;
    requires org.monarchinitiative.phenol.io;
    requires org.slf4j;
    requires org.monarchinitiative.fenominal.core;
    requires jdk.jsobject;
    requires com.google.protobuf;
    requires com.google.protobuf.util;
    requires org.phenopackets.schema;
    requires org.phenopackets.phenopackettools.builder;
    requires json.simple;
    requires spring.boot.autoconfigure;
    requires org.controlsfx.controls;
    requires spring.boot;
    requires spring.core;

    exports org.monarchinitiative.fenominal.gui to javafx.graphics, spring.beans, spring.context;
    opens org.monarchinitiative.fenominal.gui to spring.core;
    opens org.monarchinitiative.fenominal.gui.config to spring.core;
    exports org.monarchinitiative.fenominal.gui.config to spring.beans, spring.context;
    exports org.monarchinitiative.fenominal.gui.controller to spring.beans;
    opens org.monarchinitiative.fenominal.gui.controller to spring.core, javafx.fxml;
    opens org.monarchinitiative.fenominal.gui.hpotextminingwidget to javafx.fxml;

}