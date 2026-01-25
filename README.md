## Requirements

- USE version 3.1.0 or greater is required for this plugin

## Installation

- Install the kodkod jar file to the local repository:
  * using the external maven:
    ```sh
    cd use-validator
    ./init.sh           # (or init.cmd on Windows)
    ```
  * Or using Maven inside Eclipse IDE, input the Goals text-box with the following value: 
    ```sh
        install:install-file -Dfile=${project.basedir}/lib/kodkod.jar \
                         -DgroupId=kodkod \
                         -DartifactId=kodkod \
                         -Dversion=1.0 \
                         -Dpackaging=jar 

- Copy the use-validator.jar into the lib/plugins directory of USE (or using a soft link on linux). For a more detailed, read the USE README.

## Usage

- Open USE specification.
  
- In the main menu select "Plugins" -> "Model Validator"
  
- Select the options to configure the validation ...
