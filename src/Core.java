import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Core {

    private Core() {}

    public static final class Referencia {
        public final char identificadorMatriz;
        public final int fila;
        public final int columna;
        public final long direccionVirtual;
        public final int numeroPagina;
        public final int desplazamiento;
        public final char operacion;

        public Referencia(char identificadorMatriz, int fila, int columna, long direccionVirtual,
                           int numeroPagina, int desplazamiento, char operacion) {
            this.identificadorMatriz = identificadorMatriz;
            this.fila = fila;
            this.columna = columna;
            this.direccionVirtual = direccionVirtual;
            this.numeroPagina = numeroPagina;
            this.desplazamiento = desplazamiento;
            this.operacion = operacion;
        }
    }

    public static final class Marco {
        public final int idMarco;
        public Integer pidDueno;
        public Integer vpnCargada;
        public long ultimaReferencia;

        public Marco(int idMarco) {
            this.idMarco = idMarco;
        }

        public void limpiar() {
            pidDueno = null;
            vpnCargada = null;
            ultimaReferencia = 0L;
        }
    }

    public static final class TablaPaginas {
        private final Map<Integer, Integer> mapaVpnMarco = new HashMap<>();

        public Integer obtenerMarcoParaVpn(int numeroPagina) {
            return mapaVpnMarco.get(numeroPagina);
        }

        public void registrarMapeo(int numeroPagina, int idMarco) {
            mapaVpnMarco.put(numeroPagina, idMarco);
        }

        public void eliminarMapeo(int numeroPagina) {
            mapaVpnMarco.remove(numeroPagina);
        }

        public Map<Integer, Integer> copiarMapeo() {
            return new HashMap<>(mapaVpnMarco);
        }
    }

    public static final class Estadisticas {
        public long aciertos;
        public long fallos;
        public long swaps;

        public double tasaFallos(int totalReferencias) {
            return totalReferencias == 0 ? 0.0 : (double) fallos / totalReferencias;
        }

        public double tasaAciertos(int totalReferencias) {
            return totalReferencias == 0 ? 0.0 : (double) aciertos / totalReferencias;
        }
    }

    public static final class Proceso {
        public final int pid;
        public final int tamanoPagina;
        public final int numeroFilas;
        public final int numeroColumnas;
        public final int totalReferencias;
        public final int totalPaginas;
        public final List<Referencia> referencias;
        public final TablaPaginas tablaPaginas = new TablaPaginas();
        public final List<Integer> marcosAsignados = new ArrayList<>();
        public final Estadisticas estadisticas = new Estadisticas();
        public int indiceReferenciaActual;
        public boolean finalizado;
        public boolean huboFalloEnReferenciaActual;
        public long hitsEvento;

        public Proceso(int pid, int tamanoPagina, int numeroFilas, int numeroColumnas,
                        int totalReferencias, int totalPaginas, List<Referencia> referencias) {
            this.pid = pid;
            this.tamanoPagina = tamanoPagina;
            this.numeroFilas = numeroFilas;
            this.numeroColumnas = numeroColumnas;
            this.totalReferencias = totalReferencias;
            this.totalPaginas = totalPaginas;
            this.referencias = referencias;
        }

        public Referencia referenciaActual() {
            if (indiceReferenciaActual < 0 || indiceReferenciaActual >= referencias.size()) {
                return null;
            }
            return referencias.get(indiceReferenciaActual);
        }

        public boolean tieneReferenciasPendientes() {
            return indiceReferenciaActual < referencias.size();
        }
    }
}
