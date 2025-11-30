#!/bin/bash
# Script para executar o Rover com argumentos por defeito se não fornecidos
# Uso: ./run-rover.sh [id] [x] [y] [porta] [ipNave]
# Valores por defeito: id=1, x=0.0, y=0.0, porta=5001, ipNave=10.0.0.1

ID=${1:-1}
X=${2:-0.0}
Y=${3:-0.0}
PORTA=${4:-5001}
IP_NAVE=${5:-10.0.0.1}

java -jar Rover.jar "$ID" "$X" "$Y" "$PORTA" "$IP_NAVE"
