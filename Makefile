.PHONY: build clean run-rover run-nave 

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

