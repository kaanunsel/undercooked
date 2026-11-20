JAVAC = javac
JAVA = java
JAR = jar
SRC_DIR = src
BUILD_DIR = build
DIST_DIR = dist
JAR_FILE = $(DIST_DIR)/undercooked-server.jar

SOURCES := $(shell find $(SRC_DIR) -name "*.java")

.PHONY: all compile test jar run check-constraints clean

all: compile

compile:
	@mkdir -p $(BUILD_DIR)
	$(JAVAC) --release 21 -d $(BUILD_DIR) $(SOURCES)

test: compile check-constraints
	$(JAVA) -cp $(BUILD_DIR) undercooked.tests.ConcurrencyTestSuite

jar: compile check-constraints
	@mkdir -p $(DIST_DIR)
	$(JAR) cfe $(JAR_FILE) undercooked.Main -C $(BUILD_DIR) .
	@echo "Built $(JAR_FILE)"

run: compile
	$(JAVA) -cp $(BUILD_DIR) undercooked.Main 6789

check-constraints:
	@echo "Checking CMPE 436 hard constraints (zero java.util.concurrent.*)..."
	@if grep -rn "java\.util\.concurrent" $(SRC_DIR); then \
		echo "ERROR: Forbidden java.util.concurrent usage detected!"; \
		exit 1; \
	else \
		echo "OK: Zero java.util.concurrent imports found."; \
	fi

clean:
	rm -rf $(BUILD_DIR) $(DIST_DIR)
