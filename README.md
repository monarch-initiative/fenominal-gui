# fenominal-gui
Graphical User Interface for Fenominal


## Building fenominal-gui

While we are in the process of adding fenominal to maven central, building the GUI requires a local 
maven install of fenominal (the version in the pom file must match the locally install version of
fenominal).

```bazaar
git clone https://github.com/monarch-initiative/fenominal.git
cd fenominal
mvn install
```


## Running GUI app

Assuming the build completed successfully, the GUI is ran by the following command:

```bash
java -jar fenominal-gui/target/fenominal-gui-${project.version}.jar
```

Please refer to the documentation to learn more about various GUI features.

