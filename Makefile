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

#executa os 4 terminais em background
run-demo:
	@echo ">>> A iniciar Nave-Mãe..."
	nohup sh -c "make run-nave" > nave.log 2>&1 &

	@echo ">>> A iniciar Rover 1..."
	nohup sh -c "make run-rover1" > rover1.log 2>&1 &

	@echo ">>> A iniciar Rover 2..."
	nohup sh -c "make run-rover2" > rover2.log 2>&1 &

	@echo ">>> A iniciar Rover 3..."
	nohup sh -c "make run-rover3" > rover3.log 2>&1 &

run-test-api:
	@echo "Testando /rovers:"
	curl -s http://localhost:8080/rovers | jq .
	@echo "\nTestando /missoes:"
	curl -s http://localhost:8080/missoes | jq .
	@echo "\nTestando /telemetria/historico:"
	curl -s http://localhost:8080/telemetria/historico | jq .

# Clean and build
all: clean build

