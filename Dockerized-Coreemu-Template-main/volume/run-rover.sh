#!/bin/bash
# Script para executar o Rover com argumentos por defeito se não fornecidos
# Uso: ./run-rover.sh [id] [x] [y] [ipNave] [portaTcpNave] [portaUdp]
# Valores por defeito: id=1, x=0.0, y=0.0, ipNave=10.0.0.1, portaTcpNave=5001, portaUdp=9010+id

ID=${1:-1}
X=${2:-0.0}
Y=${3:-0.0}
IP_NAVE=${4:-10.0.0.1}
PORTA_TCP=${5:-5001}
PORTA_UDP=${6:-$((9010 + ID))}

java -jar Rover.jar "$ID" "$X" "$Y" "$IP_NAVE" "$PORTA_TCP" "$PORTA_UDP"
