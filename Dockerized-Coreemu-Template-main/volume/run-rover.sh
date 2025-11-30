#!/bin/bash
# Script para executar o Rover com argumentos por defeito se não fornecidos
# Uso: ./run-rover.sh [id] [x] [y] [porta]
# Valores por defeito: id=1, x=0.0, y=0.0, porta=5001

ID=${1:-1}
X=${2:-0.0}
Y=${3:-0.0}
PORTA=${4:-5001}

java -jar Rover.jar "$ID" "$X" "$Y" "$PORTA"
