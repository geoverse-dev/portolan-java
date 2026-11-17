.PHONY: help setup test coverage check check-conformance prepare-conformance-adapter check-conformance-read check-conformance-metadata build install parquetry-install parquetry-test clean

MVN ?= mvn
CONFORMANCE_REPOSITORY ?= https://github.com/geoverse-dev/portolan-conformance.git
CONFORMANCE_VERSION ?= v0.1.0
CONFORMANCE ?= uvx --from 'git+$(CONFORMANCE_REPOSITORY)@$(CONFORMANCE_VERSION)' portolan-conformance
CONFORMANCE_READ_REPORT ?= target/portolan-conformance-read.json
CONFORMANCE_METADATA_REPORT ?= target/portolan-conformance-metadata.json

help:
	@printf '%s\n' 'Targets: setup test coverage check check-conformance build install parquetry-install parquetry-test clean'

setup:
	$(MVN) -q -DskipTests test

test:
	$(MVN) test

coverage:
	$(MVN) verify

check:
	$(MVN) verify

check-conformance: check-conformance-read check-conformance-metadata

prepare-conformance-adapter:
	$(MVN) -q test-compile dependency:build-classpath \
		-Dmdep.outputFile=target/conformance-classpath.txt \
		-Dmdep.includeScope=test

check-conformance-read: prepare-conformance-adapter
	$(CONFORMANCE) run \
		--profile read \
		--adapter '$(CURDIR)/scripts/conformance-adapter' \
		--report '$(CONFORMANCE_READ_REPORT)'

check-conformance-metadata: prepare-conformance-adapter
	$(CONFORMANCE) run \
		--profile metadata-validation \
		--adapter '$(CURDIR)/scripts/conformance-adapter' \
		--report '$(CONFORMANCE_METADATA_REPORT)'

build:
	$(MVN) package

install:
	$(MVN) install

parquetry-install:
	test -n "$(PARQUETRY_HOME)"
	cd "$(PARQUETRY_HOME)" && ./mvnw -DskipTests -pl :parquetry-core -am install
	cd "$(PARQUETRY_HOME)" && ./mvnw -Dmaven.test.skip=true -pl :parquetry-stac -am install
	$(MVN) -DskipTests install

parquetry-test:
	$(MVN) -f parquetry-reader/pom.xml clean verify

clean:
	$(MVN) clean
	$(MVN) -f parquetry-reader/pom.xml clean
