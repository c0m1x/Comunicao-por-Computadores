.PHONY: build clean run-rover run-nave 

build:
	gradle build

clean:
	gradle clean

demo-kill:
	@echo "A matar todos os processos Java anteriores..."
	- pkill -f "api.gc.GroundControlApp"
	- pkill -f "nave.NaveMaeApp"
	- pkill -f "rover.RoverApp"
	@echo "OK"

# Run RoverApp (uso: make run-rover ARGS="<id> <posX> <posY> <ipNave> <portaTcpNave> <portaUdp>")
run-rover:
	gradle runRoverApp --args="$(ARGS)"

run-rover1:
	gradle runRoverApp --args="1 0.0 0.0 127.0.0.1 5001 9011"

run-rover2:
	gradle runRoverApp --args="2 10.0 0.0 127.0.0.1 5001 9012"

run-rover3:
	gradle runRoverApp --args="3 0.0 10.0 127.0.0.1 5001 9013"

# Run NaveMaeApp
run-nave:
	gradle runNaveMaeApp

run-ground-control:
	gradle runGroundControlApp

run-demo:
	(gradle runNaveMaeApp &) ; \
	sleep 1 ; \
	(gradle runGroundControlApp &) ; \
	sleep 1 ; \
	(gradle runRoverApp --args="1 0.0 0.0 127.0.0.1 5001 9011" &)

run-full:
	make demo-kill
	(gradle runNaveMaeApp > logs_nave.txt &) ; \
	sleep 1 ; \
	(gradle runGroundControlApp > logs_gc.txt &) ; \
	sleep 1 ; \
	(gradle runRoverApp --args="1 0.0 0.0 127.0.0.1 5001 9011" > logs_r1.txt &) ; \
	(gradle runRoverApp --args="2 5.0 3.0 127.0.0.1 5001 9012" > logs_r2.txt &) ; \
	echo "Sistema iniciado: Nave + GC + Rovers"

#make build
#java -cp build/libs/CC.jar api.gc.GroundControlApp

run-deploy:
	gradle jarNaveMae jarRover jarGroundControl
	cp build/libs/NaveMae.jar Dockerized-Coreemu-Template-main/volume/
	cp build/libs/Rover.jar Dockerized-Coreemu-Template-main/volume/
	cp build/libs/GroundControl.jar Dockerized-Coreemu-Template-main/volume/
	@echo "JARs deployed to Dockerized-Coreemu-Template-main/volume/"

run-test-api:
	@echo "Testing /rovers:" ; \
	curl -s http://localhost:8080/rovers | jq .
	
# Clean and build
all: clean build



