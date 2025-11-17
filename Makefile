.PHONY: help build clean run-rover run-nave run-gc-console run-gc-streamlit test jar all py-install py-console py-streamlit py-help

# Default target
help:
    @echo "Available targets:"
    @echo ""
    @echo "Java:"
    @echo "  make build            - Compile the Java project"
    @echo "  make clean            - Clean build artifacts"
    @echo "  make run-rover        - Run RoverApp (ARGS=\"<id> <x> <y> <porta>\")"
    @echo "  make run-rover1       - Run Rover 1 (porta 5001)"
    @echo "  make run-rover2       - Run Rover 2 (porta 5002)"
    @echo "  make run-rover3       - Run Rover 3 (porta 5003)"
    @echo "  make run-nave         - Run the NaveMaeApp"
    @echo "  make all              - Clean and build"
    @echo ""


# Build the project
build:
    gradle build

# Clean build artifacts
clean:
    gradle clean

# Run RoverApp (uso: make run-rover ARGS="<id> <x> <y> <porta>")
run-rover:
    gradle runRoverApp --args="$(ARGS)"

# Run multiple rovers
run-rover1:
    gradle runRoverApp --args="1 0.0 0.0 5001"

run-rover2:
    gradle runRoverApp --args="2 10.0 0.0 5002"

run-rover3:
    gradle runRoverApp --args="3 0.0 10.0 5001"

# Run NaveMaeApp
run-nave:
    gradle runNaveMaeApp

# Clean and build
all: clean build
