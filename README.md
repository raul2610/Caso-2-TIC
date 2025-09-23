# Caso-2-TIC
Simulador de memoria virtual con paginación por demanda.

## Grupo 19
- Raúl Sebastián Ruiz Aragón: 202321332
- Sergio Soler

## Cómo ejecutar
Compilar: `javac -d out src\*.java`
    Solo compila este comando cuando se abre desde otro computador sin carpeta out, cambie el src, configs, entradas o parametros de linea de comandos.

### Opción 1
`java -cp out App generate config\sample_config.txt`
Puede ver el resultado como archivo o con `Get-Content input\proc0.txt -TotalCount 12`

### Opción 2
`java -cp out App simulate --frames 8 --processes 2 --input input --out output`
Imprime métricas por proceso y escribe `output\stats.csv` y `output\run_*.log`.

## Explicación
- `javac -d out ...` compila los `.java` y coloca los `.class` en la carpeta `out`.
- `java -cp out App ...` ejecuta indicando que el classpath es `out`.
- La carpeta `out` se crea/recrea automáticamente al compilar. Está ignorada en git (`.gitignore`).

## Generar entradas
- Si desea generar `input\proc<i>.txt` a partir de una configuración: `java -cp out App generate config\sample_config.txt`. (Misma opcion 1)
- Si ya tiene `input\proc<i>.txt`, puede saltar este paso y ejecutar directamente la simulación.

## Notas
- `--frames` debe ser múltiplo de `--processes`.
- Los archivos `proc<i>.txt` deben existir y corresponder a la cantidad de procesos indicada.
