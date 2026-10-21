# m6-lab-b Makefile.
# Default `make build / make test` uses only JUnit + the stubs that
# ship with the lab -- no network, no API key required.
#
# `make langchain4j` additionally downloads langchain4j (+ transitive
# deps) into libs/langchain4j/ via Maven.  Needed only when you plug a
# real embedder / LLM into the pipeline (see README.md).

JUNIT_URL := https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.10.0/junit-platform-console-standalone-1.10.0.jar
JUNIT_JAR := libs/junit.jar

LC4J_DIR  := libs/langchain4j
LC4J_MARK := $(LC4J_DIR)/.ok

SRCS  := $(shell find src -name '*.java' 2>/dev/null)
TESTS := $(shell find test -name '*.java' 2>/dev/null)

# When langchain4j is installed, include every jar under libs/langchain4j
# in the compile and run classpath; otherwise we build against the stubs.
LC4J_CP := $(shell [ -d $(LC4J_DIR) ] && find $(LC4J_DIR) -name '*.jar' 2>/dev/null | paste -sd ':' -)
CP      := $(JUNIT_JAR)$(if $(LC4J_CP),:$(LC4J_CP),):build

.PHONY: deps build test clean langchain4j

deps: $(JUNIT_JAR)

$(JUNIT_JAR):
	@mkdir -p libs
	@curl -sSL -o $@ $(JUNIT_URL)

build: deps
	@mkdir -p build
	javac --release 17 -d build -cp $(CP) $(SRCS) $(TESTS)

test: build
	java -cp $(CP) -jar $(JUNIT_JAR) --class-path build --scan-class-path

# One-shot: pull langchain4j and its transitive deps into libs/langchain4j/.
# Requires Maven on the PATH.  Re-run if you bump the version in pom.xml.
langchain4j: $(LC4J_MARK)

$(LC4J_MARK): pom.xml
	@command -v mvn >/dev/null 2>&1 || { \
	  echo "ERROR: Maven not found on PATH.  Install Maven (brew install maven, apt install maven) and re-try."; \
	  exit 1; }
	@mkdir -p $(LC4J_DIR)
	mvn -f pom.xml -q dependency:copy-dependencies \
	    -DoutputDirectory=$(LC4J_DIR) \
	    -DincludeScope=runtime
	@touch $(LC4J_MARK)
	@echo ""
	@echo "langchain4j installed under $(LC4J_DIR)/."
	@echo "Re-run 'make build' and the classpath will pick it up."

clean:
	rm -rf build libs
