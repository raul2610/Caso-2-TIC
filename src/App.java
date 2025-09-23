import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class App {

    public static void main(String[] argumentos) {
        if (argumentos.length == 0) {
            mostrarAyuda();
            return;
        }
        String subcomando = argumentos[0];
        try {
            if ("generate".equals(subcomando)) {
                ejecutarGeneracion(argumentos);
            } else if ("simulate".equals(subcomando)) {
                ejecutarSimulacion(argumentos);
            } else {
                System.err.println("Subcomando desconocido: " + subcomando);
                mostrarAyuda();
            }
        } catch (Exception error) {
            System.err.println("Error: " + error.getMessage());
        }
    }

    private static void mostrarAyuda() {
        System.out.println("Uso incorrecto de la aplicacion. Mal input de argumentos.");
    }

    private static void ejecutarGeneracion(String[] argumentos) throws IOException {
        if (argumentos.length < 2) {
            throw new IllegalArgumentException("Falta la ruta del archivo de configuracion");
        }
        Path rutaConfig = Paths.get(argumentos[1]);
        IOKit.Configuracion configuracion = IOKit.Configuracion.desdeArchivo(rutaConfig);
        List<Core.Proceso> procesos = new ArrayList<>();
        for (int indice = 0; indice < configuracion.numeroProcesos; indice++) {
            int[] dimensiones = configuracion.tamanosMatrices.get(indice);
            Core.Proceso proceso = Generador.construirProceso(indice, configuracion.tamanoPagina,
                    dimensiones[0], dimensiones[1]);
            procesos.add(proceso);
        }
        Path directorioEntrada = Paths.get("input");
        IOKit.EntradaSalidaProcesos.escribirArchivosProcesos(procesos, directorioEntrada);
        System.out.println("Archivos proc<i>.txt generados en " + directorioEntrada.toAbsolutePath());
    }

    private static void ejecutarSimulacion(String[] argumentos) throws IOException {
        ParametrosSimulacion parametros = ParametrosSimulacion.desdeArgumentos(argumentos);
        try (BufferedWriter bitacora = IOKit.UtilidadesLog.crearEscritorLog(parametros.directorioSalida)) {
            IOKit.UtilidadesLog.imprimir(bitacora, "Inicio:");
            List<Core.Proceso> procesos = IOKit.EntradaSalidaProcesos.leerProcesos(parametros.directorioEntrada, parametros.numeroProcesos, bitacora);
            SimuladorMotor motor = new SimuladorMotor(procesos, parametros.totalMarcos, parametros.numeroProcesos, bitacora);
            IOKit.UtilidadesLog.imprimir(bitacora, "Simulación:");
            motor.ejecutar();
            IOKit.EntradaSalidaProcesos.escribirCsvEstadisticas(procesos, parametros.directorioSalida);
            imprimirResumen(procesos);
        }
    }

    private static void imprimirResumen(List<Core.Proceso> procesos) {
        for (Core.Proceso proceso : procesos) {
            Core.Estadisticas estadisticas = proceso.estadisticas;
            long aciertosDerivados = Math.max(0L, (long) proceso.totalReferencias - estadisticas.fallos);
            double tasaFallos = estadisticas.tasaFallos(proceso.totalReferencias);
            double tasaExito = 1.0 - tasaFallos;
            System.out.printf("Proceso %d: \n-NR=%d \n-Fallos=%d \n-Aciertos=%d \n-SWAP=%d \n-Tasa fallos=%.4f \n-Tasa exito=%.4f%n",
                    proceso.pid, proceso.totalReferencias, estadisticas.fallos, aciertosDerivados,
                    estadisticas.swaps, tasaFallos, tasaExito);
        }
    }

    private static final class ParametrosSimulacion {
        final int totalMarcos;
        final int numeroProcesos;
        final Path directorioEntrada;
        final Path directorioSalida;

        private ParametrosSimulacion(int totalMarcos, int numeroProcesos, Path directorioEntrada, Path directorioSalida) {
            this.totalMarcos = totalMarcos;
            this.numeroProcesos = numeroProcesos;
            this.directorioEntrada = directorioEntrada;
            this.directorioSalida = directorioSalida;
        }

        static ParametrosSimulacion desdeArgumentos(String[] argumentos) {
            int totalMarcos = -1;
            int numeroProcesos = -1;
            Path directorioEntrada = Paths.get("input");
            Path directorioSalida = Paths.get("output");
            for (int i = 1; i < argumentos.length; i++) {
                String actual = argumentos[i];
                if ("--frames".equals(actual) && i + 1 < argumentos.length) {
                    totalMarcos = Integer.parseInt(argumentos[++i]);
                } else if ("--processes".equals(actual) && i + 1 < argumentos.length) {
                    numeroProcesos = Integer.parseInt(argumentos[++i]);
                } else if ("--input".equals(actual) && i + 1 < argumentos.length) {
                    directorioEntrada = Paths.get(argumentos[++i]);
                } else if ("--out".equals(actual) && i + 1 < argumentos.length) {
                    directorioSalida = Paths.get(argumentos[++i]);
                } else {
                    throw new IllegalArgumentException("Argumento desconocido o mal formado: " + actual);
                }
            }
            if (totalMarcos <= 0) {
                throw new IllegalArgumentException("--frames debe ser un entero positivo");
            }
            if (numeroProcesos <= 0) {
                throw new IllegalArgumentException("--processes debe ser un entero positivo");
            }
            if (totalMarcos % numeroProcesos != 0) {
                throw new IllegalArgumentException("El numero total de marcos debe ser multiplo del numero de procesos");
            }
            return new ParametrosSimulacion(totalMarcos, numeroProcesos, directorioEntrada, directorioSalida);
        }
    }

    private static final class Generador {
        private Generador() {
        }

        static Core.Proceso construirProceso(int pid, int tamanoPagina, int filas, int columnas) {
            int totalReferencias = filas * columnas * 3;
            long elementosPorMatriz = (long) filas * columnas;
            long bytesPorMatriz = elementosPorMatriz * 4L;
            int totalPaginas = (int) ((bytesPorMatriz * 3 + tamanoPagina - 1L) / tamanoPagina);
            List<Core.Referencia> referencias = new ArrayList<>(totalReferencias);

            long baseM1 = 0L;
            long baseM2 = bytesPorMatriz;
            long baseM3 = bytesPorMatriz * 2L;

            for (int fila = 0; fila < filas; fila++) {
                for (int columna = 0; columna < columnas; columna++) {
                    long indiceLineal = (long) fila * columnas + columna;
                    agregarReferencia(referencias, '1', fila, columna, baseM1, indiceLineal, tamanoPagina, 'r');
                    agregarReferencia(referencias, '2', fila, columna, baseM2, indiceLineal, tamanoPagina, 'r');
                    agregarReferencia(referencias, '3', fila, columna, baseM3, indiceLineal, tamanoPagina, 'w');
                }
            }
            return new Core.Proceso(pid, tamanoPagina, filas, columnas, totalReferencias, totalPaginas, referencias);
        }

        private static void agregarReferencia(List<Core.Referencia> referencias, char matrizId, int fila, int columna,
                                               long base, long indiceLineal, int tamanoPagina, char operacion) {
            long direccionVirtual = base + indiceLineal * 4L;
            int numeroPagina = (int) (direccionVirtual / tamanoPagina);
            int desplazamiento = (int) (direccionVirtual % tamanoPagina);
            referencias.add(new Core.Referencia(matrizId, fila, columna, direccionVirtual, numeroPagina, desplazamiento, operacion));
        }
    }

    private static final class SimuladorMotor {
        private final List<Core.Proceso> procesos;
        private final List<Core.Marco> marcos;
        private final int numeroProcesos;
        private final BufferedWriter bitacora;
        private long relojGlobal;

        SimuladorMotor(List<Core.Proceso> procesos, int totalMarcos, int numeroProcesos, BufferedWriter bitacora) throws IOException {
            this.procesos = procesos;
            this.numeroProcesos = numeroProcesos;
            this.bitacora = bitacora;
            this.marcos = new ArrayList<>(totalMarcos);
            for (int i = 0; i < totalMarcos; i++) {
                Core.Marco marco = new Core.Marco(i);
                marco.limpiar();
                marcos.add(marco);
            }
            inicializarMarcos();
        }

        void ejecutar() throws IOException {
            Deque<Core.Proceso> cola = new ArrayDeque<>(procesos);
            while (!cola.isEmpty()) {
                Core.Proceso proceso = cola.pollFirst();
                if (!proceso.tieneReferenciasPendientes()) {
                    finalizarProceso(proceso, cola);
                    continue;
                }
                Core.Referencia referencia = proceso.referenciaActual();
                IOKit.UtilidadesLog.imprimir(bitacora, "Turno proc: " + proceso.pid);
                IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + proceso.pid + " analizando linea_: " + proceso.indiceReferenciaActual);
                ResultadoAcceso resultado = resolverAcceso(proceso, referencia);
                if (resultado.esAcierto) {
                    if (!proceso.huboFalloEnReferenciaActual) { proceso.estadisticas.aciertos++; }
                    proceso.hitsEvento++;
                    proceso.indiceReferenciaActual++;
                    proceso.huboFalloEnReferenciaActual = false;
                    IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + proceso.pid + " hits: " + proceso.hitsEvento);
                } else {
                    proceso.estadisticas.fallos++;
                    proceso.estadisticas.swaps += resultado.swapsGenerados;
                    proceso.huboFalloEnReferenciaActual = true;
                    IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + proceso.pid + " falla de pag: " + proceso.estadisticas.fallos);
                }
                IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + proceso.pid + " envejecimiento");
                if (!proceso.tieneReferenciasPendientes()) {
                    proceso.finalizado = true;
                    finalizarProceso(proceso, cola);
                } else {
                    cola.offerLast(proceso);
                }
            }
        }

        private void inicializarMarcos() throws IOException {
            int marcosPorProceso = marcos.size() / numeroProcesos;
            int indiceMarco = 0;
            for (Core.Proceso proceso : procesos) {
                proceso.marcosAsignados.clear();
                for (int i = 0; i < marcosPorProceso; i++) {
                    Core.Marco marco = marcos.get(indiceMarco++);
                    marco.limpiar();
                    marco.pidDueno = proceso.pid;
                    proceso.marcosAsignados.add(marco.idMarco);
                    IOKit.UtilidadesLog.imprimir(bitacora, "Proceso " + proceso.pid + ": recibe marco " + marco.idMarco);
                }
            }
        }

        private ResultadoAcceso resolverAcceso(Core.Proceso proceso, Core.Referencia referencia) throws IOException {
            Integer idMarco = proceso.tablaPaginas.obtenerMarcoParaVpn(referencia.numeroPagina);
            if (idMarco != null) {
                Core.Marco marco = marcos.get(idMarco);
                actualizarUsoMarco(marco);
                return ResultadoAcceso.acierto();
            }

            Core.Marco marcoLibre = buscarMarcoLibre(proceso);
            if (marcoLibre != null) {
                cargarPaginaEnMarco(proceso, referencia, marcoLibre);
                return ResultadoAcceso.fallo(1);
            }

            Core.Marco victima = seleccionarMarcoVictima(proceso);
            if (victima.vpnCargada != null) {
                proceso.tablaPaginas.eliminarMapeo(victima.vpnCargada);
            }
            cargarPaginaEnMarco(proceso, referencia, victima);
            return ResultadoAcceso.fallo(2);
        }

        private Core.Marco buscarMarcoLibre(Core.Proceso proceso) {
            for (Integer idMarco : proceso.marcosAsignados) {
                Core.Marco marco = marcos.get(idMarco);
                if (marco.vpnCargada == null) {
                    return marco;
                }
            }
            return null;
        }

        private Core.Marco seleccionarMarcoVictima(Core.Proceso proceso) {
            Core.Marco seleccionado = null;
            for (Integer idMarco : proceso.marcosAsignados) {
                Core.Marco candidato = marcos.get(idMarco);
                if (seleccionado == null || candidato.ultimaReferencia < seleccionado.ultimaReferencia) {
                    seleccionado = candidato;
                }
            }
            return seleccionado;
        }

        private void cargarPaginaEnMarco(Core.Proceso proceso, Core.Referencia referencia, Core.Marco marco) {
            proceso.tablaPaginas.registrarMapeo(referencia.numeroPagina, marco.idMarco);
            marco.pidDueno = proceso.pid;
            marco.vpnCargada = referencia.numeroPagina;
            actualizarUsoMarco(marco);
        }

        private void actualizarUsoMarco(Core.Marco marco) {
            relojGlobal++;
            marco.ultimaReferencia = relojGlobal;
        }

        private void finalizarProceso(Core.Proceso proceso, Deque<Core.Proceso> cola) throws IOException {
            if (!proceso.marcosAsignados.isEmpty()) {
                IOKit.UtilidadesLog.imprimir(bitacora, "========================");
                IOKit.UtilidadesLog.imprimir(bitacora, "Termino proc: " + proceso.pid);
                IOKit.UtilidadesLog.imprimir(bitacora, "========================");
                List<Integer> marcosLiberados = new ArrayList<>(proceso.marcosAsignados);
                for (Integer idMarco : marcosLiberados) {
                    Core.Marco marco = marcos.get(idMarco);
                    if (marco.vpnCargada != null) {
                        proceso.tablaPaginas.eliminarMapeo(marco.vpnCargada);
                    }
                    marco.vpnCargada = null;
                    marco.pidDueno = null;
                    marco.ultimaReferencia = 0L;
                    IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + proceso.pid + " removiendo marco: " + idMarco);
                }
                proceso.marcosAsignados.clear();
                Core.Proceso destino = seleccionarProcesoConMasFallos(cola);
                if (destino != null) {
                    for (Integer idMarco : marcosLiberados) {
                        Core.Marco marco = marcos.get(idMarco);
                        marco.pidDueno = destino.pid;
                        destino.marcosAsignados.add(idMarco);
                        IOKit.UtilidadesLog.imprimir(bitacora, "PROC " + destino.pid + " asignando marco nuevo " + idMarco);
                    }
                } else {
                    IOKit.UtilidadesLog.imprimir(bitacora, "No hay procesos activos para reasignar marcos de proceso " + proceso.pid);
                }
            }
        }

        private Core.Proceso seleccionarProcesoConMasFallos(Deque<Core.Proceso> cola) {
            Core.Proceso seleccionado = null;
            for (Core.Proceso candidato : cola) {
                if (candidato.finalizado) {
                    continue;
                }
                if (seleccionado == null || candidato.estadisticas.fallos > seleccionado.estadisticas.fallos
                        || (candidato.estadisticas.fallos == seleccionado.estadisticas.fallos && candidato.pid < seleccionado.pid)) {
                    seleccionado = candidato;
                }
            }
            return seleccionado;
        }

        private void escribirBitacora(String mensaje) throws IOException {
            if (bitacora != null) {
                bitacora.write(mensaje);
                bitacora.newLine();
                bitacora.flush();
            }
        }
    }

    private static final class ResultadoAcceso {
        final boolean esAcierto;
        final int swapsGenerados;

        private ResultadoAcceso(boolean esAcierto, int swapsGenerados) {
            this.esAcierto = esAcierto;
            this.swapsGenerados = swapsGenerados;
        }

        static ResultadoAcceso acierto() {
            return new ResultadoAcceso(true, 0);
        }

        static ResultadoAcceso fallo(int swapsGenerados) {
            return new ResultadoAcceso(false, swapsGenerados);
        }
    }
}
