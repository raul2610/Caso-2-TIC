import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class IOKit {
    private IOKit() {
        // Utilitario de entrada y salida.
    }

    public static final class Configuracion {
        public final int tamanoPagina;
        public final int numeroProcesos;
        public final List<int[]> tamanosMatrices;

        private Configuracion(int tamanoPagina, int numeroProcesos, List<int[]> tamanosMatrices) {
            this.tamanoPagina = tamanoPagina;
            this.numeroProcesos = numeroProcesos;
            this.tamanosMatrices = Collections.unmodifiableList(tamanosMatrices);
        }

        public static Configuracion desdeArchivo(Path ruta) throws IOException {
            List<String> lineas = Files.readAllLines(ruta, StandardCharsets.UTF_8);
            Integer tamanoPagina = null;
            Integer numeroProcesos = null;
            List<int[]> tamanos = new ArrayList<>();

            for (String linea : lineas) {
                if (linea == null) {
                    continue;
                }
                String limpia = linea.trim();
                if (limpia.isEmpty()) {
                    continue;
                }
                if (limpia.startsWith("TP=")) {
                    tamanoPagina = Integer.parseInt(limpia.substring(3).trim());
                } else if (limpia.startsWith("NPROC=")) {
                    numeroProcesos = Integer.parseInt(limpia.substring(6).trim());
                } else if (limpia.startsWith("TAMS=")) {
                    String especificaciones = limpia.substring(5).trim();
                    if (!especificaciones.isEmpty()) {
                        String[] partes = especificaciones.split(",");
                        for (String parte : partes) {
                            String[] dimensiones = parte.trim().split("x");
                            if (dimensiones.length != 2) {
                                throw new IOException("Formato invalido en tamanos de matrices: " + parte);
                            }
                            int filas = Integer.parseInt(dimensiones[0]);
                            int columnas = Integer.parseInt(dimensiones[1]);
                            tamanos.add(new int[]{filas, columnas});
                        }
                    }
                }
            }

            if (tamanoPagina == null || numeroProcesos == null) {
                throw new IOException("Configuracion incompleta: se requieren TP y NPROC");
            }
            if (tamanos.size() != numeroProcesos) {
                throw new IOException("Cantidad de tamanos de matrices no coincide con NPROC");
            }
            return new Configuracion(tamanoPagina, numeroProcesos, tamanos);
        }
    }

    public static final class EntradaSalidaProcesos {
        private EntradaSalidaProcesos() {
        }

        public static void escribirArchivosProcesos(List<Core.Proceso> procesos, Path directorioEntrada) throws IOException {
            if (!Files.exists(directorioEntrada)) {
                Files.createDirectories(directorioEntrada);
            }
            for (Core.Proceso proceso : procesos) {
                Path archivo = directorioEntrada.resolve("proc" + proceso.pid + ".txt");
                try (BufferedWriter escritor = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
                    escritor.write("TP=" + proceso.tamanoPagina);
                    escritor.newLine();
                    escritor.write("NF=" + proceso.numeroFilas);
                    escritor.newLine();
                    escritor.write("NC=" + proceso.numeroColumnas);
                    escritor.newLine();
                    escritor.write("NR=" + proceso.totalReferencias);
                    escritor.newLine();
                    escritor.write("NP=" + proceso.totalPaginas);
                    escritor.newLine();
                    for (Core.Referencia referencia : proceso.referencias) {
                        escritor.write(formatearReferencia(referencia));
                        escritor.newLine();
                    }
                }
            }
        }

        public static List<Core.Proceso> leerProcesos(Path directorioEntrada, int numeroProcesosEsperados) throws IOException {
            if (!Files.exists(directorioEntrada) || !Files.isDirectory(directorioEntrada)) {
                throw new IOException("Directorio de entrada inexistente: " + directorioEntrada);
            }
            List<Path> archivos = new ArrayList<>();
            try (Stream<Path> flujo = Files.list(directorioEntrada)) {
                flujo.filter(p -> p.getFileName().toString().startsWith("proc") && p.getFileName().toString().endsWith(".txt"))
                        .forEach(archivos::add);
            }
            archivos.sort(Comparator.comparingInt(ruta -> extraerIndiceProceso(ruta)));

            if (numeroProcesosEsperados > 0 && archivos.size() != numeroProcesosEsperados) {
                throw new IOException("Se esperaban " + numeroProcesosEsperados + " procesos pero se encontraron " + archivos.size());
            }

            List<Core.Proceso> procesos = new ArrayList<>();
            for (int indice = 0; indice < archivos.size(); indice++) {
                Path archivo = archivos.get(indice);
                ProcesamientoArchivo resultado = leerArchivoProceso(archivo, indice);
                procesos.add(crearProcesoDesdeResultado(resultado, indice));
            }
            return procesos;
        }

        public static void escribirCsvEstadisticas(List<Core.Proceso> procesos, Path directorioSalida) throws IOException {
            if (!Files.exists(directorioSalida)) {
                Files.createDirectories(directorioSalida);
            }
            Path archivo = directorioSalida.resolve("stats.csv");
            try (BufferedWriter escritor = Files.newBufferedWriter(archivo, StandardCharsets.UTF_8)) {
                escritor.write("pid,NR,fallos,aciertos,swaps,tasa_fallos,tasa_exito");
                escritor.newLine();
                for (Core.Proceso proceso : procesos) {
                    Core.Estadisticas estadisticas = proceso.estadisticas;
                    escritor.write(proceso.pid + "," + proceso.totalReferencias + "," + estadisticas.fallos + ","
                            + estadisticas.aciertos + "," + estadisticas.swaps + ","
                            + String.format(Locale.US, "%.4f", estadisticas.tasaFallos(proceso.totalReferencias)) + ","
                            + String.format(Locale.US, "%.4f", estadisticas.tasaAciertos(proceso.totalReferencias)));
                    escritor.newLine();
                }
            }
        }

        private static String formatearReferencia(Core.Referencia referencia) {
            return "M" + referencia.identificadorMatriz + ":[" + referencia.fila + "-" + referencia.columna + "],"
                    + referencia.numeroPagina + "," + referencia.desplazamiento + "," + referencia.operacion;
        }

        private static int extraerIndiceProceso(Path ruta) {
            String nombre = ruta.getFileName().toString();
            int inicio = "proc".length();
            int fin = nombre.indexOf('.');
            String numero = nombre.substring(inicio, fin);
            return Integer.parseInt(numero);
        }

        private static ProcesamientoArchivo leerArchivoProceso(Path ruta, int indiceEsperado) throws IOException {
            List<String> lineas = Files.readAllLines(ruta, StandardCharsets.UTF_8);
            if (lineas.size() < 5) {
                throw new IOException("Archivo de proceso incompleto: " + ruta);
            }
            int tp = obtenerValorEntero(lineas.get(0), "TP");
            int nf = obtenerValorEntero(lineas.get(1), "NF");
            int nc = obtenerValorEntero(lineas.get(2), "NC");
            int nr = obtenerValorEntero(lineas.get(3), "NR");
            int np = obtenerValorEntero(lineas.get(4), "NP");
            List<Core.Referencia> referencias = new ArrayList<>();

            for (int i = 5; i < lineas.size(); i++) {
                String linea = lineas.get(i).trim();
                if (linea.isEmpty()) {
                    continue;
                }
                referencias.add(parsearReferencia(linea, tp));
            }

            if (referencias.size() != nr) {
                throw new IOException("El archivo " + ruta + " reporta NR=" + nr + " pero contiene " + referencias.size() + " referencias");
            }
            return new ProcesamientoArchivo(indiceEsperado, tp, nf, nc, nr, np, referencias);
        }

        private static Core.Proceso crearProcesoDesdeResultado(ProcesamientoArchivo resultado, int pid) {
            return new Core.Proceso(pid, resultado.tamanoPagina, resultado.numeroFilas, resultado.numeroColumnas,
                    resultado.totalReferencias, resultado.totalPaginas, resultado.referencias);
        }

        private static int obtenerValorEntero(String linea, String prefijo) throws IOException {
            String limpia = linea.trim();
            String esperado = prefijo + "=";
            if (!limpia.startsWith(esperado)) {
                throw new IOException("Linea invalida: se esperaba " + esperado + " en " + linea);
            }
            return Integer.parseInt(limpia.substring(esperado.length()).trim());
        }

        private static Core.Referencia parsearReferencia(String linea, int tamanoPagina) throws IOException {
            String[] secciones = linea.split(",");
            if (secciones.length != 4) {
                throw new IOException("Referencia invalida: " + linea);
            }
            String primera = secciones[0].trim();
            if (!primera.startsWith("M")) {
                throw new IOException("Referencia sin identificador de matriz: " + linea);
            }
            char idMatriz = primera.charAt(1);
            int inicioCorchete = primera.indexOf('[');
            int finCorchete = primera.indexOf(']');
            if (inicioCorchete < 0 || finCorchete < 0 || finCorchete <= inicioCorchete) {
                throw new IOException("Coordenadas invalidas en referencia: " + linea);
            }
            String coordenadas = primera.substring(inicioCorchete + 1, finCorchete);
            String[] partesCoordenadas = coordenadas.split("-");
            if (partesCoordenadas.length != 2) {
                throw new IOException("Formato de coordenadas invalido: " + linea);
            }
            int fila = Integer.parseInt(partesCoordenadas[0]);
            int columna = Integer.parseInt(partesCoordenadas[1]);

            int numeroPagina = Integer.parseInt(secciones[1].trim());
            int desplazamiento = Integer.parseInt(secciones[2].trim());
            char operacion = secciones[3].trim().charAt(0);
            long direccionVirtual = (long) numeroPagina * tamanoPagina + desplazamiento;
            return new Core.Referencia(idMatriz, fila, columna, direccionVirtual, numeroPagina, desplazamiento, operacion);
        }

        private static final class ProcesamientoArchivo {
            final int pid;
            final int tamanoPagina;
            final int numeroFilas;
            final int numeroColumnas;
            final int totalReferencias;
            final int totalPaginas;
            final List<Core.Referencia> referencias;

            ProcesamientoArchivo(int pid, int tamanoPagina, int numeroFilas, int numeroColumnas,
                                 int totalReferencias, int totalPaginas, List<Core.Referencia> referencias) {
                this.pid = pid;
                this.tamanoPagina = tamanoPagina;
                this.numeroFilas = numeroFilas;
                this.numeroColumnas = numeroColumnas;
                this.totalReferencias = totalReferencias;
                this.totalPaginas = totalPaginas;
                this.referencias = referencias;
            }
        }
    }

    public static final class UtilidadesLog {
        private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

        private UtilidadesLog() {
        }

        public static BufferedWriter crearEscritorLog(Path directorioSalida) throws IOException {
            if (!Files.exists(directorioSalida)) {
                Files.createDirectories(directorioSalida);
            }
            String nombre = "run_" + LocalDateTime.now().format(FORMATO) + ".log";
            Path ruta = directorioSalida.resolve(nombre);
            return Files.newBufferedWriter(ruta, StandardCharsets.UTF_8);
        }
    }
}