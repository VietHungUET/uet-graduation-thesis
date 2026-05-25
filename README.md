# USE Model Validator

This project provides a custom Model Validator plugin for USE.

The plugin supports model finding for UML/OCL specifications: given a `.use`
model and a `.properties` search-space configuration, it tries to find an object
model that satisfies the class model and its OCL invariants.

This repository contains a modified USE 7.1.0 build and a customized validator
with Sequence support.

## Requirements

- JDK 17
- Maven 3.x

## Build

Install the bundled Kodkod dependency once:

```sh
cd validator
./init.sh
```

On Windows:

```bat
cd validator
init.cmd
```

Then build the whole project from the repository root:

```sh
cd ..
mvn clean install
```

For a faster build without running tests:

```sh
mvn clean install -DskipTests
```

The USE distribution is generated at:

```text
use-main/use-assembly/target/use-7.1.0.zip
```

The latest validator jar is included automatically in `lib/plugins`.

## Run

Windows:

```bat
use-main\use-assembly\target\use-7.1.0\use-7.1.0\bin\use.bat
```

Linux/macOS:

```sh
./use-main/use-assembly/target/use-7.1.0/use-7.1.0/bin/use
```

Open a `.use` model, then run the Model Validator from the `Plugins` menu.

## Sample Models

Models:

```text
validator/test2/use
```

Validator properties:

```text
validator/test2/properties
```

Example:

```text
validator/test2/use/Hotel.use
validator/test2/properties/Hotel1.properties
```
