.PHONY: help setup test coverage check build install parquetry-install parquetry-test clean

MVN ?= mvn

help:
	@printf '%s\n' 'Targets: setup test coverage check build install parquetry-install parquetry-test clean'

setup:
	$(MVN) -q -DskipTests test

test:
	$(MVN) test

coverage:
	$(MVN) verify

check:
	$(MVN) verify

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
