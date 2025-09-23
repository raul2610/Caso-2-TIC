# Caso-2-TIC
Simulador de memoria virtual con paginación por demanda (Java, sin librerías externas).

## Grupo 19
- Raúl Sebastián Ruiz Aragón: 202321332
- Sergio Soler:

## Para ejecutar en consola
- javac -d out src\*.java
- Generar: java -cp out App generate config\sample_config.txt
- Simular: java -cp out App simulate --frames 8 --processes 2 --input input --out output

Notas
- --frames debe ser múltiplo de --processes.
- Se crean input\proc<i>.txt, output\stats.csv y un log output\run_*.log.

## Estructura
- `src/` código Java (`App.java`, `Core.java`, `IOKit.java`).
- `config/` configs para generación (incluye `sample_config.txt`).
- `input/` entradas generadas (`proc<i>.txt`).
- `output/` métricas y logs.